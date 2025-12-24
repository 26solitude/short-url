package com.hojang.shorturl.controller;

import com.hojang.shorturl.service.ShortUrlService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShortUrlController.class)
class ShortUrlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShortUrlService shortUrlService;

    @Test
    @DisplayName("단축 URL 생성 API 테스트")
    void createShortUrlTest() throws Exception {
        // Given
        String longUrl = "http://example.com";
        String shortKey = "1000000";
        // 서비스가 반환할 것으로 예상되는 값 Mocking
        given(shortUrlService.generateShortUrl(longUrl)).willReturn(shortKey);

        // When & Then
        mockMvc.perform(post("/shorturl")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(longUrl))
                .andExpect(status().isOk())
                .andExpect(content().string("http://localhost:8080/shorturls/" + shortKey));
    }

    @Test
    @DisplayName("원본 URL 조회 API 테스트")
    void getLongUrlTest() throws Exception {
        // Given
        String shortKey = "1000000";
        String originalUrl = "http://example.com";
        given(shortUrlService.getOriginalUrl(shortKey)).willReturn(originalUrl);

        // When & Then
        mockMvc.perform(get("/shorturls/{shortKey}", shortKey))
                .andExpect(status().isOk())
                .andExpect(content().string(originalUrl));
    }
}