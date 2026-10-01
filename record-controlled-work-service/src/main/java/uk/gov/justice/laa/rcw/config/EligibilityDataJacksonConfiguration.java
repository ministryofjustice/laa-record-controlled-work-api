package uk.gov.justice.laa.rcw.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.Map;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.rcw.model.EligibilityData;
import uk.gov.justice.laa.rcw.model.EligibilityDataBankAccount;
import uk.gov.justice.laa.rcw.model.EligibilityDataBenefit;
import uk.gov.justice.laa.rcw.model.EligibilityDataDependantIncome;
import uk.gov.justice.laa.rcw.model.EligibilityDataEarlyResult;
import uk.gov.justice.laa.rcw.model.EligibilityDataIncome;
import uk.gov.justice.laa.rcw.model.EligibilityDataProperty;
import uk.gov.justice.laa.rcw.model.EligibilityDataVehicle;

/** Configures null omission for eligibility data JSON. */
@Configuration(proxyBeanMethods = false)
public class EligibilityDataJacksonConfiguration {

  @Bean
  JsonMapperBuilderCustomizer eligibilityDataJsonMapperCustomizer() {
    return builder -> {
      builder.addMixIn(EligibilityData.class, EligibilityDataMixin.class);
      builder.addMixIn(EligibilityDataBankAccount.class, NonNullPropertiesMixin.class);
      builder.addMixIn(EligibilityDataBenefit.class, NonNullPropertiesMixin.class);
      builder.addMixIn(EligibilityDataDependantIncome.class, NonNullPropertiesMixin.class);
      builder.addMixIn(EligibilityDataEarlyResult.class, NonNullPropertiesMixin.class);
      builder.addMixIn(EligibilityDataIncome.class, NonNullPropertiesMixin.class);
      builder.addMixIn(EligibilityDataProperty.class, NonNullPropertiesMixin.class);
      builder.addMixIn(EligibilityDataVehicle.class, NonNullPropertiesMixin.class);
    };
  }

  @JsonInclude(Include.NON_NULL)
  abstract static class NonNullPropertiesMixin {}

  @JsonInclude(Include.NON_NULL)
  abstract static class EligibilityDataMixin {

    @JsonInclude(content = Include.NON_NULL)
    abstract Map<String, Object> getApiResponse();

    @JsonInclude(content = Include.NON_NULL)
    abstract Map<String, Boolean> getFeatureFlags();

    @JsonInclude(content = Include.NON_NULL)
    abstract Map<String, Object> getPending();
  }
}
