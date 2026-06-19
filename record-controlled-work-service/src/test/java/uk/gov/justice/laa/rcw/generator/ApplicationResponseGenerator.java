package uk.gov.justice.laa.rcw.generator;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Consumer;
import uk.gov.justice.laa.rcw.model.ApplicationResponse;

/** Generator for an Application model for tests. */
public class ApplicationResponseGenerator {

  public static ApplicationResponse create(Consumer<ApplicationResponse.Builder> customizer) {
    return createApplication(customizer).build();
  }

  private static ApplicationResponse.Builder createApplication(
      Consumer<ApplicationResponse.Builder> customizer) {
    var builder =
        ApplicationResponse.builder()
            .id(UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901"))
            .individualLegalAidNumber(UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901"))
            .providerFirmId(UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901"))
            .providerOfficeId(UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901"))
            .createdAt(OffsetDateTime.now())
            .createdBy("Random User")
            .modifiedAt(OffsetDateTime.now())
            .modifiedBy("Random User");
    if (customizer != null) {
      customizer.accept(builder);
    }
    return builder;
  }
}
