package uk.gov.justice.laa.rcw.generator;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Consumer;
import uk.gov.justice.laa.rcw.model.Application;
import uk.gov.justice.laa.rcw.model.ApplicationStatus;
import uk.gov.justice.laa.rcw.model.ClientDetails;
import uk.gov.justice.laa.rcw.model.Declaration;
import uk.gov.justice.laa.rcw.model.Evidence;

/** Generator for an Application model for tests. */
public class ApplicationGenerator {

  public static Application create(Consumer<Application.Builder> customizer) {
    return createApplication(customizer).build();
  }

  private static Application.Builder createApplication(Consumer<Application.Builder> customizer) {

    ClientDetails clientDetails = ClientDetailsGenerator.createWithName(null);
    Declaration declaration = DeclarationGenerator.create(null);
    Evidence evidence = EvidenceGenerator.create(null);

    var builder =
        Application.builder()
            .id(UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901"))
            .individualLegalAidNumber(UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901"))
            .providerFirmId(UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901"))
            .providerOfficeId(UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901"))
            .clientDetails(clientDetails)
            .applicationStatus(ApplicationStatus.DRAFT)
            .declaration(declaration)
            .evidence(evidence)
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
