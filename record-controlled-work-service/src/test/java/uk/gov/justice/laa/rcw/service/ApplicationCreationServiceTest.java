package uk.gov.justice.laa.rcw.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import uk.gov.justice.laa.ia.datastore.client.api.ApplicationApi;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.StartApplicationCommand;
import uk.gov.justice.laa.rcw.generator.ApplicationGenerator;
import uk.gov.justice.laa.rcw.generator.CreateApplicationRequestGenerator;
import uk.gov.justice.laa.rcw.mapper.ApplicationMapper;
import uk.gov.justice.laa.rcw.mapper.ApplicationMapperImpl;
import uk.gov.justice.laa.rcw.model.Application;
import uk.gov.justice.laa.rcw.model.CreateApplicationRequestBody;

@ExtendWith(MockitoExtension.class)
class ApplicationCreationServiceTest {

  private static final String ORIGINAL_TOKEN = "original-incoming-token";

  @Mock private ApplicationApi mockApplicationApi;
  @Mock private ApplicationMapper mockApplicationMapper;

  private final BearerTokenProvider bearerTokenProvider = new BearerTokenProvider();
  private ApplicationCreationService applicationCreationService;

  @BeforeEach
  void setUp() {
    applicationCreationService =
        new ApplicationCreationService(
            mockApplicationApi, mockApplicationMapper, bearerTokenProvider);
    Jwt jwt =
        Jwt.withTokenValue(ORIGINAL_TOKEN).header("alg", "none").claim("sub", "test-user").build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldCreateApplication_forwardsRequestToDatastore() {
    CreateApplicationRequestBody request = CreateApplicationRequestGenerator.createWithName(null);
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
            .providerOfficeCode("22439e72-68d3-4770-b435-c352d883d21e")
            .applicationType("RCW")
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
    verify(mockApplicationApi)
        .startApplication(authorizationHeaderCaptor.capture(), commandCaptor.capture());
    assertThat(authorizationHeaderCaptor.getValue()).isEqualTo("Bearer " + ORIGINAL_TOKEN);
    assertThat(commandCaptor.getValue()).isEqualTo(command);
    assertThat(result).isEqualTo(expectedApplication);
  }
}
