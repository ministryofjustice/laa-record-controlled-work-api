package uk.gov.justice.laa.rcw.generator;

import java.time.LocalDate;
import java.util.function.Consumer;
import uk.gov.justice.laa.rcw.model.CreateApplicationRequestBody;

/** Generator for an Application model for tests. */
public class CreateApplicationRequestGenerator {

  public static CreateApplicationRequestBody createWithName(
      Consumer<CreateApplicationRequestBody.Builder> customizer) {
    return createApplication(customizer).fullName("Joe Bloggs").build();
  }

  public static CreateApplicationRequestBody createWithoutName(
      Consumer<CreateApplicationRequestBody.Builder> customizer) {
    return createApplication(customizer).build();
  }

  private static CreateApplicationRequestBody.Builder createApplication(
      Consumer<CreateApplicationRequestBody.Builder> customizer) {
    var builder =
        CreateApplicationRequestBody.builder()
            .ecf("false")
            .legalAidBefore("false")
            .dateOfBirth(LocalDate.of(2000, 1, 1))
            .hasNINumber("yes")
            .niNumber("JN123456D")
            .haveAHomeAddress("yes")
            .addressLine1("123 Fake Street")
            .townOrCity("Manchester")
            .country("UK")
            .postcode("TE55TT");
    if (customizer != null) {
      customizer.accept(builder);
    }
    return builder;
  }
}
