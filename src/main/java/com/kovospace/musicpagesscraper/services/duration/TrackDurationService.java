package com.kovospace.musicpagesscraper.services.duration;

import com.kovospace.musicpagesscraper.repositories.duration.TrackDurationEntity;
import com.kovospace.musicpagesscraper.repositories.duration.TrackDurationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Serves known track durations and computes the missing ones off the request thread.
 *
 * <p>Reading a duration means fetching audio, so it can never happen inline: a band profile is up
 * to a couple of dozen tracks of several MB each. A request therefore answers immediately with
 * whatever is already known and leaves the rest to the pool — the next request for the same band
 * finds them filled in.
 *
 * <p>A duration never changes, so there is no expiry; the row is written once and kept.
 */
@Service
public class TrackDurationService {

  private static final Logger log = LoggerFactory.getLogger(TrackDurationService.class);

  private static final int WORKERS = 2;
  private static final int QUEUE_CAPACITY = 500;

  private final TrackDurationRepository repository;
  private final Mp3DurationReader reader;

  /** Tracks currently queued or being read, so a burst of requests does not fetch the same file twice. */
  private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

  private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
    WORKERS, WORKERS, 0L, TimeUnit.MILLISECONDS,
    new ArrayBlockingQueue<>(QUEUE_CAPACITY),
    runnable -> {
      Thread t = new Thread(runnable, "track-duration");
      t.setDaemon(true);
      return t;
    },
    // A full queue means the backlog is already long; dropping is fine because the work is
    // re-offered by the next request that misses the same track.
    new ThreadPoolExecutor.DiscardPolicy());

  @Autowired
  public TrackDurationService(TrackDurationRepository repository, Mp3DurationReader reader) {
    this.repository = repository;
    this.reader = reader;
  }

  /** Durations already known, keyed by hrefHash. Missing keys mean "not computed yet". */
  public Map<String, Long> known(Collection<String> hrefHashes) {
    if (hrefHashes == null || hrefHashes.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<String, Long> result = new HashMap<>();
    for (TrackDurationEntity entity : repository.findAllByHrefHashIn(hrefHashes)) {
      result.put(entity.getHrefHash(), entity.getDurationMs());
    }
    return result;
  }

  /** Queues every track whose duration is not known yet. Returns at once. */
  public void requestMissing(List<TrackRef> tracks) {
    for (TrackRef track : tracks) {
      if (track.hrefHash == null || track.href == null || !inFlight.add(track.hrefHash)) {
        continue;
      }
      try {
        executor.execute(() -> compute(track));
      } catch (RuntimeException e) {
        inFlight.remove(track.hrefHash);
      }
    }
  }

  private void compute(TrackRef track) {
    try {
      if (repository.existsByHrefHash(track.hrefHash)) {
        return;
      }
      Long durationMs = reader.read(track.href);
      if (durationMs == null) {
        // Left unstored on purpose: a transient fetch failure should be retried later, and the
        // in-flight guard already stops the same request storm from repeating it now.
        log.warn("No duration read for {}", track.href);
        return;
      }
      repository.save(new TrackDurationEntity(track.hrefHash, durationMs));
    } catch (Exception e) {
      log.warn("Duration computation failed for {}: {}", track.href, e.toString());
    } finally {
      inFlight.remove(track.hrefHash);
    }
  }

  @PreDestroy
  void shutdown() {
    executor.shutdownNow();
  }

  /** Minimal identity of a track: what to fetch, and what to store it under. */
  public static class TrackRef {
    public final String hrefHash;
    public final String href;

    public TrackRef(String hrefHash, String href) {
      this.hrefHash = hrefHash;
      this.href = href;
    }
  }
}
