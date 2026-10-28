package uk.gov.justice.laa.rcw.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.EditApplicationCommand;
import uk.gov.justice.laa.ia.datastore.client.model.StartApplicationCommand;
import uk.gov.justice.laa.rcw.exception.ApplicationBadRequestException;
import uk.gov.justice.laa.rcw.exception.ApplicationConflictException;
import uk.gov.justice.laa.rcw.exception.ApplicationForbiddenException;
import uk.gov.justice.laa.rcw.gateway.ApplicationGateway;
import uk.gov.justice.laa.rcw.generator.ApplicationGenerator;
import uk.gov.justice.laa.rcw.generator.CreateApplicationRequestGenerator;
import uk.gov.justice.laa.rcw.mapper.ApplicationMapper;
import uk.gov.justice.laa.rcw.model.Application;
import uk.gov.justice.laa.rcw.model.CreateApplicationRequestBody;

@ExtendWith(MockitoExtension.class)
class ApplicationCreationServiceTest {

  private static final String AUTHORIZED_OFFICE_CODE = "22439e72-68d3-4770-b435-c352d883d21e";

  @Mock private ApplicationGateway mockApplicationGateway;
  @Mock private ApplicationMapper mockApplicationMapper;
  @Mock private AuthorizedOfficesProvider mockAuthorizedOfficesProvider;

  private ApplicationCreationService applicationCreationService;

  @BeforeEach
  void setUp() {
    applicationCreationService =
        new ApplicationCreationService(
            mockApplicationGateway, mockApplicationMapper, mockAuthorizedOfficesProvider);
    lenient()
        .when(mockAuthorizedOfficesProvider.currentAuthorizedOfficeCodes())
        .thenReturn(List.of(AUTHORIZED_OFFICE_CODE));
  }

  @Test
  void shouldCreateApplication_forwardsRequestToGateway() {
    CreateApplicationRequestBody request =
        CreateApplicationRequestGenerator.createWithName(
            builder -> builder.providerOfficeCode(AUTHORIZED_OFFICE_CODE));
    StartApplicationCommand startCommand =
        StartApplicationCommand.builder().providerOfficeCode(AUTHORIZED_OFFICE_CODE).build();
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    ApplicationResponse datastoreResponse =
        ApplicationResponse.builder().id(applicationId).eTag(5L).build();
    Application expectedApplication = ApplicationGenerator.create(b -> b.id(applicationId));

    when(mockApplicationMapper.toStartApplicationCommand(request)).thenReturn(startCommand);
    when(mockApplicationGateway.startApplication(AUTHORIZED_OFFICE_CODE, startCommand))
        .thenReturn(datastoreResponse);
    when(mockApplicationMapper.toApplication(datastoreResponse)).thenReturn(expectedApplication);
    EditApplicationCommand editCommand = EditApplicationCommand.builder().eTag(5L).build();
    when(mockApplicationMapper.toEditApplicationCommandFromCreate(request, 5L))
        .thenReturn(editCommand);

    Application result = applicationCreationService.createApplication(request);

    verify(mockApplicationGateway).startApplication(AUTHORIZED_OFFICE_CODE, startCommand);
    verify(mockApplicationMapper).toEditApplicationCommandFromCreate(request, 5L);
    verify(mockApplicationGateway).editApplication(eq(applicationId), same(editCommand));
    verify(mockApplicationGateway, never()).updateScopingData(any(), any());
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

    verify(mockApplicationGateway, never()).startApplication(anyString(), any());
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

    verify(mockApplicationGateway, never()).startApplication(anyString(), any());
  }

  @Test
  void shouldCreateApplication_propagatesException_whenStartApplicationFails() {
    CreateApplicationRequestBody request =
        CreateApplicationRequestGenerator.createWithName(
            builder -> builder.providerOfficeCode(AUTHORIZED_OFFICE_CODE));
    StartApplicationCommand startCommand =
        StartApplicationCommand.builder().providerOfficeCode(AUTHORIZED_OFFICE_CODE).build();

    when(mockApplicationMapper.toStartApplicationCommand(request)).thenReturn(startCommand);
    when(mockApplicationGateway.startApplication(AUTHORIZED_OFFICE_CODE, startCommand))
        .thenThrow(new ApplicationBadRequestException("Datastore rejected the request"));

    assertThatThrownBy(() -> applicationCreationService.createApplication(request))
        .isInstanceOf(ApplicationBadRequestException.class);

    verify(mockApplicationGateway, never()).updateScopingData(any(), any());
  }

  @Test
  void shouldCreateApplication_propagatesException_whenScopingUpdateFails_whenEditFails() {
    CreateApplicationRequestBody request =
        CreateApplicationRequestGenerator.createWithName(
            builder -> builder.providerOfficeCode(AUTHORIZED_OFFICE_CODE));
    StartApplicationCommand startCommand =
        StartApplicationCommand.builder().providerOfficeCode(AUTHORIZED_OFFICE_CODE).build();
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    ApplicationResponse datastoreResponse =
        ApplicationResponse.builder().id(applicationId).eTag(5L).build();

    when(mockApplicationMapper.toStartApplicationCommand(request)).thenReturn(startCommand);
    when(mockApplicationGateway.startApplication(AUTHORIZED_OFFICE_CODE, startCommand))
        .thenReturn(datastoreResponse);
    EditApplicationCommand editCommand = EditApplicationCommand.builder().eTag(5L).build();
    when(mockApplicationMapper.toEditApplicationCommandFromCreate(request, 5L))
        .thenReturn(editCommand);
    ApplicationConflictException failure =
        new ApplicationConflictException("Application was modified concurrently");
    when(mockApplicationGateway.editApplication(applicationId, editCommand)).thenThrow(failure);

    assertThatThrownBy(() -> applicationCreationService.createApplication(request))
        .isSameAs(failure);
  }
}
