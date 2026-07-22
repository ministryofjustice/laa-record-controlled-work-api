package uk.gov.justice.laa.rcw.generator;

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
            .ecfFlag(false)
            .legalAidBefore("no")
            .clientDetails(ClientDetailsGenerator.createWithName(null));
    if (customizer != null) {
      customizer.accept(builder);
    }
    return builder;
  }
}
