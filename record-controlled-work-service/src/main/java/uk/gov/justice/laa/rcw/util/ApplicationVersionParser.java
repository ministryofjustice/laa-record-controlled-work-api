package uk.gov.justice.laa.rcw.util;

import java.util.OptionalLong;
import java.util.regex.Pattern;

/** Parses RCW version headers and validates typed datastore versions. */
public final class ApplicationVersionParser {

  private static final Pattern IF_MATCH_PATTERN = Pattern.compile("\"[0-9]+\"");

  private ApplicationVersionParser() {}

  /** Parses a quoted, nonnegative decimal {@code If-Match} version. */
  public static OptionalLong parseIfMatch(String ifMatch) {
    if (ifMatch == null || !IF_MATCH_PATTERN.matcher(ifMatch).matches()) {
      return OptionalLong.empty();
    }
    try {
      return OptionalLong.of(Long.parseLong(ifMatch.substring(1, ifMatch.length() - 1)));
    } catch (NumberFormatException exception) {
      return OptionalLong.empty();
    }
  }

  /** Validates a typed datastore version as a nonnegative signed long. */
  public static OptionalLong parseVersion(Long version) {
    return version == null || version < 0 ? OptionalLong.empty() : OptionalLong.of(version);
  }
}
