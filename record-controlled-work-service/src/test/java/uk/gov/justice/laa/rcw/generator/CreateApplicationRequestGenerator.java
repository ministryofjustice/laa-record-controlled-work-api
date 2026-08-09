package uk.gov.justice.laa.rcw.generator;

import java.util.function.Consumer;
import uk.gov.justice.laa.rcw.model.CreateApplicationRequestBody;

/** Generator for an Application model for tests. */
public class CreateApplicationRequestGenerator {

  /** Generator Application with name property. */
  public static CreateApplicationRequestBody createWithName(
      Consumer<CreateApplicationRequestBody.Builder> customizer) {
    return createApplication(customizer)
        .clientDetails(ClientDetailsGenerator.createWithName(null))
        .build();
  }

  /** Generator Application without name property. */
  public static CreateApplicationRequestBody createWithoutName(
      Consumer<CreateApplicationRequestBody.Builder> customizer) {
    return createApplication(customizer)
        .clientDetails(ClientDetailsGenerator.createWithoutName(null))
        .build();
  }

  private static CreateApplicationRequestBody.Builder createApplication(
      Consumer<CreateApplicationRequestBody.Builder> customizer) {
    var builder =
        CreateApplicationRequestBody.builder()
            .ecfFlag(false)
            .legalAidBefore("false")
            .providerOfficeCode("22439e72-68d3-4770-b435-c352d883d21e");
    if (customizer != null) {
      customizer.accept(builder);
    }
    return builder;
  }
}
