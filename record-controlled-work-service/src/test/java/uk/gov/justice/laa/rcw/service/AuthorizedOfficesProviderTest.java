package uk.gov.justice.laa.rcw.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AuthorizedOfficesProviderTest {

  private final AuthorizedOfficesProvider authorizedOfficesProvider =
      new AuthorizedOfficesProvider();

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldReturnOfficeCodes_whenLaaAccountsClaimIsPresent() {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("sub", "test-user")
            .claim("LAA_ACCOUNTS", List.of("AB12CD", "XY34ZT"))
            .build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

    assertThat(authorizedOfficesProvider.currentAuthorizedOfficeCodes())
        .containsExactly("AB12CD", "XY34ZT");
  }

  @Test
  void shouldReturnEmptyList_whenLaaAccountsClaimIsMissing() {
    Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").claim("sub", "test-user").build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

    assertThat(authorizedOfficesProvider.currentAuthorizedOfficeCodes()).isEmpty();
  }

  @Test
  void shouldReturnEmptyList_whenLaaAccountsClaimIsEmpty() {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("sub", "test-user")
            .claim("LAA_ACCOUNTS", List.of())
            .build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

    assertThat(authorizedOfficesProvider.currentAuthorizedOfficeCodes()).isEmpty();
  }

  @Test
  void shouldReturnEmptyList_whenNoOAuth2TokenAuthenticationIsPresent() {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("user", "credentials"));

    assertThat(authorizedOfficesProvider.currentAuthorizedOfficeCodes()).isEmpty();
  }
}
