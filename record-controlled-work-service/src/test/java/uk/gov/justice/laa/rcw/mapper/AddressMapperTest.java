package uk.gov.justice.laa.rcw.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import uk.gov.justice.laa.rcw.generator.CreateApplicationRequestGenerator;
import uk.gov.justice.laa.rcw.model.Address;
import uk.gov.justice.laa.rcw.model.CreateAddressRequestBody;
import uk.gov.justice.laa.rcw.model.UpdateAddressRequestBody;

class AddressMapperTest {

  private final AddressMapper addressMapper = new AddressMapperImpl(new JsonNullableMapperImpl());

  @Test
  void shouldMapDatastoreAddressToAddress() {
    var datastoreAddress =
        uk.gov.justice.laa.ia.datastore.client.model.Address.builder()
            .addressLine1("10 Downing Street")
            .addressLine2("Prime ministers address")
            .townOrCity("London")
            .postCode("SW1A 2AA")
            .country("GB")
            .build();

    Address result = addressMapper.toAddress(datastoreAddress);

    assertThat(result.getAddressLine1()).isEqualTo("10 Downing Street");
    assertThat(result.getAddressLine2()).isEqualTo("Prime ministers address");
    assertThat(result.getTownOrCity()).isEqualTo("London");
    assertThat(result.getPostCode()).isEqualTo("SW1A 2AA");
    assertThat(result.getCountry()).isEqualTo("GB");
  }

  @Test
  void shouldMapNullDatastoreAddressToNull() {
    assertThat(addressMapper.toAddress(null)).isNull();
  }

  @Test
  void shouldMapCreateAddressRequestToDatastoreCommand() {
    CreateAddressRequestBody address = CreateApplicationRequestGenerator.Address.create(null);

    var result = addressMapper.toCreateAddressCommand(address);

    assertThat(result.getAddressLine1()).isEqualTo(address.getAddressLine1());
    assertThat(result.getAddressLine2()).isEqualTo(address.getAddressLine2());
    assertThat(result.getTownOrCity()).isEqualTo(address.getTownOrCity());
    assertThat(result.getPostCode()).isEqualTo(address.getPostCode());
    assertThat(result.getCountry()).isEqualTo(address.getCountry());
  }

  @Test
  void shouldMapEditableAddressAndPreserveNullableValues() {
    UpdateAddressRequestBody address =
        new UpdateAddressRequestBody()
            .addressLine1("1 Example Street")
            .addressLine2("")
            .addressLine3(null)
            .addressLine4("Flat 2")
            .townOrCity("London")
            .postCode(null)
            .county("")
            .country("GB");

    var result = addressMapper.toPatchAddressData(address);

    assertThat(result.getAddressLine1()).isEqualTo("1 Example Street");
    assertThat(result.getAddressLine2_JsonNullable()).isEqualTo(JsonNullable.of(""));
    assertThat(result.getAddressLine3_JsonNullable()).isEqualTo(JsonNullable.of(null));
    assertThat(result.getAddressLine4_JsonNullable()).isEqualTo(JsonNullable.of("Flat 2"));
    assertThat(result.getTownOrCity_JsonNullable()).isEqualTo(JsonNullable.of("London"));
    assertThat(result.getPostCode_JsonNullable()).isEqualTo(JsonNullable.of(null));
    assertThat(result.getCounty_JsonNullable()).isEqualTo(JsonNullable.of(""));
    assertThat(result.getCountry()).isEqualTo("GB");
  }

  @Test
  void shouldMapNullEditableAddressToNull() {
    assertThat(addressMapper.toPatchAddressData(null)).isNull();
  }

  @Test
  void shouldMapNullCreateAddressRequestToNull() {
    assertThat(addressMapper.toCreateAddressCommand(null)).isNull();
  }
}
