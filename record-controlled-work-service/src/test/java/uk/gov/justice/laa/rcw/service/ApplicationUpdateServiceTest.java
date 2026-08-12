package uk.gov.justice.laa.rcw.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.UpdateApplicationCommand;
import uk.gov.justice.laa.rcw.exception.ApplicationConflictException;
import uk.gov.justice.laa.rcw.exception.ApplicationForbiddenException;
import uk.gov.justice.laa.rcw.gateway.ApplicationGateway;
import uk.gov.justice.laa.rcw.mapper.ApplicationMapper;
import uk.gov.justice.laa.rcw.model.ApplicationState;

@ExtendWith(MockitoExtension.class)
class ApplicationUpdateServiceTest {

  private static final String AUTHORIZED_OFFICE_CODE = "AB12CD";

  @Mock private ApplicationMapper mockApplicationMapper;
  @Mock private ApplicationGateway mockApplicationGateway;
  @Mock private AuthorizedOfficesProvider mockAuthorizedOfficesProvider;

  private ApplicationGuard applicationGuard;
  private ApplicationUpdateService applicationUpdateService;

  @BeforeEach
  void setUp() {
    applicationGuard = new ApplicationGuard(mockAuthorizedOfficesProvider);
    applicationUpdateService =
        new ApplicationUpdateService(
            mockApplicationMapper, mockApplicationGateway, applicationGuard);
    lenient()
        .when(mockAuthorizedOfficesProvider.currentAuthorizedOfficeCodes())
        .thenReturn(List.of(AUTHORIZED_OFFICE_CODE));
    lenient()
        .when(mockApplicationMapper.toDatastoreApplicationState(any()))
        .thenReturn(uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.COMPLETED);
  }

  @Test
  void shouldUpdateStatus_fetchesETagAndForwardsStatusToDatastore() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(7L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .applicationState(
                    uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT)
                .build());

    applicationUpdateService.updateStatus(applicationId, ApplicationState.COMPLETED);

    verify(mockApplicationGateway).fetchApplication(eq(applicationId));

    ArgumentCaptor<UpdateApplicationCommand> commandCaptor =
        ArgumentCaptor.forClass(UpdateApplicationCommand.class);
    verify(mockApplicationGateway).updateApplication(eq(applicationId), commandCaptor.capture());
    assertThat(commandCaptor.getValue().geteTag()).isEqualTo(7L);
    assertThat(commandCaptor.getValue().getApplicationState())
        .isEqualTo(uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.COMPLETED);
  }

  @Test
  void shouldUpdateStatus_retriesOnceWithFreshETag_whenDatastoreReturnsConflict() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .applicationState(
                    uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT)
                .build())
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(2L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .applicationState(
                    uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT)
                .build());
    doThrow(
            new ApplicationConflictException(
                "Application %s was modified concurrently".formatted(applicationId)))
        .doNothing()
        .when(mockApplicationGateway)
        .updateApplication(eq(applicationId), any());

    applicationUpdateService.updateStatus(applicationId, ApplicationState.COMPLETED);

    verify(mockApplicationGateway, times(2)).fetchApplication(eq(applicationId));
    ArgumentCaptor<UpdateApplicationCommand> commandCaptor =
        ArgumentCaptor.forClass(UpdateApplicationCommand.class);
    verify(mockApplicationGateway, times(2))
        .updateApplication(eq(applicationId), commandCaptor.capture());
    assertThat(commandCaptor.getAllValues())
        .extracting(UpdateApplicationCommand::geteTag)
        .containsExactly(1L, 2L);
  }

  @Test
  void shouldUpdateStatus_throwsApplicationConflictException_whenConflictPersistsAfterRetry() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .applicationState(
                    uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT)
                .build());
    doThrow(
            new ApplicationConflictException(
                "Application %s was modified concurrently".formatted(applicationId)))
        .when(mockApplicationGateway)
        .updateApplication(eq(applicationId), any());

    assertThatThrownBy(
            () -> applicationUpdateService.updateStatus(applicationId, ApplicationState.COMPLETED))
        .isInstanceOf(ApplicationConflictException.class)
        .hasMessageContaining(applicationId.toString());

    verify(mockApplicationGateway, times(2)).fetchApplication(eq(applicationId));
    verify(mockApplicationGateway, times(2)).updateApplication(eq(applicationId), any());
  }

  @Test
  void shouldUpdateStatus_throwsApplicationConflictException_whenApplicationAlreadyRecorded() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .applicationState(
                    uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.COMPLETED)
                .build());

    assertThatThrownBy(
            () -> applicationUpdateService.updateStatus(applicationId, ApplicationState.COMPLETED))
        .isInstanceOf(ApplicationConflictException.class)
        .hasMessageContaining(applicationId.toString());

    verify(mockApplicationGateway, never()).updateApplication(any(), any());
  }

  @Test
  void shouldUpdateStatus_throwsApplicationForbiddenException_whenOfficeCodeNotAuthorized() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode("OTHER-OFFICE")
                .applicationState(
                    uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT)
                .build());

    assertThatThrownBy(
            () -> applicationUpdateService.updateStatus(applicationId, ApplicationState.COMPLETED))
        .isInstanceOf(ApplicationForbiddenException.class)
        .hasMessageContaining(applicationId.toString());

    verify(mockApplicationGateway, never()).updateApplication(any(), any());
  }

  @Test
  void shouldUpdateStatus_throwsApplicationForbiddenException_whenNoOfficesAreAuthorized() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .applicationState(
                    uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT)
                .build());
    when(mockAuthorizedOfficesProvider.currentAuthorizedOfficeCodes()).thenReturn(List.of());

    assertThatThrownBy(
            () -> applicationUpdateService.updateStatus(applicationId, ApplicationState.COMPLETED))
        .isInstanceOf(ApplicationForbiddenException.class)
        .hasMessageContaining(applicationId.toString());

    verify(mockApplicationGateway, never()).updateApplication(any(), any());
  }
}
