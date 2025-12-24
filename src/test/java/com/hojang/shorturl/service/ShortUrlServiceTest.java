package com.hojang.shorturl.service;

import com.hojang.shorturl.domain.ShortUrl;
import com.hojang.shorturl.repository.ShortUrlRepository;
import com.hojang.shorturl.util.Base62Util;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShortUrlServiceTest {

    @InjectMocks
    private ShortUrlService shortUrlService;

    @Mock
    private ShortUrlRepository shortUrlRepository;

    @Mock
    private DistributedSequenceService sequenceService;

    @Mock
    private Base62Util base62Util;

    @Test
    @DisplayName("신규 URL 생성: 서명이 없으면 ID를 발급받고 저장해야 한다")
    void generateNewShortUrlTest() {
        // Given
        String longUrl = "http://google.com";
        long newId = 56_800_235_584L;
        String expectedShortKey = "1000000";

        // Mocking: 저장된 게 없음 -> ID 발급 -> 인코딩 -> 저장
        given(shortUrlRepository.findByOriginalUrlSignature(anyString())).willReturn(Optional.empty());
        given(sequenceService.nextId()).willReturn(newId);
        given(base62Util.encode(newId)).willReturn(expectedShortKey);

        // When
        String actualShortKey = shortUrlService.generateShortUrl(longUrl);

        // Then
        assertThat(actualShortKey).isEqualTo(expectedShortKey);

        // 중요: save 메서드가 반드시 1번 호출되었는지 검증
        verify(shortUrlRepository, times(1)).save(any(ShortUrl.class));
    }

    @Test
    @DisplayName("중복 URL 요청: 이미 서명이 존재하면 저장하지 않고 기존 Key를 반환해야 한다")
    void generateExistingShortUrlTest() {
        // Given
        String longUrl = "http://naver.com";
        String existingShortKey = "existing";
        ShortUrl existingUrl = new ShortUrl(existingShortKey, longUrl, "sig", "system");

        // Mocking: 이미 DB에 있음
        given(shortUrlRepository.findByOriginalUrlSignature(anyString()))
                .willReturn(Optional.of(existingUrl));

        // When
        String actualShortKey = shortUrlService.generateShortUrl(longUrl);

        // Then
        assertThat(actualShortKey).isEqualTo(existingShortKey);

        // ★ 핵심 검증 ★: 이미 있으므로 ID 발급(nextId)도 안 하고, 저장(save)도 안 해야 함
        verify(sequenceService, never()).nextId();
        verify(shortUrlRepository, never()).save(any(ShortUrl.class));
    }
}