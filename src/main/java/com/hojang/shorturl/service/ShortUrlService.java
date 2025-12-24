package com.hojang.shorturl.service;

import com.hojang.shorturl.domain.ShortUrl;
import com.hojang.shorturl.repository.ShortUrlRepository;
import com.hojang.shorturl.util.Base62Util;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class ShortUrlService {

    private final ShortUrlRepository shortUrlRepository;
    private final DistributedSequenceService sequenceService;
    private final Base62Util base62Util;

    @Transactional
    public String generateShortUrl(String longUrl) {
        // 1. 긴 URL을 해싱하여 서명 생성 (일관성 체크용)
        String signature = generateSignature(longUrl);

        // 2. 이미 존재하는 URL인지 확인 (Consistency 보장)
        return shortUrlRepository.findByOriginalUrlSignature(signature)
                .map(ShortUrl::getShortKey) // 존재하면 기존 Key 반환
                .orElseGet(() -> {
                    // 3. 존재하지 않으면 새로 생성
                    long id = sequenceService.nextId();
                    String shortKey = base62Util.encode(id);

                    // 4. 저장 (생성자는 임시로 'system' 지정)
                    ShortUrl newUrl = new ShortUrl(shortKey, longUrl, signature, "system");
                    shortUrlRepository.save(newUrl);

                    return shortKey;
                });
    }

    @Transactional(readOnly = true)
    public String getOriginalUrl(String shortKey) {
        return shortUrlRepository.findById(shortKey)
                .map(ShortUrl::getOriginalUrl)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 URL입니다."));
    }

    private String generateSignature(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(source.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Hash algorithm not found", e);
        }
    }
}