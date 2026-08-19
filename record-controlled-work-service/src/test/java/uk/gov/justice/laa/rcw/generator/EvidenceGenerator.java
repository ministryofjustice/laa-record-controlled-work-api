package uk.gov.justice.laa.rcw.generator;

import java.util.Map;
import java.util.function.Consumer;
import uk.gov.justice.laa.rcw.model.Evidence;

/** Generator for setting for an Evidence model for tests. */
public class EvidenceGenerator {

  public static Evidence create(Consumer<Evidence.Builder> customizer) {
    return createEvidence(customizer).build();
  }

  private static Evidence.Builder createEvidence(Consumer<Evidence.Builder> customizer) {
    var builder =
        Evidence.builder()
            .evidenceExemptionCode("adviceOverPhone")
            .evidenceExemptionReason("Client was advised over the phone")
            .incomeEvidenceChecklist(Map.of("payslips", true))
            .expenditureCapitalEvidenceChecklist(Map.of("bankStatements", true));
    if (customizer != null) {
      customizer.accept(builder);
    }
    return builder;
  }
}
