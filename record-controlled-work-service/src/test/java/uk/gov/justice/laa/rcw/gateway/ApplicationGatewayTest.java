package uk.gov.justice.laa.rcw.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import uk.gov.justice.laa.ia.datastore.client.api.ApplicationApi;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.StartApplicationCommand;
import uk.gov.justice.laa.ia.datastore.client.model.UpdateApplicationCommand;
import uk.gov.justice.laa.ia.datastore.client.model.UpdateEvidenceCommand;
import uk.gov.justice.laa.ia.datastore.client.model.UpdateMeansDataCommand;
import uk.gov.justice.laa.ia.datastore.client.model.UpdateScopingDataCommand;
import uk.gov.justice.laa.rcw.exception.ApplicationBadRequestException;
import uk.gov.justice.laa.rcw.exception.ApplicationConflictException;
import uk.gov.justice.laa.rcw.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.rcw.exception.ApplicationUnavailableException;
import uk.gov.justice.laa.rcw.exception.ApplicationUpstreamErrorException;
import uk.gov.justice.laa.rcw.service.BearerTokenProvider;

@ExtendWith(MockitoExtension.class)
class ApplicationGatewayTest {

  private static final String BEARER_TOKEN = "Bearer original-incoming-token";
  private static final UUID APPLICATION_ID =
      UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

  @Mock private ApplicationApi mockApplicationApi;
  @Mock private BearerTokenProvider mockBearerTokenProvider;

  private ApplicationGateway applicationGateway;

  @BeforeEach
  void setUp() {
    applicationGateway = new ApplicationGateway(mockApplicationApi, mockBearerTokenProvider);
    when(mockBearerTokenProvider.currentBearerToken()).thenReturn(BEARER_TOKEN);
  }

  @Test
  void shouldStartApplication() {
    String officeCode = "AB12CD";
    StartApplicationCommand command =
        StartApplicationCommand.builder().providerOfficeCode(officeCode).build();
    ApplicationResponse response = ApplicationResponse.builder().id(APPLICATION_ID).build();
    when(mockApplicationApi.startApplication(BEARER_TOKEN, command)).thenReturn(response);

    ApplicationResponse result = applicationGateway.startApplication(officeCode, command);

    assertThat(result).isEqualTo(response);
    verify(mockApplicationApi).startApplication(eq(BEARER_TOKEN), eq(command));
  }

  @ParameterizedTest
  @MethodSource("officeScopedErrorMappings")
  void shouldStartApplication_shouldMapDatastoreErrors(
      RuntimeException datastoreException,
      Class<? extends RuntimeException> expectedExceptionType,
      String expectedMessage) {
    String officeCode = "AB12CD";
    when(mockApplicationApi.startApplication(eq(BEARER_TOKEN), any()))
        .thenThrow(datastoreException);

    assertThatThrownBy(
            () ->
                applicationGateway.startApplication(
                    officeCode,
                    StartApplicationCommand.builder().providerOfficeCode(officeCode).build()))
        .isExactlyInstanceOf(expectedExceptionType)
        .hasMessage(expectedMessage);
  }

  @Test
  void shouldUpdateScopingData() {
    UpdateScopingDataCommand command = scopingDataCommand();

    applicationGateway.updateScopingData(APPLICATION_ID, command);

    verify(mockApplicationApi).updateScopingData(eq(APPLICATION_ID), eq(BEARER_TOKEN), eq(command));
  }

  @ParameterizedTest
  @MethodSource("applicationScopedErrorMappingsWithConflict")
  void shouldUpdateScopingData_shouldMapDatastoreErrors(
      RuntimeException datastoreException,
      Class<? extends RuntimeException> expectedExceptionType,
      String expectedMessage) {
    doThrow(datastoreException)
        .when(mockApplicationApi)
        .updateScopingData(eq(APPLICATION_ID), eq(BEARER_TOKEN), any());

    assertThatThrownBy(
            () -> applicationGateway.updateScopingData(APPLICATION_ID, scopingDataCommand()))
        .isExactlyInstanceOf(expectedExceptionType)
        .hasMessage(expectedMessage);
  }

  @Test
  void shouldFetchApplication() {
    ApplicationResponse response =
        ApplicationResponse.builder().id(APPLICATION_ID).providerOfficeCode("AB12CD").build();
    when(mockApplicationApi.getApplication(APPLICATION_ID, BEARER_TOKEN)).thenReturn(response);

    ApplicationResponse result = applicationGateway.fetchApplication(APPLICATION_ID);

    assertThat(result).isEqualTo(response);
    verify(mockApplicationApi).getApplication(eq(APPLICATION_ID), eq(BEARER_TOKEN));
  }

  @ParameterizedTest
  @MethodSource("applicationScopedErrorMappings")
  void shouldFetchApplication_shouldMapDatastoreErrors(
      RuntimeException datastoreException,
      Class<? extends RuntimeException> expectedExceptionType,
      String expectedMessage) {
    when(mockApplicationApi.getApplication(APPLICATION_ID, BEARER_TOKEN))
        .thenThrow(datastoreException);

    assertThatThrownBy(() -> applicationGateway.fetchApplication(APPLICATION_ID))
        .isExactlyInstanceOf(expectedExceptionType)
        .hasMessage(expectedMessage);
  }

  @Test
  void shouldUpdateMeansData() {
    UpdateMeansDataCommand command = meansDataCommand();

    applicationGateway.updateMeansData(APPLICATION_ID, command);

    verify(mockApplicationApi).updateMeansData(eq(APPLICATION_ID), eq(BEARER_TOKEN), eq(command));
  }

  @ParameterizedTest
  @MethodSource("applicationScopedErrorMappingsWithConflict")
  void shouldUpdateMeansData_shouldMapDatastoreErrors(
      RuntimeException datastoreException,
      Class<? extends RuntimeException> expectedExceptionType,
      String expectedMessage) {
    doThrow(datastoreException)
        .when(mockApplicationApi)
        .updateMeansData(eq(APPLICATION_ID), eq(BEARER_TOKEN), any());

    assertThatThrownBy(() -> applicationGateway.updateMeansData(APPLICATION_ID, meansDataCommand()))
        .isExactlyInstanceOf(expectedExceptionType)
        .hasMessage(expectedMessage);
  }

  @Test
  void shouldUpdateEvidence() {
    UpdateEvidenceCommand command = evidenceCommand();

    applicationGateway.updateEvidence(APPLICATION_ID, command);

    verify(mockApplicationApi).updateEvidence(eq(APPLICATION_ID), eq(BEARER_TOKEN), eq(command));
  }

  @ParameterizedTest
  @MethodSource("applicationScopedErrorMappingsWithConflict")
  void shouldUpdateEvidence_shouldMapDatastoreErrors(
      RuntimeException datastoreException,
      Class<? extends RuntimeException> expectedExceptionType,
      String expectedMessage) {
    UpdateEvidenceCommand command = evidenceCommand();
    doThrow(datastoreException)
        .when(mockApplicationApi)
        .updateEvidence(eq(APPLICATION_ID), eq(BEARER_TOKEN), any());

    assertThatThrownBy(() -> applicationGateway.updateEvidence(APPLICATION_ID, command))
        .isExactlyInstanceOf(expectedExceptionType)
        .hasMessage(expectedMessage);
  }

  @Test
  void shouldUpdateApplication() {
    UpdateApplicationCommand command = updateApplicationCommand();

    applicationGateway.updateApplication(APPLICATION_ID, command);

    verify(mockApplicationApi).updateApplication(eq(APPLICATION_ID), eq(BEARER_TOKEN), eq(command));
  }

  @ParameterizedTest
  @MethodSource("applicationScopedErrorMappingsWithConflict")
  void shouldUpdateApplication_shouldMapDatastoreErrors(
      RuntimeException datastoreException,
      Class<? extends RuntimeException> expectedExceptionType,
      String expectedMessage) {
    doThrow(datastoreException)
        .when(mockApplicationApi)
        .updateApplication(eq(APPLICATION_ID), eq(BEARER_TOKEN), any());

    assertThatThrownBy(
            () -> applicationGateway.updateApplication(APPLICATION_ID, updateApplicationCommand()))
        .isExactlyInstanceOf(expectedExceptionType)
        .hasMessage(expectedMessage);
  }

  private static Stream<Arguments> officeScopedErrorMappings() {
    return Stream.of(
        Arguments.of(
            badRequest(),
            ApplicationBadRequestException.class,
            "Datastore rejected the request for office AB12CD"),
        Arguments.of(
            serverError(),
            ApplicationUpstreamErrorException.class,
            "Datastore returned an error for office AB12CD"),
        Arguments.of(
            unavailableError(),
            ApplicationUnavailableException.class,
            "Datastore is unavailable for office AB12CD"));
  }

  private static Stream<Arguments> applicationScopedErrorMappingsWithConflict() {
    return Stream.of(
        Arguments.of(conflict(), ApplicationConflictException.class, conflictMessage()),
        Arguments.of(notFound(), ApplicationNotFoundException.class, notFoundMessage()),
        Arguments.of(badRequest(), ApplicationBadRequestException.class, badRequestMessage()),
        Arguments.of(
            serverError(), ApplicationUpstreamErrorException.class, upstreamErrorMessage()),
        Arguments.of(
            unavailableError(), ApplicationUnavailableException.class, unavailableMessage()));
  }

  private static Stream<Arguments> applicationScopedErrorMappings() {
    return Stream.of(
        Arguments.of(notFound(), ApplicationNotFoundException.class, notFoundMessage()),
        Arguments.of(badRequest(), ApplicationBadRequestException.class, badRequestMessage()),
        Arguments.of(
            serverError(), ApplicationUpstreamErrorException.class, upstreamErrorMessage()),
        Arguments.of(
            unavailableError(), ApplicationUnavailableException.class, unavailableMessage()));
  }

  private static HttpClientErrorException.NotFound notFound() {
    return (HttpClientErrorException.NotFound)
        HttpClientErrorException.create(
            HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null);
  }

  private static HttpClientErrorException.BadRequest badRequest() {
    return (HttpClientErrorException.BadRequest)
        HttpClientErrorException.create(
            HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, new byte[0], null);
  }

  private static HttpClientErrorException.Conflict conflict() {
    return (HttpClientErrorException.Conflict)
        HttpClientErrorException.create(
            HttpStatus.CONFLICT, "Conflict", HttpHeaders.EMPTY, new byte[0], null);
  }

  private static HttpServerErrorException serverError() {
    return new HttpServerErrorException(HttpStatus.BAD_GATEWAY);
  }

  private static ResourceAccessException unavailableError() {
    return new ResourceAccessException("Connection refused");
  }

  private static UpdateEvidenceCommand evidenceCommand() {
    return UpdateEvidenceCommand.builder().eTag(1L).evidenceExemptionCode("EXEMPT").build();
  }

  private static UpdateScopingDataCommand scopingDataCommand() {
    return UpdateScopingDataCommand.builder().eTag(5L).scopingQuestions(Map.of("a", "b")).build();
  }

  private static UpdateMeansDataCommand meansDataCommand() {
    return UpdateMeansDataCommand.builder().eTag(1L).data("d").result("r").build();
  }

  private static UpdateApplicationCommand updateApplicationCommand() {
    return UpdateApplicationCommand.builder()
        .eTag(2L)
        .applicationState(uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT)
        .build();
  }

  private static String notFoundMessage() {
    return "No application found with id: %s".formatted(APPLICATION_ID);
  }

  private static String conflictMessage() {
    return "Application %s was modified concurrently".formatted(APPLICATION_ID);
  }

  private static String badRequestMessage() {
    return "Datastore rejected the request for application %s".formatted(APPLICATION_ID);
  }

  private static String upstreamErrorMessage() {
    return "Datastore returned an error for application %s".formatted(APPLICATION_ID);
  }

  private static String unavailableMessage() {
    return "Datastore is unavailable for application %s".formatted(APPLICATION_ID);
  }
}
