package uk.gov.justice.laa.rcw.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationState;
import uk.gov.justice.laa.ia.datastore.client.model.EditApplicationCommand;
import uk.gov.justice.laa.rcw.exception.ApplicationConflictException;
import uk.gov.justice.laa.rcw.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.rcw.exception.ApplicationUnavailableException;
import uk.gov.justice.laa.rcw.gateway.ApplicationGateway;
import uk.gov.justice.laa.rcw.mapper.ApplicationMapper;
import uk.gov.justice.laa.rcw.model.UpdateApplicationDetailsRequestBody;

@ExtendWith(MockitoExtension.class)
class ApplicationDetailsServiceTest {

  private static final UUID APPLICATION_ID =
      UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
  private static final String OFFICE_CODE = "AB12CD";
  private static final long CALLER_VERSION = 31L;

  @Mock private ApplicationMapper mockApplicationMapper;
  @Mock private ApplicationGateway mockApplicationGateway;
  @Mock private ApplicationGuard mockApplicationGuard;

  private ApplicationDetailsService applicationDetailsService;

  @BeforeEach
  void setUp() {
    applicationDetailsService =
        new ApplicationDetailsService(
            mockApplicationMapper, mockApplicationGateway, mockApplicationGuard);
  }

  @Test
  void shouldEditOnceWithCallerVersionAndReturnDatastoreEtag() {
    UpdateApplicationDetailsRequestBody request = new UpdateApplicationDetailsRequestBody();
    EditApplicationCommand command = EditApplicationCommand.builder().eTag(CALLER_VERSION).build();
    when(mockApplicationGateway.fetchApplicationDetails(APPLICATION_ID))
        .thenReturn(application(ApplicationState.DRAFT));
    when(mockApplicationMapper.toEditApplicationCommand(request, CALLER_VERSION))
        .thenReturn(command);
    when(mockApplicationGateway.editApplication(APPLICATION_ID, command)).thenReturn("\"32\"");

    String etag =
        applicationDetailsService.updateApplicationDetails(APPLICATION_ID, request, CALLER_VERSION);

    assertThat(etag).isEqualTo("\"32\"");
    verify(mockApplicationGateway, times(1)).fetchApplicationDetails(APPLICATION_ID);
    verify(mockApplicationGuard).checkVisibleForOffice(APPLICATION_ID, OFFICE_CODE);
    verify(mockApplicationMapper).toEditApplicationCommand(request, CALLER_VERSION);
    verify(mockApplicationGateway, times(1)).editApplication(APPLICATION_ID, command);
  }

  @Test
  void shouldPreserveLeadingZerosInDatastoreEtag() {
    UpdateApplicationDetailsRequestBody request = new UpdateApplicationDetailsRequestBody();
    EditApplicationCommand command = EditApplicationCommand.builder().eTag(CALLER_VERSION).build();
    when(mockApplicationGateway.fetchApplicationDetails(APPLICATION_ID))
        .thenReturn(application(ApplicationState.DRAFT));
    when(mockApplicationMapper.toEditApplicationCommand(request, CALLER_VERSION))
        .thenReturn(command);
    when(mockApplicationGateway.editApplication(APPLICATION_ID, command)).thenReturn("\"00032\"");

    String etag =
        applicationDetailsService.updateApplicationDetails(APPLICATION_ID, request, CALLER_VERSION);

    assertThat(etag).isEqualTo("\"00032\"");
  }

  @Test
  void shouldReturnNotFoundWithoutEditingWhenOfficeIsUnauthorized() {
    UpdateApplicationDetailsRequestBody request = new UpdateApplicationDetailsRequestBody();
    when(mockApplicationGateway.fetchApplicationDetails(APPLICATION_ID))
        .thenReturn(application(ApplicationState.DRAFT));
    doThrow(notFound())
        .when(mockApplicationGuard)
        .checkVisibleForOffice(APPLICATION_ID, OFFICE_CODE);

    assertThatThrownBy(
            () ->
                applicationDetailsService.updateApplicationDetails(
                    APPLICATION_ID, request, CALLER_VERSION))
        .isInstanceOf(ApplicationNotFoundException.class)
        .hasMessage("No application found with id: " + APPLICATION_ID)
        .satisfies(
            exception ->
                assertThat(((ApplicationNotFoundException) exception).getReason())
                    .isEqualTo("APPLICATION_NOT_FOUND"));

    verify(mockApplicationGateway, times(1)).fetchApplicationDetails(APPLICATION_ID);
    verify(mockApplicationGateway, never())
        .editApplication(eq(APPLICATION_ID), any(EditApplicationCommand.class));
    verifyNoInteractions(mockApplicationMapper);
  }

  @Test
  void shouldRejectCompletedApplicationAfterOfficeAuthorization() {
    UpdateApplicationDetailsRequestBody request = new UpdateApplicationDetailsRequestBody();
    when(mockApplicationGateway.fetchApplicationDetails(APPLICATION_ID))
        .thenReturn(application(ApplicationState.COMPLETED));

    assertThatThrownBy(
            () ->
                applicationDetailsService.updateApplicationDetails(
                    APPLICATION_ID, request, CALLER_VERSION))
        .isInstanceOf(ApplicationConflictException.class)
        .satisfies(
            exception ->
                assertThat(((ApplicationConflictException) exception).getReason())
                    .isEqualTo("APPLICATION_COMPLETED"));

    verify(mockApplicationGuard).checkVisibleForOffice(APPLICATION_ID, OFFICE_CODE);
    verify(mockApplicationGateway, never())
        .editApplication(eq(APPLICATION_ID), any(EditApplicationCommand.class));
    verifyNoInteractions(mockApplicationMapper);
  }

  @Test
  void shouldHideCompletedApplicationWhenOfficeIsUnauthorized() {
    UpdateApplicationDetailsRequestBody request = new UpdateApplicationDetailsRequestBody();
    when(mockApplicationGateway.fetchApplicationDetails(APPLICATION_ID))
        .thenReturn(application(ApplicationState.COMPLETED));
    doThrow(notFound())
        .when(mockApplicationGuard)
        .checkVisibleForOffice(APPLICATION_ID, OFFICE_CODE);

    assertThatThrownBy(
            () ->
                applicationDetailsService.updateApplicationDetails(
                    APPLICATION_ID, request, CALLER_VERSION))
        .isExactlyInstanceOf(ApplicationNotFoundException.class)
        .satisfies(
            exception ->
                assertThat(((ApplicationNotFoundException) exception).getReason())
                    .isEqualTo("APPLICATION_NOT_FOUND"));

    verify(mockApplicationGateway, never())
        .editApplication(eq(APPLICATION_ID), any(EditApplicationCommand.class));
    verifyNoInteractions(mockApplicationMapper);
  }

  @Test
  void shouldNotRetryWhenDatastoreEditFails() {
    UpdateApplicationDetailsRequestBody request = new UpdateApplicationDetailsRequestBody();
    EditApplicationCommand command = EditApplicationCommand.builder().eTag(CALLER_VERSION).build();
    ApplicationUnavailableException failure =
        new ApplicationUnavailableException("Datastore is unavailable");
    when(mockApplicationGateway.fetchApplicationDetails(APPLICATION_ID))
        .thenReturn(application(ApplicationState.DRAFT));
    when(mockApplicationMapper.toEditApplicationCommand(request, CALLER_VERSION))
        .thenReturn(command);
    when(mockApplicationGateway.editApplication(APPLICATION_ID, command)).thenThrow(failure);

    assertThatThrownBy(
            () ->
                applicationDetailsService.updateApplicationDetails(
                    APPLICATION_ID, request, CALLER_VERSION))
        .isSameAs(failure);

    verify(mockApplicationGateway, times(1)).fetchApplicationDetails(APPLICATION_ID);
    verify(mockApplicationGateway, times(1)).editApplication(APPLICATION_ID, command);
  }

  private static ApplicationResponse application(ApplicationState state) {
    return ApplicationResponse.builder()
        .eTag(100L)
        .providerOfficeCode(OFFICE_CODE)
        .applicationState(state)
        .build();
  }

  private static ApplicationNotFoundException notFound() {
    return new ApplicationNotFoundException("No application found with id: " + APPLICATION_ID);
  }
}
