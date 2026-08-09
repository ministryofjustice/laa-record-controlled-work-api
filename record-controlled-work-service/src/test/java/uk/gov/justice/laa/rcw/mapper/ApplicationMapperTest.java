package uk.gov.justice.laa.rcw.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationSummary;
import uk.gov.justice.laa.ia.datastore.client.model.CreateAddressCommand;
import uk.gov.justice.laa.ia.datastore.client.model.CreateClientCommand;
import uk.gov.justice.laa.ia.datastore.client.model.StartApplicationCommand;
import uk.gov.justice.laa.rcw.generator.AddressGenerator;
import uk.gov.justice.laa.rcw.generator.ClientDetailsGenerator;
import uk.gov.justice.laa.rcw.generator.CreateApplicationRequestGenerator;
import uk.gov.justice.laa.rcw.model.Address;
import uk.gov.justice.laa.rcw.model.Application;
import uk.gov.justice.laa.rcw.model.ApplicationOverview;
import uk.gov.justice.laa.rcw.model.ApplicationState;
import uk.gov.justice.laa.rcw.model.ClientDetails;

class ApplicationMapperTest {

  private static final UUID APPLICATION_ID =
      UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
  private static final UUID CLIENT_ID = UUID.fromString("ebd50ba0-9ed9-4003-83a8-c11ac07d9e32");
  private static final String REFERENCE_NUMBER = "CW-111111";
  private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2024-01-01T09:00:00Z");
  private static final OffsetDateTime MODIFIED_AT = OffsetDateTime.parse("2024-01-02T10:00:00Z");

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
  void shouldMapCreateRequestBodyToStartApplicationCommand() {
    var request = CreateApplicationRequestGenerator.createWithName(null);

    StartApplicationCommand result = applicationMapper.toStartApplicationCommand(request);

    assertThat(result.getApplicationType())
        .isEqualTo(StartApplicationCommand.ApplicationTypeEnum.RCW);
    assertThat(result.getProviderOfficeCode()).isEqualTo(request.getProviderOfficeCode());
    assertThat(result.getClient()).isNotNull();
  }

  @Test
  void shouldMapClientDetailsToCreateClientCommand() {
    ClientDetails clientDetails = ClientDetailsGenerator.createWithName(null);

    CreateClientCommand result = applicationMapper.toCreateClientCommand(clientDetails);

    assertThat(result.getFirstName()).isEqualTo(clientDetails.getFirstName());
    assertThat(result.getLastName()).isEqualTo(clientDetails.getLastName());
    assertThat(result.getDateOfBirth()).isEqualTo(clientDetails.getDateOfBirth());
    assertThat(result.getNationalInsuranceNumber()).isEqualTo(clientDetails.getNiNumber());
    assertThat(result.getNoFixedAbode()).isFalse();
    assertThat(result.getCreateAddressCommand()).isNotNull();
  }

  @Test
  void shouldMapClientDetailsToCreateClientCommand_whenHasFixedAddressIsFalse() {
    ClientDetails clientDetails =
        ClientDetailsGenerator.createWithName(b -> b.hasFixedAddress(false));

    assertThat(applicationMapper.toCreateClientCommand(clientDetails).getNoFixedAbode()).isTrue();
  }

  @Test
  void shouldMapNullClientDetailsToNull() {
    assertThat(applicationMapper.toCreateClientCommand(null)).isNull();
  }

  @Test
  void shouldMapAddressToCreateAddressCommand() {
    Address address = AddressGenerator.create(null);

    CreateAddressCommand result = applicationMapper.toCreateAddressCommand(address);

    assertThat(result.getAddressLine1()).isEqualTo(address.getAddressLine1());
    assertThat(result.getAddressLine2()).isEqualTo(address.getAddressLine2());
    assertThat(result.getTownOrCity()).isEqualTo(address.getTownOrCity());
    assertThat(result.getPostCode()).isEqualTo(address.getPostCode());
    assertThat(result.getCountry()).isEqualTo(address.getCountry());
  }

  @Test
  void shouldMapNullAddressToNullCreateAddressCommand() {
    assertThat(applicationMapper.toCreateAddressCommand(null)).isNull();
  }

  @Test
  void shouldMapApplicationResponseToApplication() {
    ApplicationResponse response =
        ApplicationResponse.builder()
            .id(APPLICATION_ID)
            .individualLegalAidNumber(CLIENT_ID)
            .providerFirmCode("123456")
            .providerOfficeCode("office-123")
            .applicationState(uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT)
            .ecfFlag(false)
            .applicationType("RCW")
            .createdAt(CREATED_AT)
            .createdBy("test-user")
            .modifiedAt(MODIFIED_AT)
            .modifiedBy("test-user")
            .build();

    Application result = applicationMapper.toApplication(response);

    assertThat(result.getId()).isEqualTo(APPLICATION_ID);
    assertThat(result.getIndividualLegalAidNumber()).isEqualTo(CLIENT_ID);
    assertThat(result.getProviderFirmCode()).isEqualTo("123456");
    assertThat(result.getApplicationState()).isEqualTo(ApplicationState.DRAFT);
    assertThat(result.getCreatedAt()).isEqualTo(CREATED_AT);
    assertThat(result.getCreatedBy()).isEqualTo("test-user");
    assertThat(result.getModifiedAt()).isEqualTo(MODIFIED_AT);
    assertThat(result.getModifiedBy()).isEqualTo("test-user");
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

  @Test
  void shouldMapDatastoreClientDetailsToClientDetails() {
    uk.gov.justice.laa.ia.datastore.client.model.ClientDetails datastoreClient =
        uk.gov.justice.laa.ia.datastore.client.model.ClientDetails.builder()
            .individualLegalAidNumber(CLIENT_ID)
            .firstName("Joe")
            .lastName("Bloggs")
            .dateOfBirth(LocalDate.of(1990, 1, 1))
            .niNumber("QQ123456C")
            .noFixedAbode(false)
            .createdAt(CREATED_AT)
            .modifiedAt(MODIFIED_AT)
            .build();

    ClientDetails result = applicationMapper.toClientDetails(datastoreClient);

    assertThat(result.getId()).isEqualTo(CLIENT_ID);
    assertThat(result.getFirstName()).isEqualTo("Joe");
    assertThat(result.getLastName()).isEqualTo("Bloggs");
    assertThat(result.getDateOfBirth()).isEqualTo(LocalDate.of(1990, 1, 1));
    assertThat(result.getNiNumber()).isEqualTo("QQ123456C");
    assertThat(result.getHasFixedAddress()).isTrue();
    assertThat(result.getCreatedAt()).isEqualTo(CREATED_AT);
    assertThat(result.getModifiedAt()).isEqualTo(MODIFIED_AT);
  }

  @Test
  void shouldMapDatastoreClientDetailsToClientDetails_whenNoFixedAbodeIsTrue() {
    uk.gov.justice.laa.ia.datastore.client.model.ClientDetails datastoreClient =
        uk.gov.justice.laa.ia.datastore.client.model.ClientDetails.builder()
            .noFixedAbode(true)
            .build();

    assertThat(applicationMapper.toClientDetails(datastoreClient).getHasFixedAddress()).isFalse();
  }

  @Test
  void shouldMapNullDatastoreClientDetailsToNull() {
    assertThat(applicationMapper.toClientDetails(null)).isNull();
  }

  @Test
  void shouldMapDatastoreAddressToAddress() {
    uk.gov.justice.laa.ia.datastore.client.model.Address datastoreAddress =
        uk.gov.justice.laa.ia.datastore.client.model.Address.builder()
            .addressLine1("10 Downing Street")
            .addressLine2("Prime ministers address")
            .townOrCity("London")
            .postCode("SW1A 2AA")
            .country("GB")
            .createdAt(CREATED_AT)
            .modifiedAt(MODIFIED_AT)
            .build();

    Address result = applicationMapper.toAddress(datastoreAddress);

    assertThat(result.getAddressLine1()).isEqualTo("10 Downing Street");
    assertThat(result.getAddressLine2()).isEqualTo("Prime ministers address");
    assertThat(result.getTownOrCity()).isEqualTo("London");
    assertThat(result.getPostCode()).isEqualTo("SW1A 2AA");
    assertThat(result.getCountry()).isEqualTo("GB");
    assertThat(result.getCreatedAt()).isEqualTo(CREATED_AT);
    assertThat(result.getModifiedAt()).isEqualTo(MODIFIED_AT);
  }

  @Test
  void shouldMapNullDatastoreAddressToNull() {
    assertThat(applicationMapper.toAddress(null)).isNull();
  }
}
