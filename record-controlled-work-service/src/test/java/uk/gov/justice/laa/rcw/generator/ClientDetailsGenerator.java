package uk.gov.justice.laa.rcw.generator;

import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Consumer;
import uk.gov.justice.laa.rcw.model.Address;
import uk.gov.justice.laa.rcw.model.ClientDetails;

/** Generator for setting for an ClientDetails model for tests. */
public class ClientDetailsGenerator {

  public static ClientDetails create(Consumer<ClientDetails.Builder> customizer) {
    return createClientDetails(customizer).build();
  }

  private static ClientDetails.Builder createClientDetails(
      Consumer<ClientDetails.Builder> customizer) {

    Address address = AddressGenerator.create(null);

    var builder =
        ClientDetails.builder()
            .id(UUID.randomUUID())
            .fullName("Joe Bloggs")
            .niNumber("AB123456Q")
            .address(address)
            .dateOfBirth(LocalDate.of(1990, 1, 1));
    if (customizer != null) {
      customizer.accept(builder);
    }
    return builder;
  }
}
