package uk.gov.justice.laa.rcw.generator;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Consumer;
import uk.gov.justice.laa.rcw.model.Evidence;
import uk.gov.justice.laa.rcw.model.EvidenceStatus;

/** Generator for setting for an Evidence model for tests. */
public class EvidenceGenerator {

  public static Evidence create(Consumer<Evidence.Builder> customizer) {
    return createEvidence(customizer).build();
  }

  private static Evidence.Builder createEvidence(Consumer<Evidence.Builder> customizer) {
    var builder =
        Evidence.builder()
            .id(UUID.randomUUID())
            .createdAt(OffsetDateTime.now())
            .modifiedAt(OffsetDateTime.now())
            .evidenceStatus(EvidenceStatus.DRAFT)
            .payeIncomeEvidence(false)
            .otherIncomeEvidence(false)
            .housingCostsEvidence(false)
            .capitalEvidence(false)
            .createdBy("Joe Bloggs")
            .modifiedBy("James Bloggs");
    if (customizer != null) {
      customizer.accept(builder);
    }
    return builder;
  }
}
