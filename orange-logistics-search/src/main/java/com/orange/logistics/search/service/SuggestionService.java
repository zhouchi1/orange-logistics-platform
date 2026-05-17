package com.orange.logistics.search.service;

import com.orange.logistics.search.dto.Suggestion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 搜索建议服务
 * - 前缀匹配
 * - 热门搜索
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestionService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    private static final String HOT_SEARCH_KEY = "search:hot";
    private static final String SEARCH_PREFIX_KEY = "search:prefix:";
    private static final int MAX_SUGGESTIONS = 10;

    /**
     * 获取搜索建议（前缀匹配 + 热门搜索）
     */
    public Flux<Suggestion> getSuggestions(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return getHotSearches();
        }

        // 前缀匹配
        return redisTemplate.opsForZSet()
                .reverseRangeByScore(SEARCH_PREFIX_KEY + prefix.charAt(0),
                        Range.closed(0.0, Double.MAX_VALUE))
                .filter(item -> item.startsWith(prefix))
                .take(MAX_SUGGESTIONS)
                .map(item -> Suggestion.builder()
                        .text(item)
                        .type("PREFIX")
                        .score(0L)
                        .build())
                .switchIfEmpty(getHotSearches());
    }

    /**
     * 获取热门搜索
     */
    public Flux<Suggestion> getHotSearches() {
        return redisTemplate.opsForZSet()
                .reverseRangeWithScores(HOT_SEARCH_KEY,
                        Range.closed(0L, (long) MAX_SUGGESTIONS - 1))
                .map(tuple -> Suggestion.builder()
                        .text(tuple.getValue())
                        .type("HOT")
                        .score(tuple.getScore() != null ? tuple.getScore().longValue() : 0L)
                        .build());
    }

    /**
     * 记录搜索词（增加热度）
     */
    public Mono<Void> recordSearch(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Mono.empty();
        }
        return redisTemplate.opsForZSet()
                .incrementScore(HOT_SEARCH_KEY, keyword.trim(), 1)
                .then(
                    // 同时记录到前缀索引
                    redisTemplate.opsForZSet()
                            .add(SEARCH_PREFIX_KEY + keyword.charAt(0), keyword.trim(), 1)
                            .then()
                );
    }

    /**
     * 初始化热门搜索（可在启动时调用）
     */
    public Mono<Void> initHotSearches(List<String> keywords) {
        return Flux.fromIterable(keywords)
                .flatMap(keyword -> redisTemplate.opsForZSet()
                        .add(HOT_SEARCH_KEY, keyword, 100))
                .then();
    }
}
