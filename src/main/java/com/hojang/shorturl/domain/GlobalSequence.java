package com.hojang.shorturl.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "global_sequence")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GlobalSequence {
    @Id
    private String id;     // 예: "short_url_seq"
    private Long maxId;    // 현재 확보된 최대 ID 범위
}