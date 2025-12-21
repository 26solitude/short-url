package com.hojang.shorturl.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "short_url")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ShortUrl {
    @Id
    private String shortKey; // Base62로 변환된 단축 키 (PK)
    private String originalUrl;
}