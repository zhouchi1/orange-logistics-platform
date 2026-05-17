package com.orange.logistics.risk.controller;

import com.orange.logistics.risk.entity.Blacklist;
import com.orange.logistics.risk.repository.BlacklistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * 黑名单管理接 */
@RestController
@RequestMapping("/api/risk/blacklist")
@RequiredArgsConstructor
public class BlacklistController {

    private final BlacklistRepository blacklistRepository;

    /**
     * 查询所有启用的黑名单     */
    @GetMapping
    public Flux<Blacklist> listAll() {
        return blacklistRepository.findByEnabledTrue();
    }

    /**
     * 按类型查询黑名单
     */
    @GetMapping("/type/{type}")
    public Flux<Blacklist> listByType(@PathVariable String type) {
        return blacklistRepository.findByTypeAndEnabledTrue(type);
    }

    /**
     * 添加黑名单     */
    @PostMapping
    public Mono<Blacklist> add(@RequestBody Blacklist blacklist) {
        blacklist.setEnabled(true);
        blacklist.setCreatedAt(LocalDateTime.now());
        return blacklistRepository.save(blacklist);
    }

    /**
     * 检查是否命中黑名单
     */
    @GetMapping("/check")
    public Mono<Boolean> check(@RequestParam String type, @RequestParam String value) {
        return blacklistRepository.findActiveByTypeAndValue(type, value)
                .hasElement();
    }

    /**
     * 禁用黑名单     */
    @DeleteMapping("/{id}")
    public Mono<Void> disable(@PathVariable Long id) {
        return blacklistRepository.findById(id)
                .flatMap(blacklist -> {
                    blacklist.setEnabled(false);
                    return blacklistRepository.save(blacklist);
                })
                .then();
    }
}
