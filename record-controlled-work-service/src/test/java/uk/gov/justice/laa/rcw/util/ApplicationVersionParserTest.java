package uk.gov.justice.laa.rcw.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ApplicationVersionParserTest {

  @Test
  void shouldParseZeroAndLeadingZerosFromIfMatch() {
    assertThat(ApplicationVersionParser.parseIfMatch("\"0\"")).isEqualTo(OptionalLong.of(0));
    assertThat(ApplicationVersionParser.parseIfMatch("\"000\"")).isEqualTo(OptionalLong.of(0));
  }

  @Test
  void shouldParseMaximumVersionFromIfMatch() {
    assertThat(ApplicationVersionParser.parseIfMatch("\"9223372036854775807\""))
        .isEqualTo(OptionalLong.of(Long.MAX_VALUE));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(
      strings = {
        "",
        "0",
        "W/\"0\"",
        "*",
        "\"-1\"",
        "\"+1\"",
        "\" 1\"",
        "\"1 \"",
        "\"1\", \"2\"",
        "\"9223372036854775808\""
      })
  void shouldRejectInvalidIfMatch(String ifMatch) {
    assertThat(ApplicationVersionParser.parseIfMatch(ifMatch)).isEqualTo(OptionalLong.empty());
  }

  @Test
  void shouldRejectNonAsciiDigitsFromIfMatch() {
    String nonAsciiDigit = Character.toString(0x0661);
    assertThat(ApplicationVersionParser.parseIfMatch("\"" + nonAsciiDigit + "\""))
        .isEqualTo(OptionalLong.empty());
  }

  @ParameterizedTest
  @ValueSource(longs = {0L, 7L, Long.MAX_VALUE})
  void shouldAcceptNonnegativeTypedVersion(long version) {
    assertThat(ApplicationVersionParser.parseVersion(version)).isEqualTo(OptionalLong.of(version));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(longs = {-1L, Long.MIN_VALUE})
  void shouldRejectNullOrNegativeTypedVersion(Long version) {
    assertThat(ApplicationVersionParser.parseVersion(version)).isEqualTo(OptionalLong.empty());
  }
}
