package uk.gov.justice.laa.rcw.generator;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Consumer;
import uk.gov.justice.laa.rcw.model.ClientDeclarationStatus;
import uk.gov.justice.laa.rcw.model.Declaration;

/** Generator for setting for an Declaration model for tests. */
public class DeclarationGenerator {

  public static Declaration create(Consumer<Declaration.Builder> customizer) {
    return createDeclaration(customizer).build();
  }

  private static Declaration.Builder createDeclaration(Consumer<Declaration.Builder> customizer) {
    var builder =
        Declaration.builder()
            .id(UUID.randomUUID())
            .clientDeclarationStatus(ClientDeclarationStatus.DRAFT)
            .declarationConfirmation(false)
            .createdAt(OffsetDateTime.now())
            .modifiedAt(OffsetDateTime.now())
            .createdBy("Joe Bloggs")
            .modifiedBy("James Bloggs");
    if (customizer != null) {
      customizer.accept(builder);
    }
    return builder;
  }
}
