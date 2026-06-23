package uk.gov.justice.laa.rcw.generator;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Consumer;
import uk.gov.justice.laa.rcw.model.Address;

/** Generator for an Address model for tests. */
public class AddressGenerator {

  public static Address create(Consumer<Address.Builder> customizer) {
    return createAddress(customizer).build();
  }

  private static Address.Builder createAddress(Consumer<Address.Builder> customizer) {
    var builder =
        Address.builder()
            .id(UUID.randomUUID())
            .addressLine1("10 Downing Street")
            .addressLine2("Prime ministers address")
            .postCode("SW1A 2AA")
            .townOrCity("London")
            .country("GB")
            .createdAt(OffsetDateTime.now())
            .modifiedAt(OffsetDateTime.now());

    if (customizer != null) {
      customizer.accept(builder);
    }

    return builder;
  }
}
