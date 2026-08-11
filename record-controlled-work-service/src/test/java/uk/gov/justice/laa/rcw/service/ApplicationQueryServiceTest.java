package uk.gov.justice.laa.rcw.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import uk.gov.justice.laa.ia.datastore.client.api.ApplicationApi;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponses;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationState;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationSummary;
import uk.gov.justice.laa.rcw.exception.ApplicationBadRequestException;
import uk.gov.justice.laa.rcw.exception.ApplicationConflictException;
import uk.gov.justice.laa.rcw.exception.ApplicationForbiddenException;
import uk.gov.justice.laa.rcw.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.rcw.exception.ApplicationUnavailableException;
import uk.gov.justice.laa.rcw.exception.ApplicationUpstreamErrorException;
import uk.gov.justice.laa.rcw.mapper.ApplicationMapper;
import uk.gov.justice.laa.rcw.mapper.ApplicationMapperImpl;
import uk.gov.justice.laa.rcw.model.Application;
import uk.gov.justice.laa.rcw.model.ApplicationOverview;

@ExtendWith(MockitoExtension.class)
class ApplicationQueryServiceTest {

  private static final String ORIGINAL_TOKEN = "original-incoming-token";

  @Mock private ApplicationApi mockApplicationApi;
  @Mock private AuthorizedOfficesProvider mockAuthorizedOfficesProvider;

  private final ApplicationMapper applicationMapper = new ApplicationMapperImpl();
  private final BearerTokenProvider bearerTokenProvider = new BearerTokenProvider();
  private ApplicationQueryService applicationQueryService;

  @BeforeEach
  void setUp() {
    applicationQueryService =
        new ApplicationQueryService(
            mockApplicationApi,
            applicationMapper,
            bearerTokenProvider,
            mockAuthorizedOfficesProvider);
    Jwt jwt =
        Jwt.withTokenValue(ORIGINAL_TOKEN).header("alg", "none").claim("sub", "test-user").build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldGetApplications_mapsDatastoreResponseToApplicationOverviews() {
    OffsetDateTime modifiedAt = OffsetDateTime.now();
    UUID applicationId = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    ApplicationSummary summary =
        ApplicationSummary.builder()
            .id(applicationId)
            .clientFirstName("Joe")
            .clientLastName("Bloggs")
            .referenceNumber("CW-111111")
            .modifiedAt(modifiedAt)
            .build();
    when(mockApplicationApi.getApplications(anyString(), any(), any(), any(), any()))
        .thenReturn(ApplicationResponses.builder().content(List.of(summary)).build());

    List<ApplicationOverview> result =
        applicationQueryService.getApplications(
            1, 25, null, uk.gov.justice.laa.rcw.model.ApplicationState.DRAFT);

    assertThat(result)
        .containsExactly(
            ApplicationOverview.builder()
                .id(applicationId)
                .name("Joe Bloggs")
                .applicationRefNumber("CW-111111")
                .modifiedAt(modifiedAt)
                .build());
  }

  @Test
  void shouldGetApplications_returnsEmptyListWhenNoContent() {
    when(mockApplicationApi.getApplications(anyString(), any(), any(), any(), any()))
        .thenReturn(ApplicationResponses.builder().content(List.of()).build());

    List<ApplicationOverview> result = applicationQueryService.getApplications(0, 25, null, null);

    assertThat(result).isEmpty();
  }

  @Test
  void shouldGetApplications_forwardsPageSizeAndOfficeIdToDatastore() {
    String officeId = "22439e72-68d3-4770-b435-c352d883d21e";
    when(mockApplicationApi.getApplications(anyString(), any(), any(), any(), any()))
        .thenReturn(ApplicationResponses.builder().content(List.of()).build());

    applicationQueryService.getApplications(
        2, 50, officeId, uk.gov.justice.laa.rcw.model.ApplicationState.COMPLETED);

    // CHECKSTYLE.SUPPRESS: LocalVariableName
    ArgumentCaptor<String> xAuthorizationCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Integer> pageCaptor = ArgumentCaptor.forClass(Integer.class);
    ArgumentCaptor<Integer> sizeCaptor = ArgumentCaptor.forClass(Integer.class);
    ArgumentCaptor<String> officeIdCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<ApplicationState> statusCaptor = ArgumentCaptor.forClass(ApplicationState.class);
    verify(mockApplicationApi)
        .getApplications(
            xAuthorizationCaptor.capture(),
            pageCaptor.capture(),
            sizeCaptor.capture(),
            officeIdCaptor.capture(),
            statusCaptor.capture());

    assertThat(xAuthorizationCaptor.getValue()).isEqualTo("Bearer " + ORIGINAL_TOKEN);
    assertThat(pageCaptor.getValue()).isEqualTo(2);
    assertThat(sizeCaptor.getValue()).isEqualTo(50);
    assertThat(officeIdCaptor.getValue()).isEqualTo(officeId);
    assertThat(statusCaptor.getValue()).isEqualTo(ApplicationState.COMPLETED);
  }

  @Test
  void shouldGetApplicationById() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationApi.getApplication(eq(applicationId), anyString()))
        .thenReturn(
            ApplicationResponse.builder()
                .id(applicationId)
                .providerFirmCode("123456")
                .providerOfficeCode("22439e72-68d3-4770-b435-c352d883d21e")
                .ecfFlag(false)
                .applicationType("CONTROLLED_WORK")
                .createdBy("Random User")
                .modifiedBy("Random User")
                .build());

    Optional<Application> result = applicationQueryService.getApplication(applicationId);

    assertThat(result).isPresent();
    assertThat(result.get().getId()).isEqualTo(applicationId);
    assertThat(result.get().getProviderFirmCode()).isEqualTo("123456");
    assertThat(result.get().getApplicationType()).isEqualTo("CONTROLLED_WORK");
  }

  @Test
  void shouldGetApplicationById_forwardsBearerToken() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationApi.getApplication(eq(applicationId), anyString()))
        .thenReturn(ApplicationResponse.builder().id(applicationId).build());

    applicationQueryService.getApplication(applicationId);

    // CHECKSTYLE.SUPPRESS: LocalVariableName
    ArgumentCaptor<String> xAuthorizationCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockApplicationApi).getApplication(eq(applicationId), xAuthorizationCaptor.capture());
    assertThat(xAuthorizationCaptor.getValue()).isEqualTo("Bearer " + ORIGINAL_TOKEN);
  }

  @Test
  void shouldGetApplicationById_returnsEmptyWhenDatastoreReturnsNotFound() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationApi.getApplication(eq(applicationId), anyString()))
        .thenThrow(
            HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not found", HttpHeaders.EMPTY, new byte[0], null));

    Optional<Application> result = applicationQueryService.getApplication(applicationId);

    assertThat(result).isEmpty();
  }

  @Test
  void shouldFetchApplicationResponse_returnsRawResponse() {
    UUID applicationId = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    ApplicationResponse expected = ApplicationResponse.builder().id(applicationId).eTag(5L).build();
    when(mockApplicationApi.getApplication(eq(applicationId), anyString())).thenReturn(expected);

    ApplicationResponse result = applicationQueryService.fetchApplicationResponse(applicationId);

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void shouldFetchApplicationResponse_throwsApplicationNotFoundException_whenNotFound() {
    UUID applicationId = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    when(mockApplicationApi.getApplication(eq(applicationId), anyString()))
        .thenThrow(
            HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null));

    assertThatThrownBy(() -> applicationQueryService.fetchApplicationResponse(applicationId))
        .isInstanceOf(ApplicationNotFoundException.class)
        .hasMessageContaining(applicationId.toString());
  }

  @Test
  void shouldFetchApplicationResponse_throwsApplicationBadRequestException_whenBadRequest() {
    UUID applicationId = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    when(mockApplicationApi.getApplication(eq(applicationId), anyString()))
        .thenThrow(
            HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, new byte[0], null));

    assertThatThrownBy(() -> applicationQueryService.fetchApplicationResponse(applicationId))
        .isInstanceOf(ApplicationBadRequestException.class)
        .hasMessageContaining(applicationId.toString());
  }

  @Test
  void shouldFetchApplicationResponse_throwsApplicationUpstreamErrorException_whenServerError() {
    UUID applicationId = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    when(mockApplicationApi.getApplication(eq(applicationId), anyString()))
        .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

    assertThatThrownBy(() -> applicationQueryService.fetchApplicationResponse(applicationId))
        .isInstanceOf(ApplicationUpstreamErrorException.class)
        .hasMessageContaining(applicationId.toString());
  }

  @Test
  void shouldFetchApplicationResponse_throwsApplicationUnavailableException_whenConnectionFails() {
    UUID applicationId = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    when(mockApplicationApi.getApplication(eq(applicationId), anyString()))
        .thenThrow(new ResourceAccessException("Connection refused"));

    assertThatThrownBy(() -> applicationQueryService.fetchApplicationResponse(applicationId))
        .isInstanceOf(ApplicationUnavailableException.class)
        .hasMessageContaining(applicationId.toString());
  }

  @Test
  void shouldCheckAuthorizedForOffice_doesNotThrow_whenOfficeIsAuthorized() {
    UUID applicationId = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    when(mockAuthorizedOfficesProvider.currentAuthorizedOfficeCodes())
        .thenReturn(List.of("AB12CD", "XY34ZT"));

    applicationQueryService.checkAuthorizedForOffice(applicationId, "AB12CD");
  }

  @Test
  void shouldCheckAuthorizedForOffice_throwsForbiddenException_whenOfficeNotAuthorized() {
    UUID applicationId = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    when(mockAuthorizedOfficesProvider.currentAuthorizedOfficeCodes())
        .thenReturn(List.of("AB12CD"));

    assertThatThrownBy(
            () -> applicationQueryService.checkAuthorizedForOffice(applicationId, "OTHER-OFFICE"))
        .isInstanceOf(ApplicationForbiddenException.class)
        .hasMessageContaining(applicationId.toString());
  }

  @Test
  void shouldCheckNotAlreadyRecorded_doesNotThrow_whenStateIsDraft() {
    UUID applicationId = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");

    applicationQueryService.checkNotAlreadyRecorded(applicationId, ApplicationState.DRAFT);
  }

  @Test
  void shouldCheckNotAlreadyRecorded_throwsConflictException_whenStateIsCompleted() {
    UUID applicationId = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");

    assertThatThrownBy(
            () ->
                applicationQueryService.checkNotAlreadyRecorded(
                    applicationId, ApplicationState.COMPLETED))
        .isInstanceOf(ApplicationConflictException.class)
        .hasMessageContaining(applicationId.toString());
  }
}
