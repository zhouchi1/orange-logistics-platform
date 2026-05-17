package com.orange.logistics.search.controller;

import com.orange.logistics.search.dto.*;
import com.orange.logistics.search.service.AddressParser;
import com.orange.logistics.search.service.SearchService;
import com.orange.logistics.search.service.SuggestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 搜索服务接口
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final AddressParser addressParser;
    private final SuggestionService suggestionService;

    /**
     * 全文搜索
     */
    @PostMapping
    public Mono<SearchResult> search(@RequestBody SearchRequest request) {
        return searchService.search(request);
    }

    /**
     * 按运单号搜索
     */
    @GetMapping("/waybill/{waybillNo}")
    public Mono<SearchResult.SearchHit> searchByWaybillNo(@PathVariable String waybillNo) {
        return searchService.searchByWaybillNo(waybillNo);
    }

    /**
     * 地址解析
     */
    @PostMapping("/address/parse")
    public Mono<ParsedAddress> parseAddress(@RequestBody String address) {
        return addressParser.parse(address);
    }

    /**
     * 搜索建议
     */
    @GetMapping("/suggestions")
    public Flux<Suggestion> getSuggestions(@RequestParam(required = false) String prefix) {
        return suggestionService.getSuggestions(prefix);
    }

    /**
     * 热门搜索
     */
    @GetMapping("/hot")
    public Flux<Suggestion> getHotSearches() {
        return suggestionService.getHotSearches();
    }
}
