package com.kovospace.musicpagesscraper.services.duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Reads the true playing time out of an MP3.
 *
 * <p>Two strategies, cheapest first:
 *
 * <ol>
 *   <li><b>VBR header</b> — most files start with a Xing/Info/VBRI header holding the exact frame
 *       count. It sits in the first frame, so a small range request answers the question outright.
 *   <li><b>Frame count</b> — files without that header (bandzone has whole albums of them) have to
 *       be walked frame by frame. Only these pay for a full download.
 * </ol>
 *
 * <p>Estimating from the first frame's bitrate — what a naive player does — is not an option here:
 * on a variable-bitrate file opening with a quiet 32 kbps lead-in it overshoots by 6x.
 */
@Component
public class Mp3DurationReader {

  private static final Logger log = LoggerFactory.getLogger(Mp3DurationReader.class);

  private static final int HEADER_PROBE_BYTES = 64 * 1024;
  private static final int CONNECT_TIMEOUT_MS = 10_000;
  private static final int READ_TIMEOUT_MS = 60_000;
  private static final int MAX_BYTES = 64 * 1024 * 1024;

  // Layer III bitrates in kbps, indexed by the header's bitrate bits.
  private static final int[] BITRATES_MPEG1 =
    {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0};
  private static final int[] BITRATES_MPEG2 =
    {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0};
  private static final int[] RATES_MPEG1 = {44100, 48000, 32000};
  private static final int[] RATES_MPEG2 = {22050, 24000, 16000};
  private static final int[] RATES_MPEG25 = {11025, 12000, 8000};

  /**
   * @return playing time in milliseconds, or null when the file could not be read or parsed.
   */
  public Long read(String url) {
    try {
      byte[] probe = download(url, HEADER_PROBE_BYTES);
      if (probe == null || probe.length == 0) {
        return null;
      }
      Long fromHeader = fromVbrHeader(probe);
      if (fromHeader != null) {
        return fromHeader;
      }
      byte[] whole = download(url, -1);
      if (whole == null) {
        return null;
      }
      return byCountingFrames(whole);
    } catch (Exception e) {
      log.warn("Could not read duration of {}: {}", url, e.toString());
      return null;
    }
  }

  private byte[] download(String url, int maxBytes) throws IOException {
    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
    conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
    conn.setReadTimeout(READ_TIMEOUT_MS);
    conn.setInstanceFollowRedirects(true);
    conn.setRequestProperty("User-Agent", "music-pages-scraper");
    if (maxBytes > 0) {
      conn.setRequestProperty("Range", "bytes=0-" + (maxBytes - 1));
    }
    try (InputStream in = conn.getInputStream()) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buf = new byte[8192];
      int limit = maxBytes > 0 ? maxBytes : MAX_BYTES;
      int n;
      while ((n = in.read(buf)) != -1) {
        out.write(buf, 0, n);
        if (out.size() >= limit) {
          break;
        }
      }
      return out.toByteArray();
    } finally {
      conn.disconnect();
    }
  }

  /** Start of audio, skipping an ID3v2 tag when present. */
  private int audioStart(byte[] d) {
    if (d.length > 10 && d[0] == 'I' && d[1] == 'D' && d[2] == '3') {
      int size = ((d[6] & 0x7F) << 21) | ((d[7] & 0x7F) << 14) | ((d[8] & 0x7F) << 7) | (d[9] & 0x7F);
      return 10 + size;
    }
    return 0;
  }

  private boolean isFrameHeader(byte[] d, int i) {
    if (i + 4 > d.length) {
      return false;
    }
    if ((d[i] & 0xFF) != 0xFF || (d[i + 1] & 0xE0) != 0xE0) {
      return false;
    }
    int version = (d[i + 1] >> 3) & 3;   // 0 = MPEG2.5, 1 = reserved, 2 = MPEG2, 3 = MPEG1
    int layer = (d[i + 1] >> 1) & 3;     // 1 = Layer III
    int bitrateIdx = (d[i + 2] >> 4) & 0xF;
    int rateIdx = (d[i + 2] >> 2) & 3;
    return version != 1 && layer == 1 && bitrateIdx != 0 && bitrateIdx != 0xF && rateIdx != 3;
  }

  private int sampleRate(byte[] d, int i) {
    int version = (d[i + 1] >> 3) & 3;
    int rateIdx = (d[i + 2] >> 2) & 3;
    if (version == 3) {
      return RATES_MPEG1[rateIdx];
    }
    return version == 2 ? RATES_MPEG2[rateIdx] : RATES_MPEG25[rateIdx];
  }

  private int samplesPerFrame(byte[] d, int i) {
    return ((d[i + 1] >> 3) & 3) == 3 ? 1152 : 576;
  }

  private int frameLength(byte[] d, int i) {
    int version = (d[i + 1] >> 3) & 3;
    int bitrateIdx = (d[i + 2] >> 4) & 0xF;
    int padding = (d[i + 2] >> 1) & 1;
    int bitrate = (version == 3 ? BITRATES_MPEG1[bitrateIdx] : BITRATES_MPEG2[bitrateIdx]) * 1000;
    int coefficient = version == 3 ? 144 : 72;
    return (coefficient * bitrate / sampleRate(d, i)) + padding;
  }

  private int findFirstFrame(byte[] d) {
    for (int i = audioStart(d); i < d.length - 4; i++) {
      if (isFrameHeader(d, i)) {
        return i;
      }
    }
    return -1;
  }

  private Long fromVbrHeader(byte[] d) {
    int f = findFirstFrame(d);
    if (f < 0) {
      return null;
    }
    int version = (d[f + 1] >> 3) & 3;
    boolean mono = ((d[f + 3] >> 6) & 3) == 3;
    int sideInfo = version == 3 ? (mono ? 17 : 32) : (mono ? 9 : 17);

    int xing = f + 4 + sideInfo;
    if (matches(d, xing, "Xing") || matches(d, xing, "Info")) {
      int flags = readInt(d, xing + 4);
      if (flags == Integer.MIN_VALUE || (flags & 1) == 0) {
        return null; // no frame count in this header, fall back to counting
      }
      int frames = readInt(d, xing + 8);
      return frames <= 0 ? null : toMillis(frames, samplesPerFrame(d, f), sampleRate(d, f));
    }

    int vbri = f + 4 + 32;
    if (matches(d, vbri, "VBRI")) {
      int frames = readInt(d, vbri + 14);
      return frames <= 0 ? null : toMillis(frames, samplesPerFrame(d, f), sampleRate(d, f));
    }
    return null;
  }

  private Long byCountingFrames(byte[] d) {
    int i = findFirstFrame(d);
    if (i < 0) {
      return null;
    }
    int samplesPerFrame = samplesPerFrame(d, i);
    int sampleRate = sampleRate(d, i);
    long frames = 0;
    while (i < d.length - 4) {
      if (isFrameHeader(d, i)) {
        frames++;
        int len = frameLength(d, i);
        if (len <= 4) {
          break;
        }
        i += len;
      } else {
        i++; // resync past padding or a damaged frame
      }
    }
    return frames == 0 ? null : toMillis(frames, samplesPerFrame, sampleRate);
  }

  private Long toMillis(long frames, int samplesPerFrame, int sampleRate) {
    if (sampleRate <= 0) {
      return null;
    }
    return frames * samplesPerFrame * 1000L / sampleRate;
  }

  private boolean matches(byte[] d, int off, String tag) {
    if (off < 0 || off + tag.length() > d.length) {
      return false;
    }
    for (int i = 0; i < tag.length(); i++) {
      if (d[off + i] != tag.charAt(i)) {
        return false;
      }
    }
    return true;
  }

  private int readInt(byte[] d, int off) {
    if (off < 0 || off + 4 > d.length) {
      return Integer.MIN_VALUE;
    }
    return ((d[off] & 0xFF) << 24) | ((d[off + 1] & 0xFF) << 16)
         | ((d[off + 2] & 0xFF) << 8) | (d[off + 3] & 0xFF);
  }
}
