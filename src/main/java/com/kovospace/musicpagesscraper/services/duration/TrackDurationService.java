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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Serves known track durations, computes the missing ones off the request thread, and computes the
 * one track a client is waiting on right now.
 *
 * <p>Reading a duration means fetching audio, so a band profile can never do it inline: it is up to
 * a couple of dozen tracks of several MB each. Such a request answers immediately with whatever is
 * already known and leaves the rest to the pool — the next request for the same band finds them
 * filled in.
 *
 * <p>That pool is deliberately small, which makes it useless for the track a player has just
 * started: the file could sit behind hundreds of queued ones. {@link #readNow} therefore does not
 * use the pool at all. It reads on the caller's own thread, and when the track is queued already it
 * <em>steals</em> it — the claim flag lets exactly one of the two read the file, whichever gets
 * there first, while the other waits for that same answer.
 *
 * <p>A duration never changes, so there is no expiry; the row is written once and kept.
 */
@Service
public class TrackDurationService {

  private static final Logger log = LoggerFactory.getLogger(TrackDurationService.class);

  private static final int WORKERS = 2;
  private static final int QUEUE_CAPACITY = 500;

  /**
   * How many tracks may be read on request threads at once. Reading is bandwidth-bound, so a burst
   * of players starting together would otherwise pull every file in parallel and make all of them
   * slower than they would have been one after another.
   */
  private static final int ON_DEMAND_READERS = 4;

  private final TrackDurationRepository repository;
  private final Mp3DurationReader reader;

  /** Reads queued or running, keyed by hrefHash, so the same file is never fetched twice at once. */
  private final ConcurrentHashMap<String, Job> jobs = new ConcurrentHashMap<>();

  private final Semaphore onDemandReaders = new Semaphore(ON_DEMAND_READERS);

  private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
    WORKERS, WORKERS, 0L, TimeUnit.MILLISECONDS,
    new ArrayBlockingQueue<>(QUEUE_CAPACITY),
    runnable -> {
      Thread t = new Thread(runnable, "track-duration");
      t.setDaemon(true);
      return t;
    },
    // A full queue means the backlog is already long, so dropping the work is fine - it is
    // re-offered by the next request that misses the same track. The job has to be dropped with
    // it, which is why this aborts loudly instead of discarding silently.
    new ThreadPoolExecutor.AbortPolicy());

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
      if (track.hrefHash == null || track.href == null) {
        continue;
      }
      Job job = jobs.computeIfAbsent(track.hrefHash, hash -> new Job(track));
      if (!job.queued.compareAndSet(false, true)) {
        continue;
      }
      try {
        executor.execute(() -> {
          if (job.claim()) {
            compute(job);
          }
        });
      } catch (RejectedExecutionException e) {
        jobs.remove(track.hrefHash, job);
      }
    }
  }

  /**
   * The duration of one track, read now rather than in the background.
   *
   * @param timeoutMs how long to wait when another thread is already reading this same file; a read
   *                  started here is bounded by the reader's own connect and read timeouts instead
   * @return the duration in milliseconds, or null when it could not be read
   */
  public Long readNow(TrackRef track, long timeoutMs) {
    if (track.hrefHash == null || track.href == null) {
      return null;
    }

    Long stored = known(Collections.singletonList(track.hrefHash)).get(track.hrefHash);
    if (stored != null) {
      return stored;
    }

    Job job = jobs.computeIfAbsent(track.hrefHash, hash -> new Job(track));
    if (job.claim()) {
      // Ours to read: either nobody had asked for this track, or it was still sitting in the queue
      // and we took it off the worker's hands.
      boolean reading = false;
      try {
        // Waiting a turn beats answering "unknown" the moment a few players start together: a read
        // takes seconds, so the queue in front is short. Only a timeout gives up, and then the job
        // is handed back so the band endpoint can queue it again.
        reading = onDemandReaders.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      if (!reading) {
        return finish(job, null);
      }
      try {
        return compute(job);
      } finally {
        onDemandReaders.release();
      }
    }

    // A background worker, or another player, is already reading this exact file - wait for that
    // answer instead of pulling the same megabytes down a second time.
    try {
      return job.result.get(timeoutMs, TimeUnit.MILLISECONDS);
    } catch (Exception e) {
      return null;
    }
  }

  private Long compute(Job job) {
    TrackRef track = job.track;
    try {
      Long stored = known(Collections.singletonList(track.hrefHash)).get(track.hrefHash);
      if (stored != null) {
        return finish(job, stored);
      }
      Long durationMs = reader.read(track.href);
      if (durationMs == null) {
        // Left unstored on purpose: a transient fetch failure should be retried later, and dropping
        // the job below is what lets the next request queue it again.
        log.warn("No duration read for {}", track.href);
        return finish(job, null);
      }
      repository.save(new TrackDurationEntity(track.hrefHash, durationMs));
      return finish(job, durationMs);
    } catch (Exception e) {
      log.warn("Duration computation failed for {}: {}", track.href, e.toString());
      return finish(job, null);
    }
  }

  /** Publishes the answer to whoever is waiting, and lets the track be asked for again later. */
  private Long finish(Job job, Long durationMs) {
    jobs.remove(job.track.hrefHash, job);
    job.result.complete(durationMs);
    return durationMs;
  }

  @PreDestroy
  void shutdown() {
    executor.shutdownNow();
  }

  /** One track's read. Whoever claims it does the work; everyone else waits on {@code result}. */
  private static final class Job {
    final TrackRef track;
    final AtomicBoolean queued = new AtomicBoolean();
    final AtomicBoolean claimed = new AtomicBoolean();
    final CompletableFuture<Long> result = new CompletableFuture<>();

    Job(TrackRef track) {
      this.track = track;
    }

    boolean claim() {
      return claimed.compareAndSet(false, true);
    }
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
