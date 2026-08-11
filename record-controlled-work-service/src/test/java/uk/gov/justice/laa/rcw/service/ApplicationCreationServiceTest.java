package uk.gov.justice.laa.rcw.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import uk.gov.justice.laa.ia.datastore.client.model.StartApplicationCommand;
import uk.gov.justice.laa.ia.datastore.client.model.UpdateScopingDataCommand;
import uk.gov.justice.laa.rcw.exception.ApplicationBadRequestException;
import uk.gov.justice.laa.rcw.exception.ApplicationConflictException;
import uk.gov.justice.laa.rcw.exception.ApplicationForbiddenException;
import uk.gov.justice.laa.rcw.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.rcw.exception.ApplicationUnavailableException;
import uk.gov.justice.laa.rcw.exception.ApplicationUpstreamErrorException;
import uk.gov.justice.laa.rcw.generator.ApplicationGenerator;
import uk.gov.justice.laa.rcw.generator.CreateApplicationRequestGenerator;
import uk.gov.justice.laa.rcw.mapper.ApplicationMapper;
import uk.gov.justice.laa.rcw.mapper.ApplicationMapperImpl;
import uk.gov.justice.laa.rcw.model.Application;
import uk.gov.justice.laa.rcw.model.CreateApplicationRequestBody;

@ExtendWith(MockitoExtension.class)
class ApplicationCreationServiceTest {

  private static final String ORIGINAL_TOKEN = "original-incoming-token";
  private static final String AUTHORIZED_OFFICE_CODE = "22439e72-68d3-4770-b435-c352d883d21e";

  @Mock private ApplicationApi mockApplicationApi;
  @Mock private ApplicationMapper mockApplicationMapper;
  @Mock private AuthorizedOfficesProvider mockAuthorizedOfficesProvider;

  private final BearerTokenProvider bearerTokenProvider = new BearerTokenProvider();
  private ApplicationCreationService applicationCreationService;

  @BeforeEach
  void setUp() {
    applicationCreationService =
        new ApplicationCreationService(
            mockApplicationApi,
            mockApplicationMapper,
            bearerTokenProvider,
            mockAuthorizedOfficesProvider);
    Jwt jwt =
        Jwt.withTokenValue(ORIGINAL_TOKEN).header("alg", "none").claim("sub", "test-user").build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    lenient()
        .when(mockAuthorizedOfficesProvider.currentAuthorizedOfficeCodes())
        .thenReturn(List.of(AUTHORIZED_OFFICE_CODE));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldCreateApplication_forwardsRequestToDatastore() {
    CreateApplicationRequestBody request =
        CreateApplicationRequestGenerator.createWithName(
            builder ->
                builder
                    .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                    .scopingQuestions(Map.of("priorLegalAid", "same_matter")));
    StartApplicationCommand command =
        StartApplicationCommand.builder()
            .providerOfficeCode(request.getProviderOfficeCode())
            .applicationType(StartApplicationCommand.ApplicationTypeEnum.RCW)
            .client(new ApplicationMapperImpl().toCreateClientCommand(request.getClientDetails()))
            .build();
    ApplicationResponse datastoreResponse =
        ApplicationResponse.builder()
            .id(UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901"))
            .individualLegalAidNumber(UUID.fromString("ebd50ba0-9ed9-4003-83a8-c11ac07d9e32"))
            .providerFirmCode("123456")
            .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
            .applicationType("RCW")
            .eTag(0L)
            .build();
    Application expectedApplication =
        ApplicationGenerator.create(b -> b.id(datastoreResponse.getId()));

    when(mockApplicationMapper.toStartApplicationCommand(request)).thenReturn(command);
    when(mockApplicationApi.startApplication(anyString(), any())).thenReturn(datastoreResponse);
    when(mockApplicationMapper.toApplication(datastoreResponse)).thenReturn(expectedApplication);

    Application result = applicationCreationService.createApplication(request);

    ArgumentCaptor<String> authorizationHeaderCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<StartApplicationCommand> commandCaptor =
        ArgumentCaptor.forClass(StartApplicationCommand.class);
    ArgumentCaptor<UpdateScopingDataCommand> scopingCommandCaptor =
        ArgumentCaptor.forClass(UpdateScopingDataCommand.class);
    verify(mockApplicationApi)
        .startApplication(authorizationHeaderCaptor.capture(), commandCaptor.capture());
    verify(mockApplicationApi)
        .updateScopingData(
            any(), authorizationHeaderCaptor.capture(), scopingCommandCaptor.capture());
    assertThat(authorizationHeaderCaptor.getValue()).isEqualTo("Bearer " + ORIGINAL_TOKEN);
    assertThat(commandCaptor.getValue()).isEqualTo(command);
    assertThat(scopingCommandCaptor.getValue().geteTag()).isEqualTo(0L);
    assertThat(scopingCommandCaptor.getValue().getScopingQuestions())
        .isEqualTo(Map.of("priorLegalAid", "same_matter"));
    assertThat(result).isEqualTo(expectedApplication);
  }

  @Test
  void shouldCreateApplication_throwsApplicationForbiddenException_whenOfficeCodeNotAuthorized() {
    CreateApplicationRequestBody request =
        CreateApplicationRequestGenerator.createWithName(
            builder -> builder.providerOfficeCode("OTHER-OFFICE"));

    assertThatThrownBy(() -> applicationCreationService.createApplication(request))
        .isInstanceOf(ApplicationForbiddenException.class)
        .hasMessageContaining("OTHER-OFFICE");

    verify(mockApplicationApi, never()).startApplication(anyString(), any());
    verify(mockApplicationApi, never()).updateScopingData(any(), anyString(), any());
  }

  @Test
  void shouldCreateApplication_throwsApplicationForbiddenException_whenNoOfficesAreAuthorized() {
    CreateApplicationRequestBody request =
        CreateApplicationRequestGenerator.createWithName(
            builder -> builder.providerOfficeCode(AUTHORIZED_OFFICE_CODE));
    when(mockAuthorizedOfficesProvider.currentAuthorizedOfficeCodes()).thenReturn(List.of());

    assertThatThrownBy(() -> applicationCreationService.createApplication(request))
        .isInstanceOf(ApplicationForbiddenException.class)
        .hasMessageContaining(AUTHORIZED_OFFICE_CODE);

    verify(mockApplicationApi, never()).startApplication(anyString(), any());
    verify(mockApplicationApi, never()).updateScopingData(any(), anyString(), any());
  }

  @Test
  void shouldCreateApplication_retryScopingUpdateOnceWithFreshETag_whenDatastoreReturnsConflict() {
    CreateApplicationRequestBody request =
        CreateApplicationRequestGenerator.createWithName(
            builder ->
                builder
                    .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
                    .scopingQuestions(Map.of("priorLegalAid", "same_matter")));

    StartApplicationCommand startCommand =
        StartApplicationCommand.builder()
            .providerOfficeCode(request.getProviderOfficeCode())
            .applicationType(StartApplicationCommand.ApplicationTypeEnum.RCW)
            .client(new ApplicationMapperImpl().toCreateClientCommand(request.getClientDetails()))
            .build();

    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

    ApplicationResponse datastoreResponse =
        ApplicationResponse.builder()
            .id(applicationId)
            .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
            .eTag(0L)
            .build();

    ApplicationResponse refreshedApplication =
        ApplicationResponse.builder()
            .id(applicationId)
            .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
            .eTag(1L)
            .build();

    Application expectedApplication =
        ApplicationGenerator.create(builder -> builder.id(applicationId));

    when(mockApplicationMapper.toStartApplicationCommand(request)).thenReturn(startCommand);
    when(mockApplicationApi.startApplication(anyString(), any())).thenReturn(datastoreResponse);
    when(mockApplicationApi.getApplication(eq(applicationId), anyString()))
        .thenReturn(refreshedApplication);
    doThrow(conflict())
        .doNothing()
        .when(mockApplicationApi)
        .updateScopingData(eq(applicationId), anyString(), any());
    when(mockApplicationMapper.toApplication(datastoreResponse)).thenReturn(expectedApplication);

    Application result = applicationCreationService.createApplication(request);

    ArgumentCaptor<UpdateScopingDataCommand> scopingCommandCaptor =
        ArgumentCaptor.forClass(UpdateScopingDataCommand.class);
    verify(mockApplicationApi, times(2))
        .updateScopingData(eq(applicationId), anyString(), scopingCommandCaptor.capture());
    assertThat(scopingCommandCaptor.getAllValues())
        .extracting(UpdateScopingDataCommand::geteTag)
        .containsExactly(0L, 1L);
    assertThat(scopingCommandCaptor.getAllValues())
        .extracting(UpdateScopingDataCommand::getScopingQuestions)
        .containsExactly(
            Map.of("priorLegalAid", "same_matter"), Map.of("priorLegalAid", "same_matter"));
    assertThat(result).isEqualTo(expectedApplication);
  }

  @Test
  void shouldCreateApplication_throwApplicationConflictException_whenScopingConflictPersists() {
    CreateApplicationRequestBody request =
        CreateApplicationRequestGenerator.createWithName(
            builder -> builder.providerOfficeCode(AUTHORIZED_OFFICE_CODE));

    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

    ApplicationResponse datastoreResponse =
        ApplicationResponse.builder()
            .id(applicationId)
            .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
            .eTag(0L)
            .build();

    when(mockApplicationMapper.toStartApplicationCommand(request))
        .thenReturn(
            StartApplicationCommand.builder().providerOfficeCode(AUTHORIZED_OFFICE_CODE).build());
    when(mockApplicationApi.startApplication(anyString(), any())).thenReturn(datastoreResponse);
    when(mockApplicationApi.getApplication(eq(applicationId), anyString()))
        .thenReturn(datastoreResponse);
    doThrow(conflict())
        .when(mockApplicationApi)
        .updateScopingData(eq(applicationId), anyString(), any());

    assertThatThrownBy(() -> applicationCreationService.createApplication(request))
        .isInstanceOf(ApplicationConflictException.class)
        .hasMessageContaining(applicationId.toString());

    verify(mockApplicationApi, times(2)).updateScopingData(eq(applicationId), anyString(), any());
    verify(mockApplicationApi).getApplication(eq(applicationId), anyString());
  }

  @Test
  void shouldCreateApplication_throwApplicationNotFoundException_whenScopingUpdateReturns404() {
    CreateApplicationRequestBody request =
        CreateApplicationRequestGenerator.createWithName(
            builder -> builder.providerOfficeCode(AUTHORIZED_OFFICE_CODE));

    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

    ApplicationResponse datastoreResponse =
        ApplicationResponse.builder()
            .id(applicationId)
            .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
            .eTag(0L)
            .build();

    when(mockApplicationMapper.toStartApplicationCommand(request))
        .thenReturn(
            StartApplicationCommand.builder().providerOfficeCode(AUTHORIZED_OFFICE_CODE).build());
    when(mockApplicationApi.startApplication(anyString(), any())).thenReturn(datastoreResponse);
    doThrow(notFound())
        .when(mockApplicationApi)
        .updateScopingData(eq(applicationId), anyString(), any());

    assertThatThrownBy(() -> applicationCreationService.createApplication(request))
        .isInstanceOf(ApplicationNotFoundException.class)
        .hasMessageContaining(applicationId.toString());
  }

  @Test
  void shouldCreateApplication_throwApplicationBadRequestException_whenDatastoreCreateReturns400() {
    CreateApplicationRequestBody request =
        CreateApplicationRequestGenerator.createWithName(
            builder -> builder.providerOfficeCode(AUTHORIZED_OFFICE_CODE));

    when(mockApplicationMapper.toStartApplicationCommand(request))
        .thenReturn(
            StartApplicationCommand.builder().providerOfficeCode(AUTHORIZED_OFFICE_CODE).build());
    when(mockApplicationApi.startApplication(anyString(), any())).thenThrow(badRequest());

    assertThatThrownBy(() -> applicationCreationService.createApplication(request))
        .isInstanceOf(ApplicationBadRequestException.class)
        .hasMessageContaining(AUTHORIZED_OFFICE_CODE);

    verify(mockApplicationApi, never()).updateScopingData(any(), anyString(), any());
  }

  @Test
  void shouldCreateApplication_throwApplicationBadRequestException_whenScopingUpdateReturns400() {
    CreateApplicationRequestBody request =
        CreateApplicationRequestGenerator.createWithName(
            builder -> builder.providerOfficeCode(AUTHORIZED_OFFICE_CODE));

    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

    ApplicationResponse datastoreResponse =
        ApplicationResponse.builder()
            .id(applicationId)
            .providerOfficeCode(AUTHORIZED_OFFICE_CODE)
            .eTag(0L)
            .build();

    when(mockApplicationMapper.toStartApplicationCommand(request))
        .thenReturn(
            StartApplicationCommand.builder().providerOfficeCode(AUTHORIZED_OFFICE_CODE).build());
    when(mockApplicationApi.startApplication(anyString(), any())).thenReturn(datastoreResponse);
    doThrow(badRequest())
        .when(mockApplicationApi)
        .updateScopingData(eq(applicationId), anyString(), any());

    assertThatThrownBy(() -> applicationCreationService.createApplication(request))
        .isInstanceOf(ApplicationBadRequestException.class)
        .hasMessageContaining(applicationId.toString());
  }

  @Test
  void shouldCreateApplication_throwUpstreamError_whenDatastoreCreateReturns5xx() {
    CreateApplicationRequestBody request =
        CreateApplicationRequestGenerator.createWithName(
            builder -> builder.providerOfficeCode(AUTHORIZED_OFFICE_CODE));
    StartApplicationCommand command =
        StartApplicationCommand.builder().providerOfficeCode(AUTHORIZED_OFFICE_CODE).build();

    when(mockApplicationMapper.toStartApplicationCommand(request)).thenReturn(command);
    when(mockApplicationApi.startApplication(anyString(), any())).thenThrow(serverError());

    assertThatThrownBy(() -> applicationCreationService.createApplication(request))
        .isInstanceOf(ApplicationUpstreamErrorException.class)
        .hasMessageContaining(AUTHORIZED_OFFICE_CODE);
  }

  @Test
  void shouldCreateApplication_throwUnavailable_whenDatastoreCreateIsUnavailable() {
    CreateApplicationRequestBody request =
        CreateApplicationRequestGenerator.createWithName(
            builder -> builder.providerOfficeCode(AUTHORIZED_OFFICE_CODE));
    StartApplicationCommand command =
        StartApplicationCommand.builder().providerOfficeCode(AUTHORIZED_OFFICE_CODE).build();

    when(mockApplicationMapper.toStartApplicationCommand(request)).thenReturn(command);
    when(mockApplicationApi.startApplication(anyString(), any()))
        .thenThrow(new ResourceAccessException("Connection refused"));

    assertThatThrownBy(() -> applicationCreationService.createApplication(request))
        .isInstanceOf(ApplicationUnavailableException.class)
        .hasMessageContaining(AUTHORIZED_OFFICE_CODE);
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

  private static HttpClientErrorException.BadRequest badRequest() {
    return (HttpClientErrorException.BadRequest)
        HttpClientErrorException.create(
            HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, new byte[0], null);
  }

  private static HttpServerErrorException serverError() {
    return new HttpServerErrorException(HttpStatus.BAD_GATEWAY);
  }
}
