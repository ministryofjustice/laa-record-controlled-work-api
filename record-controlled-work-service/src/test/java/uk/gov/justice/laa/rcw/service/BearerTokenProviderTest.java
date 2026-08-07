package uk.gov.justice.laa.rcw.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class BearerTokenProviderTest {

  private final BearerTokenProvider bearerTokenProvider = new BearerTokenProvider();

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldReturnBearerToken_whenOAuth2TokenAuthenticationIsPresent() {
    Jwt jwt =
        Jwt.withTokenValue("original-incoming-token")
            .header("alg", "none")
            .claim("sub", "test-user")
            .build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

    assertThat(bearerTokenProvider.currentBearerToken())
        .isEqualTo("Bearer original-incoming-token");
  }

  @Test
  void shouldThrowIllegalStateException_whenNoOAuth2TokenAuthenticationIsPresent() {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("user", "credentials"));

    assertThatThrownBy(bearerTokenProvider::currentBearerToken)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No authenticated token available");
  }
}
