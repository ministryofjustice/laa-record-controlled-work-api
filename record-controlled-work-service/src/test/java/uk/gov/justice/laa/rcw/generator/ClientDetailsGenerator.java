package uk.gov.justice.laa.rcw.generator;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Consumer;
import uk.gov.justice.laa.rcw.model.Address;
import uk.gov.justice.laa.rcw.model.ClientDetails;

/** Generator for setting for an ClientDetails model for tests. */
public class ClientDetailsGenerator {

  public static ClientDetails createWithName(Consumer<ClientDetails.Builder> customizer) {
    return createClientDetails(customizer).firstName("Joe").lastName("Bloggs").build();
  }

  public static ClientDetails createWithoutName(Consumer<ClientDetails.Builder> customizer) {
    return createClientDetails(customizer).build();
  }

  private static ClientDetails.Builder createClientDetails(
      Consumer<ClientDetails.Builder> customizer) {

    Address address = AddressGenerator.create(null);

    var builder =
        ClientDetails.builder()
            .id(UUID.randomUUID())
            .niNumber("QQ123456C")
            .hasFixedAddress(true)
            .address(address)
            .dateOfBirth(LocalDate.of(1990, 1, 1))
            .createdAt(OffsetDateTime.now())
            .modifiedAt(OffsetDateTime.now());
    if (customizer != null) {
      customizer.accept(builder);
    }
    return builder;
  }
}
