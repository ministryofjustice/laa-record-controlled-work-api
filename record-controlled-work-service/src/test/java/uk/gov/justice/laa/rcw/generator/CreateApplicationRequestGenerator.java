package uk.gov.justice.laa.rcw.generator;

import java.util.function.Consumer;
import uk.gov.justice.laa.rcw.model.CreateApplicationRequestBody;

/** Generator for an Application model for tests. */
public class CreateApplicationRequestGenerator {

  public static CreateApplicationRequestBody createWithName(
      Consumer<CreateApplicationRequestBody.Builder> customizer) {
    return createApplication(customizer)
        .clientDetails(ClientDetailsGenerator.createWithName(null))
        .build();
  }

  public static CreateApplicationRequestBody createWithoutName(
      Consumer<CreateApplicationRequestBody.Builder> customizer) {
    return createApplication(customizer)
        .clientDetails(ClientDetailsGenerator.createWithoutName(null))
        .build();
  }

  private static CreateApplicationRequestBody.Builder createApplication(
      Consumer<CreateApplicationRequestBody.Builder> customizer) {
    var builder = CreateApplicationRequestBody.builder().ecfFlag(false).legalAidBefore("false");
    if (customizer != null) {
      customizer.accept(builder);
    }
    return builder;
  }
}
