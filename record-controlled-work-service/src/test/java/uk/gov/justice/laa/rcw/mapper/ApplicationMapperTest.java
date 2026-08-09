package uk.gov.justice.laa.rcw.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.ia.datastore.client.model.Address;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationSummary;
import uk.gov.justice.laa.ia.datastore.client.model.ClientDeclarationStatus;
import uk.gov.justice.laa.ia.datastore.client.model.ClientDetails;
import uk.gov.justice.laa.ia.datastore.client.model.DeclarationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.EligibilityResult;
import uk.gov.justice.laa.rcw.model.Application;
import uk.gov.justice.laa.rcw.model.ApplicationOverview;
import uk.gov.justice.laa.rcw.model.ApplicationState;

class ApplicationMapperTest {

  private static final UUID APPLICATION_ID =
      UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
  private static final String REFERENCE_NUMBER = "CW-111111";
  private static final OffsetDateTime MODIFIED_AT = OffsetDateTime.now();

  private final ApplicationMapper applicationMapper = new ApplicationMapperImpl();

  @Test
  void shouldMapApplicationSummaryToApplicationOverview() {
    ApplicationSummary applicationSummary =
        ApplicationSummary.builder()
            .id(APPLICATION_ID)
            .clientFirstName("Joe")
            .clientLastName("Bloggs")
            .referenceNumber(REFERENCE_NUMBER)
            .modifiedAt(MODIFIED_AT)
            .build();

    ApplicationOverview result = applicationMapper.toApplicationOverview(applicationSummary);

    assertThat(result.getId()).isEqualTo(APPLICATION_ID);
    assertThat(result.getName()).isEqualTo("Joe Bloggs");
    assertThat(result.getApplicationRefNumber()).isEqualTo(REFERENCE_NUMBER);
    assertThat(result.getModifiedAt()).isEqualTo(MODIFIED_AT);
  }

  @Test
  void shouldMapApplicationSummaryToApplicationOverview_whenClientFirstNameIsNull() {
    ApplicationSummary applicationSummary =
        ApplicationSummary.builder()
            .id(APPLICATION_ID)
            .clientFirstName(null)
            .clientLastName("Bloggs")
            .referenceNumber(REFERENCE_NUMBER)
            .modifiedAt(MODIFIED_AT)
            .build();

    ApplicationOverview result = applicationMapper.toApplicationOverview(applicationSummary);

    assertThat(result.getName()).isEqualTo("Bloggs");
  }

  @Test
  void shouldMapApplicationSummaryToApplicationOverview_whenClientNamesAreNull() {
    ApplicationSummary applicationSummary =
        ApplicationSummary.builder()
            .id(APPLICATION_ID)
            .clientFirstName(null)
            .clientLastName(null)
            .referenceNumber(REFERENCE_NUMBER)
            .modifiedAt(MODIFIED_AT)
            .build();

    ApplicationOverview result = applicationMapper.toApplicationOverview(applicationSummary);

    assertThat(result.getName()).isEmpty();
  }

  @Test
  void shouldMapDraftStatusToDatastoreApplicationState() {
    assertThat(applicationMapper.toDatastoreApplicationState(ApplicationState.DRAFT))
        .isEqualTo(uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT);
  }

  @Test
  void shouldMapCompletedStatusToDatastoreApplicationState() {
    assertThat(applicationMapper.toDatastoreApplicationState(ApplicationState.COMPLETED))
        .isEqualTo(uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.COMPLETED);
  }

  @Test
  void shouldMapNullStatusToNull() {
    assertThat(applicationMapper.toDatastoreApplicationState(null)).isNull();
  }

  @Test
  void shouldMapApplicationResponseToApplication() {
    OffsetDateTime now = OffsetDateTime.now();
    UUID individualLegalAidNumber = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    UUID declarationId = UUID.fromString("d4e5f6a7-b8c9-0123-def1-234567890123");
    Address address =
        Address.builder()
            .addressLine1("10 Downing Street")
            .townOrCity("London")
            .postCode("SW1A 2AA")
            .country("GB")
            .createdAt(now)
            .modifiedAt(now)
            .build();
    ClientDetails client =
        ClientDetails.builder()
            .individualLegalAidNumber(individualLegalAidNumber)
            .firstName("Joe")
            .lastName("Bloggs")
            .dateOfBirth(LocalDate.of(1990, 1, 1))
            .niNumber("QQ123456C")
            .noFixedAbode(false)
            .address(address)
            .createdAt(now)
            .modifiedAt(now)
            .build();
    DeclarationResponse declaration =
        DeclarationResponse.builder()
            .id(declarationId)
            .clientDeclarationStatus(ClientDeclarationStatus.DRAFT)
            .declarationConfirmation(true)
            .createdAt(now)
            .createdBy("Joe Bloggs")
            .modifiedAt(now)
            .modifiedBy("Joe Bloggs")
            .build();
    EligibilityResult eligibilityResult =
        EligibilityResult.builder()
            .data(Map.of("level_of_help", "controlled"))
            .result(Map.of("indication", true))
            .build();
    ApplicationResponse applicationResponse =
        ApplicationResponse.builder()
            .id(APPLICATION_ID)
            .individualLegalAidNumber(individualLegalAidNumber)
            .client(client)
            .providerFirmCode("123456")
            .providerOfficeCode("22439e72-68d3-4770-b435-c352d883d21e")
            .applicationState(uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT)
            .declaration(declaration)
            .reasonForReapplication("Change in circumstances")
            .meansAssessmentRequired(true)
            .typeOfNonMeans(false)
            .ecfFlag(false)
            .contribution("100.00")
            .applicationType("CONTROLLED_WORK")
            .eligibilityResult(eligibilityResult)
            .createdAt(now)
            .createdBy("Random User")
            .modifiedAt(now)
            .modifiedBy("Random User")
            .build();

    Application result = applicationMapper.toApplication(applicationResponse);

    assertThat(result.getId()).isEqualTo(APPLICATION_ID);
    assertThat(result.getIndividualLegalAidNumber()).isEqualTo(individualLegalAidNumber);
    assertThat(result.getProviderFirmCode()).isEqualTo("123456");
    assertThat(result.getProviderOfficeCode()).isEqualTo("22439e72-68d3-4770-b435-c352d883d21e");
    assertThat(result.getApplicationState()).isEqualTo(ApplicationState.DRAFT);
    assertThat(result.getReasonForReapplication()).isEqualTo("Change in circumstances");
    assertThat(result.getMeansAssessmentRequired()).isTrue();
    assertThat(result.getTypeOfNonMeans()).isFalse();
    assertThat(result.getEcfFlag()).isFalse();
    assertThat(result.getContribution()).isEqualTo("100.00");
    assertThat(result.getApplicationType()).isEqualTo("CONTROLLED_WORK");
    assertThat(result.getCreatedAt()).isEqualTo(now);
    assertThat(result.getCreatedBy()).isEqualTo("Random User");
    assertThat(result.getModifiedAt()).isEqualTo(now);
    assertThat(result.getModifiedBy()).isEqualTo("Random User");
    assertThat(result.getEvidence()).isNull();

    assertThat(result.getClientDetails().getFirstName()).isEqualTo("Joe");
    assertThat(result.getClientDetails().getLastName()).isEqualTo("Bloggs");
    assertThat(result.getClientDetails().getDateOfBirth()).isEqualTo(LocalDate.of(1990, 1, 1));
    assertThat(result.getClientDetails().getNiNumber()).isEqualTo("QQ123456C");
    assertThat(result.getClientDetails().getHasFixedAddress()).isTrue();
    assertThat(result.getClientDetails().getCreatedAt()).isEqualTo(now);
    assertThat(result.getClientDetails().getModifiedAt()).isEqualTo(now);

    assertThat(result.getClientDetails().getAddress().getAddressLine1())
        .isEqualTo("10 Downing Street");
    assertThat(result.getClientDetails().getAddress().getTownOrCity()).isEqualTo("London");
    assertThat(result.getClientDetails().getAddress().getPostCode()).isEqualTo("SW1A 2AA");
    assertThat(result.getClientDetails().getAddress().getCountry()).isEqualTo("GB");

    assertThat(result.getDeclaration().getId()).isEqualTo(declarationId);
    assertThat(result.getDeclaration().getClientDeclarationStatus())
        .isEqualTo(uk.gov.justice.laa.rcw.model.ClientDeclarationStatus.DRAFT);
    assertThat(result.getDeclaration().getDeclarationConfirmation()).isTrue();
    assertThat(result.getDeclaration().getCreatedBy()).isEqualTo("Joe Bloggs");
    assertThat(result.getDeclaration().getModifiedBy()).isEqualTo("Joe Bloggs");

    assertThat(result.getEligibility().getData()).isEqualTo(Map.of("level_of_help", "controlled"));
    assertThat(result.getEligibility().getResult()).isEqualTo(Map.of("indication", true));
  }

  @Test
  void shouldMapApplicationResponseToApplication_whenNoFixedAbodeIsNull() {
    ClientDetails client = ClientDetails.builder().build();
    ApplicationResponse applicationResponse =
        ApplicationResponse.builder()
            .id(APPLICATION_ID)
            .individualLegalAidNumber(APPLICATION_ID)
            .client(client)
            .providerFirmCode("123456")
            .providerOfficeCode("22439e72-68d3-4770-b435-c352d883d21e")
            .ecfFlag(false)
            .applicationType("CONTROLLED_WORK")
            .createdAt(MODIFIED_AT)
            .createdBy("Random User")
            .modifiedAt(MODIFIED_AT)
            .modifiedBy("Random User")
            .build();

    Application result = applicationMapper.toApplication(applicationResponse);

    assertThat(result.getClientDetails().getHasFixedAddress()).isNull();
  }

  @Test
  void shouldMapApplicationResponseToApplication_whenOptionalNestedObjectsAreAbsent() {
    ApplicationResponse applicationResponse =
        ApplicationResponse.builder()
            .id(APPLICATION_ID)
            .individualLegalAidNumber(APPLICATION_ID)
            .client(ClientDetails.builder().build())
            .providerFirmCode("123456")
            .providerOfficeCode("22439e72-68d3-4770-b435-c352d883d21e")
            .ecfFlag(false)
            .applicationType("CONTROLLED_WORK")
            .createdAt(MODIFIED_AT)
            .createdBy("Random User")
            .modifiedAt(MODIFIED_AT)
            .modifiedBy("Random User")
            .build();

    Application result = applicationMapper.toApplication(applicationResponse);

    assertThat(result.getDeclaration()).isNull();
    assertThat(result.getEligibility()).isNull();
    assertThat(result.getEvidence()).isNull();
    assertThat(result.getClientDetails().getAddress()).isNull();
  }
}
