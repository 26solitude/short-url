package com.hojang.shorturl.controller;

import com.hojang.shorturl.service.ShortUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ShortUrlController {

    private final ShortUrlService shortUrlService;

    // 1. 단축 URL 생성
    @PostMapping("/shorturl")
    public String createShortUrl(@RequestBody String longUrl) {
        String shortKey = shortUrlService.generateShortUrl(longUrl);
        // 실제 운영 시에는 도메인을 설정 파일에서 가져와야 함
        return "http://localhost:8080/shorturls/" + shortKey;
    }

    // 2. 원본 URL 조회 (리다이렉트 용도라면 ResponseEntity<Void> + 302 Found 권장)
    @GetMapping("/shorturls/{shortKey}")
    public String getLongUrl(@PathVariable String shortKey) {
        return shortUrlService.getOriginalUrl(shortKey);
    }
}