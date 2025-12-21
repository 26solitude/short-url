package com.hojang.shorturl.controller;

import com.hojang.shorturl.domain.ShortUrl;
import com.hojang.shorturl.repository.ShortUrlRepository;
import com.hojang.shorturl.service.DistributedSequenceService;
import com.hojang.shorturl.util.Base62Util;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ShortUrlController {

    private final DistributedSequenceService sequenceService;
    private final ShortUrlRepository shortUrlRepository;
    private final Base62Util base62Util;

    // 1. POST /shorturl : 긴 URL -> 단축 URL 생성
    @PostMapping("/shorturl")
    @Transactional
    public String createShortUrl(@RequestBody String longUrl) {
        // (1) 분산 카운터에서 ID 발급 (예: 1001)
        long id = sequenceService.nextId();

        // (2) Base62 인코딩 (예: "g7")
        String shortKey = base62Util.encode(id);

        // (3) DB 저장 (shortKey, longUrl)
        shortUrlRepository.save(new ShortUrl(shortKey, longUrl));

        // (4) 결과 반환
        return "http://localhost:8080/shorturls/" + shortKey;
    }

    // 2. GET /shorturls/{shorturl} : 단축 URL -> 원본 URL 반환
    @GetMapping("/shorturls/{shortKey}")
    public String getLongUrl(@PathVariable String shortKey) {
        return shortUrlRepository.findById(shortKey)
                .map(ShortUrl::getOriginalUrl)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 URL입니다."));
    }
}