package uk.gov.justice.laa.rcw.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Detects regressions that buffer an entire request before checking its size.
 *
 * <p>Body-limit tests allow the configured limit plus one byte, enough to detect an oversized
 * request. Reading any further throws instead of returning EOF, so an unrestricted {@code
 * readAllBytes()} fails the test even if the request would eventually receive HTTP 413. A finite
 * byte-array stream would not reveal that the complete body had been consumed first.
 *
 * <p>The stream supplies whitespace without allocating a large payload. This proves early rejection
 * still bounds reads when Content-Length is missing or inaccurate.
 */
public final class GuardedInputStream extends InputStream {

  private final int maxBytes;
  private int bytesRead;

  /** Creates a stream allowing the specified number of bytes to be read. */
  public GuardedInputStream(int maxBytes) {
    this.maxBytes = maxBytes;
  }

  @Override
  public int read() throws IOException {
    if (bytesRead == maxBytes) {
      throw new IOException("guarded stream read limit exceeded");
    }
    bytesRead++;
    return ' ';
  }

  @Override
  public int read(byte[] bytes, int offset, int length) throws IOException {
    if (length == 0) {
      return 0;
    }
    if (bytesRead == maxBytes) {
      throw new IOException("guarded stream read limit exceeded");
    }
    int count = Math.min(length, maxBytes - bytesRead);
    Arrays.fill(bytes, offset, offset + count, (byte) ' ');
    bytesRead += count;
    return count;
  }

  /** Returns the number of bytes consumed by the test. */
  public int bytesRead() {
    return bytesRead;
  }
}
