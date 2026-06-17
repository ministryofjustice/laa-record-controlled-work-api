package uk.gov.justice.laa.rcw.generator;

import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Consumer;
import uk.gov.justice.laa.rcw.model.Application;

/** Generator for an Application model for tests. */
public class ApplicationGenerator {

  public static Application create(Consumer<Application.Builder> customizer) {
    return createApplication(customizer).build();
  }

  private static Application.Builder createApplication(Consumer<Application.Builder> customizer) {
    var builder =
        Application.builder()
            .id(UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890"))
            .name("Random Name")
            .dob(LocalDate.of(1985, 3, 14))
            .address("Random Address");
    if (customizer != null) {
      customizer.accept(builder);
    }
    return builder;
  }
}
