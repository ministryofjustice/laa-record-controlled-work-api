package uk.gov.justice.laa.rcw.generator;

import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Consumer;
import uk.gov.justice.laa.rcw.model.CreateApplicationResponseBody;

/** Generator for an Application model for tests. */
public class CreateApplicationResponseGenerator {

  public static CreateApplicationResponseBody create(
      Consumer<CreateApplicationResponseBody.Builder> customizer) {
    return createApplication(customizer).build();
  }

  private static CreateApplicationResponseBody.Builder createApplication(
      Consumer<CreateApplicationResponseBody.Builder> customizer) {
    var builder =
        CreateApplicationResponseBody.builder()
            .id(UUID.randomUUID())
            .ecf("false")
            .legalAidBefore("false")
            .firstName("Joe")
            .lastName("Bloggs")
            .dateOfBirth(LocalDate.of(2000, 1, 1))
            .hasNINumber("yes")
            .niNumber("JN123456D")
            .haveAHomeAddress("yes")
            .addressLine1("123 Fake Street")
            .townOrCity("Manchester")
            .postcode("TE55TT");
    if (customizer != null) {
      customizer.accept(builder);
    }
    return builder;
  }
}
