package com.hojang.shorturl.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "short_url", indexes = {
        @Index(name = "idx_org_url_sig", columnList = "originalUrlSignature")
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ShortUrl {

    @Id
    @Column(length = 20)
    private String shortKey;

    @Column(nullable = false, length = 1000)
    private String originalUrl;

    @Column(unique = true, length = 64)
    private String originalUrlSignature;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Column(length = 20)
    private String createdBy;

    public ShortUrl(String shortKey, String originalUrl, String originalUrlSignature, String createdBy) {
        this.shortKey = shortKey;
        this.originalUrl = originalUrl;
        this.originalUrlSignature = originalUrlSignature;
        this.createdBy = createdBy;
    }
}