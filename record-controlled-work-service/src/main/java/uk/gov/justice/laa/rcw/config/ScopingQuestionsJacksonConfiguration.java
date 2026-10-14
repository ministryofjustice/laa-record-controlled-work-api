package uk.gov.justice.laa.rcw.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.rcw.model.ApplicationScopingQuestions;

/** Omits unanswered optional scoping questions from application response JSON. */
@Configuration(proxyBeanMethods = false)
public class ScopingQuestionsJacksonConfiguration {

  @Bean
  JsonMapperBuilderCustomizer scopingQuestionsJsonMapperCustomizer() {
    return builder ->
        builder.addMixIn(ApplicationScopingQuestions.class, NonNullPropertiesMixin.class);
  }

  @JsonInclude(Include.NON_NULL)
  abstract static class NonNullPropertiesMixin {}
}
