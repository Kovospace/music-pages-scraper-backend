package com.kovospace.musicpagesscraper.repositories.duration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface TrackDurationRepository extends JpaRepository<TrackDurationEntity, Long> {

    List<TrackDurationEntity> findAllByHrefHashIn(Collection<String> hrefHashes);

    boolean existsByHrefHash(String hrefHash);
}
