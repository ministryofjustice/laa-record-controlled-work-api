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
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationState;
import uk.gov.justice.laa.ia.datastore.client.model.UpdateMeansDataCommand;
import uk.gov.justice.laa.rcw.exception.ApplicationConflictException;
import uk.gov.justice.laa.rcw.exception.ApplicationForbiddenException;
import uk.gov.justice.laa.rcw.gateway.ApplicationGateway;

@ExtendWith(MockitoExtension.class)
class ApplicationMeansServiceTest {

  private static final String AUTHORIZED_OFFICE_CODE = "AB12CD";

  @Mock private ApplicationGateway mockApplicationGateway;
  @Mock private AuthorizedOfficesProvider mockAuthorizedOfficesProvider;

  private ApplicationGuard applicationGuard;
  private ApplicationMeansService applicationMeansService;

  @BeforeEach
  void setUp() {
    applicationGuard = new ApplicationGuard(mockAuthorizedOfficesProvider);
    applicationMeansService = new ApplicationMeansService(mockApplicationGateway, applicationGuard);
    lenient()
        .when(mockAuthorizedOfficesProvider.currentAuthorizedOfficeCodes())
        .thenReturn(List.of(AUTHORIZED_OFFICE_CODE));
  }

  @Test
  void shouldUpdateMeans_fetchesETagAndForwardsDataAndResultToDatastore() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    Map<String, Object> data = Map.of("level_of_help", "controlled");
    Map<String, Object> result = Map.of("indication", true);
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(7L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .build());

    applicationMeansService.updateMeans(applicationId, data, result);

    verify(mockApplicationGateway).fetchApplication(eq(applicationId));

    ArgumentCaptor<UpdateMeansDataCommand> commandCaptor =
        ArgumentCaptor.forClass(UpdateMeansDataCommand.class);
    verify(mockApplicationGateway).updateMeansData(eq(applicationId), commandCaptor.capture());
    assertThat(commandCaptor.getValue())
        .isEqualTo(UpdateMeansDataCommand.builder().eTag(7L).data(data).result(result).build());
  }

  @Test
  void shouldUpdateMeans_retriesOnceWithFreshETag_whenDatastoreReturnsConflict() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    Map<String, Object> data = Map.of("level_of_help", "controlled");
    Map<String, Object> result = Map.of("indication", true);
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .build())
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(2L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .build());
    doThrow(
            new ApplicationConflictException(
                "Application %s was modified concurrently".formatted(applicationId)))
        .doNothing()
        .when(mockApplicationGateway)
        .updateMeansData(eq(applicationId), any());

    applicationMeansService.updateMeans(applicationId, data, result);

    verify(mockApplicationGateway, times(2)).fetchApplication(eq(applicationId));
    ArgumentCaptor<UpdateMeansDataCommand> commandCaptor =
        ArgumentCaptor.forClass(UpdateMeansDataCommand.class);
    verify(mockApplicationGateway, times(2))
        .updateMeansData(eq(applicationId), commandCaptor.capture());
    assertThat(commandCaptor.getAllValues())
        .extracting(UpdateMeansDataCommand::geteTag)
        .containsExactly(1L, 2L);
  }

  @Test
  void shouldUpdateMeans_throwsApplicationConflictException_whenConflictPersistsAfterRetry() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .build());
    doThrow(
            new ApplicationConflictException(
                "Application %s was modified concurrently".formatted(applicationId)))
        .when(mockApplicationGateway)
        .updateMeansData(eq(applicationId), any());

    assertThatThrownBy(() -> applicationMeansService.updateMeans(applicationId, Map.of(), Map.of()))
        .isInstanceOf(ApplicationConflictException.class)
        .hasMessageContaining(applicationId.toString());

    verify(mockApplicationGateway, times(2)).fetchApplication(eq(applicationId));
    verify(mockApplicationGateway, times(2)).updateMeansData(eq(applicationId), any());
  }

  @Test
  void shouldUpdateMeans_throwsApplicationConflictException_whenApplicationAlreadyRecorded() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .applicationState(ApplicationState.COMPLETED)
                .build());

    assertThatThrownBy(() -> applicationMeansService.updateMeans(applicationId, Map.of(), Map.of()))
        .isInstanceOf(ApplicationConflictException.class)
        .hasMessageContaining(applicationId.toString());

    verify(mockApplicationGateway, never()).updateMeansData(any(), any());
  }

  @Test
  void shouldUpdateMeans_throwsApplicationForbiddenException_whenOfficeCodeNotAuthorized() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder().eTag(1L).providerOfficeCode("OTHER-OFFICE").build());

    assertThatThrownBy(() -> applicationMeansService.updateMeans(applicationId, Map.of(), Map.of()))
        .isInstanceOf(ApplicationForbiddenException.class)
        .hasMessageContaining(applicationId.toString());

    verify(mockApplicationGateway, never()).updateMeansData(any(), any());
  }

  @Test
  void shouldUpdateMeans_throwsApplicationForbiddenException_whenNoOfficesAreAuthorized() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplication(eq(applicationId)))
        .thenReturn(
            ApplicationResponse.builder()
                .eTag(1L)
                .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                .build());
    when(mockAuthorizedOfficesProvider.currentAuthorizedOfficeCodes()).thenReturn(List.of());

    assertThatThrownBy(() -> applicationMeansService.updateMeans(applicationId, Map.of(), Map.of()))
        .isInstanceOf(ApplicationForbiddenException.class)
        .hasMessageContaining(applicationId.toString());

    verify(mockApplicationGateway, never()).updateMeansData(any(), any());
  }
}
