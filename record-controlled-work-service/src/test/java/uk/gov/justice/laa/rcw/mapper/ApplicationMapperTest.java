package uk.gov.justice.laa.rcw.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.justice.laa.ia.datastore.client.model.Address;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationSummary;
import uk.gov.justice.laa.ia.datastore.client.model.ClientDetails;
import uk.gov.justice.laa.ia.datastore.client.model.DeclarationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.EditApplicationCommand;
import uk.gov.justice.laa.ia.datastore.client.model.EligibilityResult;
import uk.gov.justice.laa.ia.datastore.client.model.EvidenceResponse;
import uk.gov.justice.laa.ia.datastore.client.model.StartApplicationCommand;
import uk.gov.justice.laa.rcw.generator.CreateApplicationRequestGenerator;
import uk.gov.justice.laa.rcw.model.Application;
import uk.gov.justice.laa.rcw.model.ApplicationOverview;
import uk.gov.justice.laa.rcw.model.ApplicationScopingQuestions;
import uk.gov.justice.laa.rcw.model.ApplicationState;
import uk.gov.justice.laa.rcw.model.EligibilityIndication;
import uk.gov.justice.laa.rcw.model.FamilyLawClassification;
import uk.gov.justice.laa.rcw.model.PriorLegalAid;
import uk.gov.justice.laa.rcw.model.UpdateAddressRequestBody;
import uk.gov.justice.laa.rcw.model.UpdateApplicationDetailsRequestBody;
import uk.gov.justice.laa.rcw.model.UpdateClientDetailsRequestBody;
import uk.gov.justice.laa.rcw.util.MapperFixtures;

class ApplicationMapperTest {

  private static final UUID APPLICATION_ID =
      UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
  private static final String REFERENCE_NUMBER = "CW-111111";
  private static final OffsetDateTime MODIFIED_AT = OffsetDateTime.parse("2024-01-02T10:00:00Z");

  private final ApplicationMapper applicationMapper = MapperFixtures.applicationMapper();

  @Test
  void shouldMapApplicationSummaryToApplicationOverview() {
    ApplicationSummary applicationSummary =
        ApplicationSummary.builder()
            .id(APPLICATION_ID)
            .clientFirstName("Joe")
            .clientLastName("Bloggs")
            .referenceNumber(REFERENCE_NUMBER)
            .modifiedAt(MODIFIED_AT)
            .eligibilityIndication(
                uk.gov.justice.laa.ia.datastore.client.model.EligibilityIndication.ELIGIBLE)
            .build();

    ApplicationOverview result = applicationMapper.toApplicationOverview(applicationSummary);

    assertThat(result.getId()).isEqualTo(APPLICATION_ID);
    assertThat(result.getName()).isEqualTo("Joe Bloggs");
    assertThat(result.getApplicationRefNumber()).isEqualTo(REFERENCE_NUMBER);
    assertThat(result.getModifiedAt()).isEqualTo(MODIFIED_AT);
    assertThat(result.getEligibilityIndication()).isEqualTo(EligibilityIndication.ELIGIBLE);
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
            .declarationConfirmation(true)
            .createdAt(now)
            .createdBy("Joe Bloggs")
            .modifiedAt(now)
            .modifiedBy("Joe Bloggs")
            .build();
    EligibilityResult eligibilityResult =
        EligibilityResult.builder()
            .data(
                uk.gov.justice.laa.ia.datastore.client.model.EligibilityData.builder()
                    .levelOfHelp("controlled")
                    .build())
            .result(Map.of("indication", true))
            .build();
    EvidenceResponse evidence =
        EvidenceResponse.builder()
            .evidenceExemptionCode("adviceOverPhone")
            .evidenceExemptionReason("Client was advised over the phone")
            .incomeEvidenceChecklist(Map.of("payslips", true))
            .expenditureCapitalEvidenceChecklist(Map.of("bankStatements", true))
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
            .contribution("100.00")
            .scopingQuestions(
                Map.of("priorLegalAid", "yesSameMatter", "familyLawClassification", "public"))
            .applicationType("CONTROLLED_WORK")
            .eligibilityResult(eligibilityResult)
            .evidence(evidence)
            .referenceNumber(REFERENCE_NUMBER)
            .ufn("123456/123")
            .createdAt(now)
            .createdBy("Random User")
            .modifiedAt(now)
            .modifiedBy("Random User")
            .build();

    Application result = applicationMapper.toApplication(applicationResponse);

    assertThat(result.getId()).isEqualTo(APPLICATION_ID);
    assertThat(result.getApplicationRefNumber()).isEqualTo(REFERENCE_NUMBER);
    assertThat(result.getUfn()).isEqualTo("123456/123");
    assertThat(result.getIndividualLegalAidNumber()).isEqualTo(individualLegalAidNumber);
    assertThat(result.getProviderFirmCode()).isEqualTo("123456");
    assertThat(result.getProviderOfficeCode()).isEqualTo("22439e72-68d3-4770-b435-c352d883d21e");
    assertThat(result.getApplicationState()).isEqualTo(ApplicationState.DRAFT);
    assertThat(result.getReasonForReapplication()).isEqualTo("Change in circumstances");
    assertThat(result.getMeansAssessmentRequired()).isTrue();
    assertThat(result.getTypeOfNonMeans()).isFalse();
    assertThat(result.getContribution()).isEqualTo("100.00");
    assertThat(result.getScopingQuestions())
        .isEqualTo(
            new ApplicationScopingQuestions()
                .priorLegalAid(PriorLegalAid.YES_SAME_MATTER)
                .familyLawClassification(FamilyLawClassification.PUBLIC));
    assertThat(result.getApplicationType()).isEqualTo("CONTROLLED_WORK");
    assertThat(result.getCreatedAt()).isEqualTo(now);
    assertThat(result.getCreatedBy()).isEqualTo("Random User");
    assertThat(result.getModifiedAt()).isEqualTo(now);
    assertThat(result.getModifiedBy()).isEqualTo("Random User");
    assertThat(result.getEvidence()).isNotNull();
    assertThat(result.getEvidence().getEvidenceExemptionCode()).isEqualTo("adviceOverPhone");
    assertThat(result.getClientDetails().getFirstName()).isEqualTo("Joe");
    assertThat(result.getClientDetails().getHasFixedAddress()).isTrue();
    assertThat(result.getClientDetails().getAddress().getAddressLine1())
        .isEqualTo("10 Downing Street");
    assertThat(result.getDeclaration().getDeclarationConfirmation()).isTrue();
    assertThat(result.getEligibility().getResult()).isEqualTo(Map.of("indication", true));
  }

  @Test
  void shouldPreserveMissingPriorLegalAid() {
    Application result =
        applicationMapper.toApplication(
            ApplicationResponse.builder().scopingQuestions(Map.of("otherAnswer", true)).build());

    assertThat(result.getScopingQuestions()).isNotNull();
    assertThat(result.getScopingQuestions().getPriorLegalAid()).isNull();
    assertThat(result.getScopingQuestions().getFamilyLawClassification()).isNull();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(booleans = {true, false})
  void shouldPreserveNullableEcf(Boolean ecf) {
    Application result =
        applicationMapper.toApplication(ApplicationResponse.builder().ecfFlag(ecf).build());

    assertThat(result.getEcfFlag()).isEqualTo(ecf);
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
    assertThat(result.getUfn()).isNull();
    assertThat(result.getClientDetails().getAddress()).isNull();
  }

  @Test
  void shouldMapCreateRequestBodyToStartApplicationCommand() {
    var request = CreateApplicationRequestGenerator.createWithName(null);

    StartApplicationCommand result = applicationMapper.toStartApplicationCommand(request);

    assertThat(result.getApplicationType())
        .isEqualTo(StartApplicationCommand.ApplicationTypeEnum.RCW);
    assertThat(result.getProviderOfficeCode()).isEqualTo(request.getProviderOfficeCode());
    assertThat(result.getClient()).isNotNull();
    assertThat(result.getClient().getFirstName())
        .isEqualTo(request.getClientDetails().getFirstName());
    assertThat(result.getClient().getNoFixedAbode()).isFalse();
    assertThat(result.getClient().getCreateAddressCommand().getAddressLine1())
        .isEqualTo(request.getClientDetails().getAddress().getAddressLine1());
  }

  @Test
  void shouldMapDetailsSnapshotToSparseEditCommand() {
    UpdateApplicationDetailsRequestBody request =
        fixedAddressRequest("GB", "", PriorLegalAid.YES_SAME_MATTER, true, "Reapplication");

    EditApplicationCommand result = applicationMapper.toEditApplicationCommand(request, 19L);

    assertThat(result.geteTag()).isEqualTo(19L);
    assertThat(result.getReasonForReapplication_JsonNullable().get()).isEqualTo("Reapplication");
    assertThat(result.getEcfFlag_JsonNullable().get()).isTrue();
    assertThat(result.getScopingQuestions_JsonNullable().isPresent()).isTrue();
    assertThat(result.getScopingQuestions_JsonNullable().get())
        .isEqualTo(Map.of("priorLegalAid", "yesSameMatter"));
    assertThat(result.getUfn()).isNull();
    assertThat(result.getLaaReference()).isNull();
    assertThat(result.getMeansAssessmentRequired()).isNull();
    assertThat(result.getTypeOfNonMeans()).isNull();
    assertThat(result.getContribution()).isNull();
    assertThat(result.getDeterminationId()).isNull();
    assertThat(result.getDeclaration()).isNull();
    assertThat(result.getEvidence()).isNull();

    var client = result.getClientDetails();
    assertThat(client.getFirstName()).isEqualTo("Ada");
    assertThat(client.getLastName()).isEqualTo("Lovelace");
    assertThat(client.getDateOfBirth()).isEqualTo(LocalDate.of(1990, 1, 1));
    assertThat(client.getNiNumber_JsonNullable().get()).isEqualTo("AB123456C");
    assertThat(client.getNoFixedAbode()).isFalse();
    assertThat(client.getAddress_JsonNullable().isPresent()).isTrue();

    var address = client.getAddress();
    assertThat(address.getAddressLine1()).isEqualTo("1 Example Street");
    assertThat(address.getAddressLine2_JsonNullable().get()).isEmpty();
    assertThat(address.getAddressLine3_JsonNullable().isPresent()).isTrue();
    assertThat(address.getAddressLine3_JsonNullable().get()).isNull();
    assertThat(address.getAddressLine4_JsonNullable().get()).isNull();
    assertThat(address.getTownOrCity_JsonNullable().get()).isNull();
    assertThat(address.getPostCode_JsonNullable().get()).isNull();
    assertThat(address.getCounty_JsonNullable().get()).isNull();
    assertThat(address.getCountry()).isEqualTo("GB");
  }

  @ParameterizedTest
  @ValueSource(strings = {"GB", "FR"})
  void shouldMapFixedAddressCountry(String country) {
    EditApplicationCommand result =
        applicationMapper.toEditApplicationCommand(
            fixedAddressRequest(country, null, PriorLegalAid.NO, false, null), 4L);

    assertThat(result.getClientDetails().getNoFixedAbode()).isFalse();
    assertThat(result.getClientDetails().getAddress().getCountry()).isEqualTo(country);
  }

  @Test
  void shouldMapNoFixedAddressAndExplicitNullableClears() {
    UpdateApplicationDetailsRequestBody request =
        new UpdateApplicationDetailsRequestBody()
            .priorLegalAid(PriorLegalAid.NO)
            .legalAidLast6Months(false)
            .reasonForReapplication(null)
            .ecfFlag(false)
            .clientDetails(
                new UpdateClientDetailsRequestBody()
                    .firstName("Ada")
                    .lastName("Lovelace")
                    .dateOfBirth(LocalDate.of(1990, 1, 1))
                    .niNumber(null)
                    .hasFixedAddress(false)
                    .address(null));

    EditApplicationCommand result = applicationMapper.toEditApplicationCommand(request, 7L);

    assertThat(result.getReasonForReapplication_JsonNullable().isPresent()).isTrue();
    assertThat(result.getReasonForReapplication_JsonNullable().get()).isNull();
    assertThat(result.getEcfFlag_JsonNullable().get()).isFalse();
    assertThat(result.getScopingQuestions_JsonNullable().isPresent()).isTrue();
    assertThat(result.getScopingQuestions_JsonNullable().get())
        .isEqualTo(Map.of("priorLegalAid", "no"));
    assertThat(result.getClientDetails().getNiNumber_JsonNullable().isPresent()).isTrue();
    assertThat(result.getClientDetails().getNiNumber_JsonNullable().get()).isNull();
    assertThat(result.getClientDetails().getNoFixedAbode()).isTrue();
    assertThat(result.getClientDetails().getAddress_JsonNullable().isPresent()).isTrue();
    assertThat(result.getClientDetails().getAddress_JsonNullable().get()).isNull();
  }

  private static UpdateApplicationDetailsRequestBody fixedAddressRequest(
      String country,
      String addressLine2,
      PriorLegalAid priorLegalAid,
      boolean last6Months,
      String reason) {
    UpdateAddressRequestBody address =
        new UpdateAddressRequestBody()
            .addressLine1("1 Example Street")
            .addressLine2(addressLine2)
            .addressLine3(null)
            .addressLine4(null)
            .townOrCity(null)
            .postCode(null)
            .county(null)
            .country(country);
    UpdateClientDetailsRequestBody clientDetails =
        new UpdateClientDetailsRequestBody()
            .firstName("Ada")
            .lastName("Lovelace")
            .dateOfBirth(LocalDate.of(1990, 1, 1))
            .niNumber("AB123456C")
            .hasFixedAddress(true)
            .address(address);
    return new UpdateApplicationDetailsRequestBody()
        .priorLegalAid(priorLegalAid)
        .legalAidLast6Months(last6Months)
        .reasonForReapplication(reason)
        .ecfFlag(true)
        .clientDetails(clientDetails);
  }

  @Test
  void shouldMapNullApplicationResponseToNull() {
    assertThat(applicationMapper.toApplication(null)).isNull();
  }

  @Test
  void shouldMapDraftDatastoreStateToApplicationState() {
    assertThat(
            applicationMapper.toApplicationState(
                uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT))
        .isEqualTo(ApplicationState.DRAFT);
  }

  @Test
  void shouldMapCompletedDatastoreStateToApplicationState() {
    assertThat(
            applicationMapper.toApplicationState(
                uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.COMPLETED))
        .isEqualTo(ApplicationState.COMPLETED);
  }

  @Test
  void shouldMapNullDatastoreApplicationStateToNull() {
    assertThat(applicationMapper.toApplicationState(null)).isNull();
  }
}
