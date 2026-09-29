package uk.gov.justice.laa.rcw.generator;

import java.time.LocalDate;
import java.util.Map;
import java.util.function.Consumer;
import uk.gov.justice.laa.rcw.model.CreateAddressRequestBody;
import uk.gov.justice.laa.rcw.model.CreateApplicationRequestBody;
import uk.gov.justice.laa.rcw.model.CreateClientDetailsRequestBody;

/** Generator for an Application model for tests. */
public class CreateApplicationRequestGenerator {

  /** Generator Application with name property. */
  public static CreateApplicationRequestBody createWithName(
      Consumer<CreateApplicationRequestBody.Builder> customizer) {
    return createApplication(customizer).clientDetails(ClientDetails.createWithName(null)).build();
  }

  /** Generator Application without name property. */
  public static CreateApplicationRequestBody createWithoutName(
      Consumer<CreateApplicationRequestBody.Builder> customizer) {
    return createApplication(customizer)
        .clientDetails(ClientDetails.createWithoutName(null))
        .build();
  }

  private static CreateApplicationRequestBody.Builder createApplication(
      Consumer<CreateApplicationRequestBody.Builder> customizer) {
    var builder =
        CreateApplicationRequestBody.builder()
            .legalAidBefore("false")
            .scopingQuestions(Map.of("priorLegalAid", "same_matter"))
            .providerOfficeCode("22439e72-68d3-4770-b435-c352d883d21e");
    if (customizer != null) {
      customizer.accept(builder);
    }
    return builder;
  }

  /** Generator for a CreateClientDetailsRequestBody model for tests. */
  public static class ClientDetails {

    public static CreateClientDetailsRequestBody createWithName(
        Consumer<CreateClientDetailsRequestBody.Builder> customizer) {
      return createClientDetails(customizer).firstName("Joe").lastName("Bloggs").build();
    }

    public static CreateClientDetailsRequestBody createWithoutName(
        Consumer<CreateClientDetailsRequestBody.Builder> customizer) {
      return createClientDetails(customizer).build();
    }

    private static CreateClientDetailsRequestBody.Builder createClientDetails(
        Consumer<CreateClientDetailsRequestBody.Builder> customizer) {

      CreateAddressRequestBody address = Address.create(null);

      var builder =
          CreateClientDetailsRequestBody.builder()
              .niNumber("AB123456C")
              .hasFixedAddress(true)
              .address(address)
              .dateOfBirth(LocalDate.of(1990, 1, 1));
      if (customizer != null) {
        customizer.accept(builder);
      }
      return builder;
    }
  }

  /** Generator for a CreateAddressRequestBody model for tests. */
  public static class Address {

    public static CreateAddressRequestBody create(
        Consumer<CreateAddressRequestBody.Builder> customizer) {
      return createAddress(customizer).build();
    }

    private static CreateAddressRequestBody.Builder createAddress(
        Consumer<CreateAddressRequestBody.Builder> customizer) {
      var builder =
          CreateAddressRequestBody.builder()
              .addressLine1("10 Downing Street")
              .addressLine2("Prime ministers address")
              .postCode("SW1A 2AA")
              .townOrCity("London")
              .country("GB");

      if (customizer != null) {
        customizer.accept(builder);
      }

      return builder;
    }
  }
}
