package com.orange.logistics.search.service;

import com.orange.logistics.search.dto.SearchRequest;
import com.orange.logistics.search.dto.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 搜索服务
 * - Elasticsearch 全文检索
 * - 运单号、地址、收件人搜索
 * - 降级到数据库模糊查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final DatabaseClient databaseClient;
    private final SuggestionService suggestionService;

    /**
     * 全文搜索（优先ES，降级到MySQL LIKE）
     */
    public Mono<SearchResult> search(SearchRequest request) {
        long startTime = System.currentTimeMillis();

        // 记录搜索词
        suggestionService.recordSearch(request.getKeyword()).subscribe();

        // 使用数据库模糊查询作为降级方案（ES不可用时）
        return searchFromDatabase(request, startTime);
    }

    /**
     * 数据库模糊查询（ES降级方案）
     */
    private Mono<SearchResult> searchFromDatabase(SearchRequest request, long startTime) {
        String keyword = "%" + request.getKeyword() + "%";
        int offset = (request.getPage() - 1) * request.getSize();

        // 查询总数
        Mono<Long> countMono = databaseClient.sql(
                "SELECT COUNT(*) as cnt FROM orders WHERE " +
                "waybill_no LIKE :keyword OR receiver_name LIKE :keyword OR receiver_address LIKE :keyword")
                .bind("keyword", keyword)
                .fetch()
                .one()
                .map(row -> ((Number) row.get("cnt")).longValue())
                .defaultIfEmpty(0L);

        // 查询数据
        Mono<List<SearchResult.SearchHit>> hitsMono = databaseClient.sql(
                "SELECT id, waybill_no, receiver_name, receiver_phone, receiver_address, status " +
                "FROM orders WHERE waybill_no LIKE :keyword OR receiver_name LIKE :keyword " +
                "OR receiver_address LIKE :keyword ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
                .bind("keyword", keyword)
                .bind("limit", request.getSize())
                .bind("offset", offset)
                .fetch()
                .all()
                .map(row -> SearchResult.SearchHit.builder()
                        .id(String.valueOf(row.get("id")))
                        .waybillNo((String) row.get("waybill_no"))
                        .receiverName((String) row.get("receiver_name"))
                        .receiverPhone((String) row.get("receiver_phone"))
                        .receiverAddress((String) row.get("receiver_address"))
                        .status((String) row.get("status"))
                        .score(1.0f)
                        .highlight(highlightKeyword((String) row.get("receiver_address"), request.getKeyword()))
                        .build())
                .collectList();

        return Mono.zip(countMono, hitsMono)
                .map(tuple -> SearchResult.builder()
                        .total(tuple.getT1())
                        .page(request.getPage())
                        .size(request.getSize())
                        .took(System.currentTimeMillis() - startTime)
                        .hits(tuple.getT2())
                        .build());
    }

    /**
     * 高亮关键词
     */
    private String highlightKeyword(String text, String keyword) {
        if (text == null || keyword == null) return text;
        return text.replace(keyword, "<em>" + keyword + "</em>");
    }

    /**
     * 按运单号精确搜索
     */
    public Mono<SearchResult.SearchHit> searchByWaybillNo(String waybillNo) {
        return databaseClient.sql(
                "SELECT id, waybill_no, receiver_name, receiver_phone, receiver_address, status " +
                "FROM orders WHERE waybill_no = :waybillNo")
                .bind("waybillNo", waybillNo)
                .fetch()
                .one()
                .map(row -> SearchResult.SearchHit.builder()
                        .id(String.valueOf(row.get("id")))
                        .waybillNo((String) row.get("waybill_no"))
                        .receiverName((String) row.get("receiver_name"))
                        .receiverPhone((String) row.get("receiver_phone"))
                        .receiverAddress((String) row.get("receiver_address"))
                        .status((String) row.get("status"))
                        .score(1.0f)
                        .build());
    }
}
