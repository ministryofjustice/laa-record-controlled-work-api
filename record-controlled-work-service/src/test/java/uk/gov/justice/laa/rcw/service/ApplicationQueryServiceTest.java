package uk.gov.justice.laa.rcw.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponses;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationSummary;
import uk.gov.justice.laa.rcw.exception.ApplicationConflictException;
import uk.gov.justice.laa.rcw.exception.ApplicationForbiddenException;
import uk.gov.justice.laa.rcw.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.rcw.exception.ApplicationUnavailableException;
import uk.gov.justice.laa.rcw.exception.ApplicationUpstreamErrorException;
import uk.gov.justice.laa.rcw.gateway.ApplicationGateway;
import uk.gov.justice.laa.rcw.mapper.AddressMapperImpl;
import uk.gov.justice.laa.rcw.mapper.ApplicationMapper;
import uk.gov.justice.laa.rcw.mapper.ApplicationMapperImpl;
import uk.gov.justice.laa.rcw.mapper.ClientDetailsMapperImpl;
import uk.gov.justice.laa.rcw.mapper.DeclarationMapperImpl;
import uk.gov.justice.laa.rcw.mapper.EligibilityMapper;
import uk.gov.justice.laa.rcw.mapper.EligibilityMapperImpl;
import uk.gov.justice.laa.rcw.mapper.EvidenceMapperImpl;
import uk.gov.justice.laa.rcw.mapper.JsonNullableMapperImpl;
import uk.gov.justice.laa.rcw.mapper.ScopingQuestionsMapperImpl;
import uk.gov.justice.laa.rcw.model.ApplicationOverview;
import uk.gov.justice.laa.rcw.model.ApplicationState;
import uk.gov.justice.laa.rcw.model.EligibilityIndication;

@ExtendWith(MockitoExtension.class)
class ApplicationQueryServiceTest {

  @Mock private ApplicationGateway mockApplicationGateway;
  @Mock private AuthorizedOfficesProvider mockAuthorizedOfficesProvider;

  private final ApplicationMapper applicationMapper =
      new ApplicationMapperImpl(
          new ClientDetailsMapperImpl(
              new AddressMapperImpl(new JsonNullableMapperImpl()), new JsonNullableMapperImpl()),
          new DeclarationMapperImpl(),
          new EligibilityMapperImpl(new JsonNullableMapperImpl()),
          new EvidenceMapperImpl(),
          new ScopingQuestionsMapperImpl(),
          new JsonNullableMapperImpl());
  private final EligibilityMapper eligibilityMapper =
      new EligibilityMapperImpl(new JsonNullableMapperImpl());
  private ApplicationQueryService applicationQueryService;

  @BeforeEach
  void setUp() {
    applicationQueryService =
        new ApplicationQueryService(
            mockApplicationGateway,
            applicationMapper,
            eligibilityMapper,
            mockAuthorizedOfficesProvider);
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
            .eligibilityIndication(
                uk.gov.justice.laa.ia.datastore.client.model.EligibilityIndication.ELIGIBLE)
            .build();
    when(mockApplicationGateway.getApplications(any(), any(), any(), any(), any()))
        .thenReturn(ApplicationResponses.builder().content(List.of(summary)).build());

    List<ApplicationOverview> result =
        applicationQueryService.getApplications(
            1, 25, null, ApplicationState.DRAFT, EligibilityIndication.ELIGIBLE);

    assertThat(result)
        .containsExactly(
            ApplicationOverview.builder()
                .id(applicationId)
                .name("Joe Bloggs")
                .applicationRefNumber("CW-111111")
                .modifiedAt(modifiedAt)
                .eligibilityIndication(EligibilityIndication.ELIGIBLE)
                .build());
  }

  @Test
  void shouldGetApplications_returnsEmptyListWhenNoContent() {
    when(mockApplicationGateway.getApplications(any(), any(), any(), any(), any()))
        .thenReturn(ApplicationResponses.builder().content(List.of()).build());

    List<ApplicationOverview> result =
        applicationQueryService.getApplications(0, 25, null, null, null);

    assertThat(result).isEmpty();
  }

  @Test
  void shouldGetApplications_forwardsFiltersToGateway() {
    String officeId = "22439e72-68d3-4770-b435-c352d883d21e";
    when(mockApplicationGateway.getApplications(any(), any(), any(), any(), any()))
        .thenReturn(ApplicationResponses.builder().content(List.of()).build());

    applicationQueryService.getApplications(
        2, 50, officeId, ApplicationState.COMPLETED, EligibilityIndication.INELIGIBLE);

    verify(mockApplicationGateway)
        .getApplications(
            2,
            50,
            officeId,
            uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.COMPLETED,
            uk.gov.justice.laa.ia.datastore.client.model.EligibilityIndication.INELIGIBLE);
  }

  @Test
  void shouldGetApplications_propagatesGatewayFailure() {
    ApplicationUnavailableException failure =
        new ApplicationUnavailableException("Datastore is unavailable");
    when(mockApplicationGateway.getApplications(any(), any(), any(), any(), any()))
        .thenThrow(failure);

    assertThatThrownBy(() -> applicationQueryService.getApplications(0, 25, null, null, null))
        .isSameAs(failure);
  }

  @Test
  void shouldRejectMissingDatastoreVersion() {
    UUID id = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockAuthorizedOfficesProvider.currentAuthorizedOfficeCodes())
        .thenReturn(List.of("OFFICE"));
    when(mockApplicationGateway.fetchApplicationDetails(id))
        .thenReturn(ApplicationResponse.builder().id(id).providerOfficeCode("OFFICE").build());

    assertThatThrownBy(() -> applicationQueryService.getApplication(id))
        .isInstanceOf(ApplicationUpstreamErrorException.class)
        .hasMessage("Datastore returned an invalid application version");
    verify(mockApplicationGateway).fetchApplicationDetails(id);
  }

  @Test
  void shouldRejectNegativeDatastoreVersion() {
    UUID id = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockAuthorizedOfficesProvider.currentAuthorizedOfficeCodes())
        .thenReturn(List.of("OFFICE"));
    when(mockApplicationGateway.fetchApplicationDetails(id))
        .thenReturn(
            ApplicationResponse.builder().id(id).providerOfficeCode("OFFICE").eTag(-1L).build());

    assertThatThrownBy(() -> applicationQueryService.getApplication(id))
        .isInstanceOf(ApplicationUpstreamErrorException.class);
    verify(mockApplicationGateway).fetchApplicationDetails(id);
  }

  @ParameterizedTest
  @ValueSource(longs = {0L, 7L, Long.MAX_VALUE})
  void shouldGetApplicationById(long version) {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockAuthorizedOfficesProvider.currentAuthorizedOfficeCodes())
        .thenReturn(List.of("22439e72-68d3-4770-b435-c352d883d21e"));
    when(mockApplicationGateway.fetchApplicationDetails(applicationId))
        .thenReturn(
            ApplicationResponse.builder()
                .id(applicationId)
                .providerFirmCode("123456")
                .providerOfficeCode("22439e72-68d3-4770-b435-c352d883d21e")
                .applicationType("CONTROLLED_WORK")
                .createdBy("Random User")
                .modifiedBy("Random User")
                .eTag(version)
                .build());

    var result = applicationQueryService.getApplication(applicationId);

    assertThat(result).isPresent();
    assertThat(result.get().application().getId()).isEqualTo(applicationId);
    assertThat(result.get().application().getProviderFirmCode()).isEqualTo("123456");
    assertThat(result.get().application().getApplicationType()).isEqualTo("CONTROLLED_WORK");
    assertThat(result.get().version()).isEqualTo(version);
    verify(mockApplicationGateway).fetchApplicationDetails(applicationId);
  }

  @Test
  void shouldGetApplicationById_returnsEmptyWhenOfficeIsNotAuthorized() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockAuthorizedOfficesProvider.currentAuthorizedOfficeCodes())
        .thenReturn(List.of("OTHER-OFFICE"));
    when(mockApplicationGateway.fetchApplicationDetails(applicationId))
        .thenReturn(
            ApplicationResponse.builder()
                .id(applicationId)
                .providerOfficeCode("22439e72-68d3-4770-b435-c352d883d21e")
                .build());

    var result = applicationQueryService.getApplication(applicationId);

    assertThat(result).isEmpty();
  }

  @Test
  void shouldGetApplicationById_returnsEmptyWhenNoOfficeIsAuthorized() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockAuthorizedOfficesProvider.currentAuthorizedOfficeCodes()).thenReturn(List.of());
    when(mockApplicationGateway.fetchApplicationDetails(applicationId))
        .thenReturn(
            ApplicationResponse.builder()
                .id(applicationId)
                .providerOfficeCode("22439e72-68d3-4770-b435-c352d883d21e")
                .build());

    var result = applicationQueryService.getApplication(applicationId);

    assertThat(result).isEmpty();
  }

  @Test
  void shouldGetApplicationById_returnsEmptyWhenGatewayReportsNotFound() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    when(mockApplicationGateway.fetchApplicationDetails(applicationId))
        .thenThrow(new ApplicationNotFoundException("No application found"));

    var result = applicationQueryService.getApplication(applicationId);

    assertThat(result).isEmpty();
  }

  @Test
  void shouldGetApplicationById_propagatesGatewayFailure() {
    UUID applicationId = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    ApplicationUnavailableException failure =
        new ApplicationUnavailableException("Datastore is unavailable");
    when(mockApplicationGateway.fetchApplicationDetails(applicationId)).thenThrow(failure);

    assertThatThrownBy(() -> applicationQueryService.getApplication(applicationId))
        .isSameAs(failure);
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

    applicationQueryService.checkNotAlreadyRecorded(
        applicationId, uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT);
  }

  @Test
  void shouldCheckNotAlreadyRecorded_throwsConflictException_whenStateIsCompleted() {
    UUID applicationId = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");

    assertThatThrownBy(
            () ->
                applicationQueryService.checkNotAlreadyRecorded(
                    applicationId,
                    uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.COMPLETED))
        .isInstanceOf(ApplicationConflictException.class)
        .hasMessageContaining(applicationId.toString());
  }
}
