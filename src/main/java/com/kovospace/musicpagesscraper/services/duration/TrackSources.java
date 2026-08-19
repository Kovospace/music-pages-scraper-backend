package com.kovospace.musicpagesscraper.services.duration;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Where a track may be fetched from.
 *
 * <p>Only matters for the on-demand duration endpoint, which is the one place a client says which
 * URL to open. Everywhere else the hrefs come out of the scrapers themselves. Without this the
 * endpoint would fetch any URL anybody posted to it, on the server's behalf and from inside its
 * network — so a platform whose download prefix is not listed here simply cannot be asked.
 */
public final class TrackSources {

  private static final Map<String, String> DOWNLOAD_PREFIXES;

  static {
    Map<String, String> prefixes = new HashMap<>();
    prefixes.put("bandzone", "https://bandzone.cz/track/download/");
    prefixes.put("freeteknomusic", "http://freeteknomusic.org/mp3/");
    DOWNLOAD_PREFIXES = Collections.unmodifiableMap(prefixes);
  }

  private TrackSources() {}

  public static boolean allowed(String platform, String href) {
    if (platform == null || href == null) {
      return false;
    }
    String prefix = DOWNLOAD_PREFIXES.get(platform.toLowerCase());
    return prefix != null && href.startsWith(prefix);
  }

  public static String downloadPrefix(String platform) {
    return platform == null ? null : DOWNLOAD_PREFIXES.get(platform.toLowerCase());
  }
}
