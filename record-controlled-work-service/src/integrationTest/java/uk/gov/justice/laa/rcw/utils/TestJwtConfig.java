package uk.gov.justice.laa.rcw.utils;

import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import uk.gov.laa.springboot.oauth2.testsupport.StubJwtDecoder;
import uk.gov.laa.springboot.oauth2.testsupport.StubJwtToken;

/** Provides a stub {@link JwtDecoder} for integration tests, bypassing real JWT validation. */
@TestConfiguration
public class TestJwtConfig {

  public static final String ACCESS_TOKEN = "rcw-api-access-token";

  /** Stub {@link JwtDecoder} seeded with a token containing the Applications.Read role. */
  @Bean
  public JwtDecoder jwtDecoder() {
    return StubJwtDecoder.of(
        new StubJwtToken(
            ACCESS_TOKEN, "test-user", new String[] {"Applications.Read"}, null, Map.of()));
  }
}
