package com.hojang.shorturl.repository;

import com.hojang.shorturl.domain.ShortUrl;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, String> {
}
