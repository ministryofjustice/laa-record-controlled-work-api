package uk.gov.justice.laa.rcw.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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

import uk.gov.justice.laa.ia.datastore.client.api.ApplicationApi;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponses;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationState;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationSummary;
import uk.gov.justice.laa.ia.datastore.client.model.UpdateMeansDataCommand;
import uk.gov.justice.laa.rcw.exception.ApplicationConflictException;
import uk.gov.justice.laa.rcw.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.rcw.generator.ApplicationGenerator;
import uk.gov.justice.laa.rcw.mapper.ApplicationMapper;
import uk.gov.justice.laa.rcw.mapper.ApplicationMapperImpl;
import uk.gov.justice.laa.rcw.model.Application;
import uk.gov.justice.laa.rcw.model.ApplicationOverview;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

  private static final String ORIGINAL_TOKEN = "original-incoming-token";

  @Mock private ApplicationApi mockApplicationApi;

  private final ApplicationMapper applicationMapper = new ApplicationMapperImpl();
  private ApplicationService applicationService;

  @BeforeEach
  void setUp() {
    applicationService = new ApplicationService(mockApplicationApi, applicationMapper);
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
        applicationService.getApplications(
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

    List<ApplicationOverview> result = applicationService.getApplications(0, 25, null, null);

    assertThat(result).isEmpty();
  }

  @Test
  void shouldGetApplications_forwardsPageSizeAndOfficeIdToDatastore() {
    String officeId = "22439e72-68d3-4770-b435-c352d883d21e";
    when(mockApplicationApi.getApplications(anyString(), any(), any(), any(), any()))
        .thenReturn(ApplicationResponses.builder().content(List.of()).build());

    applicationService.getApplications(
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
    Optional<Application> expected =
        Optional.of(ApplicationGenerator.create(b -> b.id(applicationId)));

    Optional<Application> result = applicationService.getApplication(applicationId);

    assertThat(result).isPresent();

    assertThat(result)
        .usingRecursiveComparison()
        .ignoringFields(
            "value.individualLegalAidNumber",
            "value.providerFirmCode",
            "value.ecfFlag",
            "value.applicationType",
            "value.providerOfficeCode",
            "value.createdAt",
            "value.modifiedAt",
            "value.clientDetails.id",
            "value.clientDetails.createdAt",
            "value.clientDetails.modifiedAt",
            "value.clientDetails.address.id",
            "value.clientDetails.address.createdAt",
            "value.clientDetails.address.modifiedAt",
            "value.declaration.id",
            "value.declaration.createdAt",
            "value.declaration.modifiedAt",
            "value.evidence.id",
            "value.evidence.createdAt",
            "value.evidence.modifiedAt")
        .isEqualTo(expected);
  }

  @Test
  void shouldUpdateMeans_fetchesETagAndForwardsDataAndResultToDatastore() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    Map<String, Object> data = Map.of("level_of_help", "controlled");
    Map<String, Object> result = Map.of("indication", true);
    when(mockApplicationApi.getApplication(eq(applicationId), anyString()))
        .thenReturn(ApplicationResponse.builder().eTag(7L).build());

    applicationService.updateMeans(applicationId, data, result);

    ArgumentCaptor<String> getXAuthorizationCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockApplicationApi).getApplication(eq(applicationId), getXAuthorizationCaptor.capture());
    assertThat(getXAuthorizationCaptor.getValue()).isEqualTo("Bearer " + ORIGINAL_TOKEN);

    ArgumentCaptor<String> updateXAuthorizationCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<UpdateMeansDataCommand> commandCaptor =
        ArgumentCaptor.forClass(UpdateMeansDataCommand.class);
    verify(mockApplicationApi)
        .updateMeansData(
            eq(applicationId), updateXAuthorizationCaptor.capture(), commandCaptor.capture());
    assertThat(updateXAuthorizationCaptor.getValue()).isEqualTo("Bearer " + ORIGINAL_TOKEN);
    assertThat(commandCaptor.getValue())
        .isEqualTo(UpdateMeansDataCommand.builder().eTag(7L).data(data).result(result).build());
  }

  @Test
  void shouldUpdateMeans_throwsApplicationNotFoundException_whenApplicationDoesNotExist() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationApi.getApplication(eq(applicationId), anyString())).thenThrow(notFound());

    assertThatThrownBy(() -> applicationService.updateMeans(applicationId, Map.of(), Map.of()))
        .isInstanceOf(ApplicationNotFoundException.class)
        .hasMessageContaining(applicationId.toString());

    verify(mockApplicationApi, never()).updateMeansData(any(), anyString(), any());
  }

  @Test
  void shouldUpdateMeans_throwsApplicationNotFoundException_whenDatastoreUpdateReturns404() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationApi.getApplication(eq(applicationId), anyString()))
        .thenReturn(ApplicationResponse.builder().eTag(1L).build());
    doThrow(notFound())
        .when(mockApplicationApi)
        .updateMeansData(eq(applicationId), anyString(), any());

    assertThatThrownBy(() -> applicationService.updateMeans(applicationId, Map.of(), Map.of()))
        .isInstanceOf(ApplicationNotFoundException.class)
        .hasMessageContaining(applicationId.toString());
  }

  @Test
  void shouldUpdateMeans_retriesOnceWithFreshETag_whenDatastoreReturnsConflict() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    Map<String, Object> data = Map.of("level_of_help", "controlled");
    Map<String, Object> result = Map.of("indication", true);
    when(mockApplicationApi.getApplication(eq(applicationId), anyString()))
        .thenReturn(ApplicationResponse.builder().eTag(1L).build())
        .thenReturn(ApplicationResponse.builder().eTag(2L).build());
    doThrow(conflict())
        .doNothing()
        .when(mockApplicationApi)
        .updateMeansData(eq(applicationId), anyString(), any());

    applicationService.updateMeans(applicationId, data, result);

    verify(mockApplicationApi, times(2)).getApplication(eq(applicationId), anyString());
    ArgumentCaptor<UpdateMeansDataCommand> commandCaptor =
        ArgumentCaptor.forClass(UpdateMeansDataCommand.class);
    verify(mockApplicationApi, times(2))
        .updateMeansData(eq(applicationId), anyString(), commandCaptor.capture());
    assertThat(commandCaptor.getAllValues())
        .extracting(UpdateMeansDataCommand::geteTag)
        .containsExactly(1L, 2L);
  }

  @Test
  void shouldUpdateMeans_throwsApplicationConflictException_whenConflictPersistsAfterRetry() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationApi.getApplication(eq(applicationId), anyString()))
        .thenReturn(ApplicationResponse.builder().eTag(1L).build());
    doThrow(conflict())
        .when(mockApplicationApi)
        .updateMeansData(eq(applicationId), anyString(), any());

    assertThatThrownBy(() -> applicationService.updateMeans(applicationId, Map.of(), Map.of()))
        .isInstanceOf(ApplicationConflictException.class)
        .hasMessageContaining(applicationId.toString());

    verify(mockApplicationApi, times(2)).getApplication(eq(applicationId), anyString());
    verify(mockApplicationApi, times(2)).updateMeansData(eq(applicationId), anyString(), any());
  }

  private static HttpClientErrorException.NotFound notFound() {
    return (HttpClientErrorException.NotFound)
        HttpClientErrorException.create(
            HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null);
  }

  private static HttpClientErrorException.Conflict conflict() {
    return (HttpClientErrorException.Conflict)
        HttpClientErrorException.create(
            HttpStatus.CONFLICT, "Conflict", HttpHeaders.EMPTY, new byte[0], null);
  }
}
