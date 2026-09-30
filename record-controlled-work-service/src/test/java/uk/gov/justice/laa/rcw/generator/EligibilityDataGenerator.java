package uk.gov.justice.laa.rcw.generator;

import java.util.function.Consumer;
import uk.gov.justice.laa.rcw.model.EligibilityData;

/** Generator for an EligibilityData model for tests. */
public class EligibilityDataGenerator {

  public static EligibilityData create(Consumer<EligibilityData.Builder> customizer) {
    return createEligibilityData(customizer).build();
  }

  private static EligibilityData.Builder createEligibilityData(
      Consumer<EligibilityData.Builder> customizer) {
    var builder = EligibilityData.builder().levelOfHelp("controlled");
    if (customizer != null) {
      customizer.accept(builder);
    }
    return builder;
  }
}
