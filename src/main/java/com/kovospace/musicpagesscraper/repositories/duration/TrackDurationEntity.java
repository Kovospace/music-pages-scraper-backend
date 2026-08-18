package com.kovospace.musicpagesscraper.repositories.duration;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class TrackDurationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(unique = true, nullable = false)
    private String hrefHash;

    private Long durationMs;

    private LocalDateTime updateTime;

    @PrePersist
    private void updateTime() {
        this.updateTime = LocalDateTime.now();
    }

    public TrackDurationEntity() {}

    public TrackDurationEntity(String hrefHash, Long durationMs) {
        this.hrefHash = hrefHash;
        this.durationMs = durationMs;
    }
}
