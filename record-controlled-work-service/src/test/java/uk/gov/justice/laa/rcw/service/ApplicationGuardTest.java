package uk.gov.justice.laa.rcw.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.rcw.exception.ApplicationForbiddenException;
import uk.gov.justice.laa.rcw.exception.ApplicationNotFoundException;

@ExtendWith(MockitoExtension.class)
class ApplicationGuardTest {

  private static final UUID APPLICATION_ID =
      UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

  @Mock private AuthorizedOfficesProvider mockAuthorizedOfficesProvider;

  private ApplicationGuard applicationGuard;

  @BeforeEach
  void setUp() {
    applicationGuard = new ApplicationGuard(mockAuthorizedOfficesProvider);
  }

  @Test
  void shouldAllowVisibleAuthorizedOffice() {
    when(mockAuthorizedOfficesProvider.currentAuthorizedOfficeCodes())
        .thenReturn(List.of("AB12CD"));

    assertThatCode(() -> applicationGuard.checkVisibleForOffice(APPLICATION_ID, "AB12CD"))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldHideUnauthorizedOfficeAsNotFound() {
    when(mockAuthorizedOfficesProvider.currentAuthorizedOfficeCodes()).thenReturn(List.of());

    assertThatThrownBy(() -> applicationGuard.checkVisibleForOffice(APPLICATION_ID, "AB12CD"))
        .isExactlyInstanceOf(ApplicationNotFoundException.class)
        .hasMessage("No application found with id: " + APPLICATION_ID)
        .satisfies(
            exception ->
                org.assertj.core.api.Assertions.assertThat(
                        ((ApplicationNotFoundException) exception).getReason())
                    .isEqualTo("APPLICATION_NOT_FOUND"));
  }

  @Test
  void shouldKeepUpdateAuthorizationForbiddenForUnauthorizedOffice() {
    when(mockAuthorizedOfficesProvider.currentAuthorizedOfficeCodes()).thenReturn(List.of());

    assertThatThrownBy(() -> applicationGuard.checkAuthorizedForOffice(APPLICATION_ID, "AB12CD"))
        .isExactlyInstanceOf(ApplicationForbiddenException.class);
  }
}
