package com.hojang.shorturl.repository;

import com.hojang.shorturl.domain.ShortUrl;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, String> {
    Optional<ShortUrl> findByOriginalUrlSignature(String originalUrlSignature);
}
