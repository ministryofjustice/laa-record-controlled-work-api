package uk.gov.justice.laa.rcw.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import uk.gov.justice.laa.rcw.generator.CreateApplicationRequestGenerator;
import uk.gov.justice.laa.rcw.model.ClientDetails;
import uk.gov.justice.laa.rcw.model.CreateClientDetailsRequestBody;
import uk.gov.justice.laa.rcw.model.UpdateClientDetailsRequestBody;

class ClientDetailsMapperTest {

  private final ClientDetailsMapper clientDetailsMapper =
      new ClientDetailsMapperImpl(
          new AddressMapperImpl(new JsonNullableMapperImpl()), new JsonNullableMapperImpl());

  @Test
  void shouldMapDatastoreClientDetailsAndNestedAddress() {
    var datastoreClient =
        uk.gov.justice.laa.ia.datastore.client.model.ClientDetails.builder()
            .firstName("Joe")
            .lastName("Bloggs")
            .dateOfBirth(LocalDate.of(1990, 1, 1))
            .niNumber("QQ123456C")
            .noFixedAbode(false)
            .address(
                uk.gov.justice.laa.ia.datastore.client.model.Address.builder()
                    .addressLine1("10 Downing Street")
                    .townOrCity("London")
                    .postCode("SW1A 2AA")
                    .country("GB")
                    .build())
            .build();

    ClientDetails result = clientDetailsMapper.toClientDetails(datastoreClient);

    assertThat(result.getFirstName()).isEqualTo("Joe");
    assertThat(result.getLastName()).isEqualTo("Bloggs");
    assertThat(result.getDateOfBirth()).isEqualTo(LocalDate.of(1990, 1, 1));
    assertThat(result.getNiNumber()).isEqualTo("QQ123456C");
    assertThat(result.getHasFixedAddress()).isTrue();
    assertThat(result.getAddress().getAddressLine1()).isEqualTo("10 Downing Street");
    assertThat(result.getAddress().getTownOrCity()).isEqualTo("London");
    assertThat(result.getAddress().getPostCode()).isEqualTo("SW1A 2AA");
    assertThat(result.getAddress().getCountry()).isEqualTo("GB");
  }

  @Test
  void shouldMapNoFixedAbodeToHasFixedAddress() {
    var datastoreClient =
        uk.gov.justice.laa.ia.datastore.client.model.ClientDetails.builder()
            .noFixedAbode(true)
            .build();

    assertThat(clientDetailsMapper.toClientDetails(datastoreClient).getHasFixedAddress()).isFalse();
  }

  @Test
  void shouldPreserveUnknownFixedAddressWhenNoFixedAbodeIsNull() {
    var datastoreClient =
        uk.gov.justice.laa.ia.datastore.client.model.ClientDetails.builder().build();

    assertThat(clientDetailsMapper.toClientDetails(datastoreClient).getHasFixedAddress()).isNull();
  }

  @Test
  void shouldMapNullDatastoreClientDetailsToNull() {
    assertThat(clientDetailsMapper.toClientDetails(null)).isNull();
  }

  @Test
  void shouldMapClientDetailsAndNestedAddressToDatastoreCommand() {
    CreateClientDetailsRequestBody client =
        CreateApplicationRequestGenerator.ClientDetails.createWithName(null);

    var result = clientDetailsMapper.toCreateClientCommand(client);

    assertThat(result.getFirstName()).isEqualTo(client.getFirstName());
    assertThat(result.getLastName()).isEqualTo(client.getLastName());
    assertThat(result.getDateOfBirth()).isEqualTo(client.getDateOfBirth());
    assertThat(result.getNationalInsuranceNumber()).isEqualTo(client.getNiNumber());
    assertThat(result.getNoFixedAbode()).isFalse();
    assertThat(result.getCreateAddressCommand().getAddressLine1())
        .isEqualTo(client.getAddress().getAddressLine1());
  }

  @Test
  void shouldMapHasFixedAddressToNoFixedAbode() {
    CreateClientDetailsRequestBody client =
        CreateApplicationRequestGenerator.ClientDetails.createWithName(
            builder -> builder.hasFixedAddress(false));

    assertThat(clientDetailsMapper.toCreateClientCommand(client).getNoFixedAbode()).isTrue();
  }

  @Test
  void shouldTreatNullHasFixedAddressAsNoFixedAbode() {
    CreateClientDetailsRequestBody client =
        CreateApplicationRequestGenerator.ClientDetails.createWithName(
            builder -> builder.hasFixedAddress(null));

    assertThat(clientDetailsMapper.toCreateClientCommand(client).getNoFixedAbode()).isTrue();
  }

  @Test
  void shouldMapNullClientDetailsRequestToNull() {
    assertThat(clientDetailsMapper.toCreateClientCommand(null)).isNull();
  }

  @Test
  void shouldMapEditableClientDetailsIncludingAddressAndBooleanAnswers() {
    UpdateClientDetailsRequestBody client =
        new UpdateClientDetailsRequestBody()
            .firstName("Ada")
            .lastName("Lovelace")
            .dateOfBirth(LocalDate.of(1990, 1, 1))
            .niNumber("AB123456C")
            .hasFixedAddress(true)
            .address(
                new uk.gov.justice.laa.rcw.model.UpdateAddressRequestBody()
                    .addressLine1("1 Example Street")
                    .addressLine2(null)
                    .addressLine3("")
                    .addressLine4(null)
                    .townOrCity("London")
                    .postCode(null)
                    .county(null)
                    .country("GB"));

    var result = clientDetailsMapper.toPatchClientDetailsData(client);

    assertThat(result.getFirstName()).isEqualTo("Ada");
    assertThat(result.getLastName()).isEqualTo("Lovelace");
    assertThat(result.getDateOfBirth()).isEqualTo(LocalDate.of(1990, 1, 1));
    assertThat(result.getNiNumber_JsonNullable()).isEqualTo(JsonNullable.of("AB123456C"));
    assertThat(result.getNoFixedAbode()).isFalse();
    assertThat(result.getAddress_JsonNullable().isPresent()).isTrue();
    assertThat(result.getAddress().getAddressLine2_JsonNullable()).isEqualTo(JsonNullable.of(null));
    assertThat(result.getAddress().getAddressLine3_JsonNullable()).isEqualTo(JsonNullable.of(""));

    client.setHasFixedAddress(false);
    assertThat(clientDetailsMapper.toPatchClientDetailsData(client).getNoFixedAbode()).isTrue();
  }

  @Test
  void shouldPreserveExplicitNullClientClearsAsPresent() {
    UpdateClientDetailsRequestBody client =
        new UpdateClientDetailsRequestBody()
            .firstName("Ada")
            .lastName("Lovelace")
            .dateOfBirth(LocalDate.of(1990, 1, 1))
            .niNumber(null)
            .hasFixedAddress(false)
            .address(null);

    var result = clientDetailsMapper.toPatchClientDetailsData(client);

    assertThat(result.getNiNumber_JsonNullable()).isEqualTo(JsonNullable.of(null));
    assertThat(result.getAddress_JsonNullable()).isEqualTo(JsonNullable.of(null));
  }
}
