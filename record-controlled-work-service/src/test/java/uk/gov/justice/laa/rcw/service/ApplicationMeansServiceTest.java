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

import java.util.Map;
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
import uk.gov.justice.laa.ia.datastore.client.model.UpdateMeansDataCommand;
import uk.gov.justice.laa.rcw.exception.ApplicationConflictException;
import uk.gov.justice.laa.rcw.exception.ApplicationNotFoundException;

@ExtendWith(MockitoExtension.class)
class ApplicationMeansServiceTest {

  private static final String ORIGINAL_TOKEN = "original-incoming-token";

  @Mock private ApplicationApi mockApplicationApi;

  private final BearerTokenProvider bearerTokenProvider = new BearerTokenProvider();
  private ApplicationMeansService applicationMeansService;

  @BeforeEach
  void setUp() {
    applicationMeansService = new ApplicationMeansService(mockApplicationApi, bearerTokenProvider);
    Jwt jwt =
        Jwt.withTokenValue(ORIGINAL_TOKEN).header("alg", "none").claim("sub", "test-user").build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldUpdateMeans_fetchesETagAndForwardsDataAndResultToDatastore() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    Map<String, Object> data = Map.of("level_of_help", "controlled");
    Map<String, Object> result = Map.of("indication", true);
    when(mockApplicationApi.getApplication(eq(applicationId), anyString()))
        .thenReturn(ApplicationResponse.builder().eTag(7L).build());

    applicationMeansService.updateMeans(applicationId, data, result);

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

    assertThatThrownBy(() -> applicationMeansService.updateMeans(applicationId, Map.of(), Map.of()))
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

    assertThatThrownBy(() -> applicationMeansService.updateMeans(applicationId, Map.of(), Map.of()))
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

    applicationMeansService.updateMeans(applicationId, data, result);

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

    assertThatThrownBy(() -> applicationMeansService.updateMeans(applicationId, Map.of(), Map.of()))
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
