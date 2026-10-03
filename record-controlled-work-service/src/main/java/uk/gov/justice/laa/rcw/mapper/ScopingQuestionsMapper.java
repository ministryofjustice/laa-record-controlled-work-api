package uk.gov.justice.laa.rcw.mapper;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.Map;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import uk.gov.justice.laa.rcw.model.ApplicationScopingQuestions;
import uk.gov.justice.laa.rcw.model.ScopingQuestions;
import uk.gov.justice.laa.rcw.model.UpdateApplicationDetailsRequestBody;

/** Maps scoping questions between the RCW model and the datastore's loose JSON. */
@Mapper(componentModel = "spring")
public interface ScopingQuestionsMapper {

  /** Reads nullable legacy answers for the application response. */
  default ApplicationScopingQuestions toApplicationScopingQuestions(Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof Map<?, ?>)) {
      return new ApplicationScopingQuestions();
    }
    return jsonMapper().convertValue(value, ApplicationScopingQuestions.class);
  }

  /** Reads recognised answers without rejecting unknown datastore fields or enum values. */
  default ScopingQuestions toScopingQuestions(Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof Map<?, ?>)) {
      return new ScopingQuestions();
    }
    return jsonMapper().convertValue(value, ScopingQuestions.class);
  }

  /** Serializes the RCW model to a map without null-valued answers. */
  default Map<String, Object> toDatastoreScopingQuestions(ScopingQuestions questions) {
    if (questions == null) {
      return Map.of();
    }
    return jsonMapper().convertValue(questions, new TypeReference<>() {});
  }

  /** Maps scoping answers from the editable application request. */
  @BeanMapping(
      ignoreByDefault = true,
      unmappedSourcePolicy = ReportingPolicy.ERROR,
      ignoreUnmappedSourceProperties = {
        "legalAidLast6Months",
        "reasonForReapplication",
        "ecfFlag",
        "clientDetails"
      })
  @Mapping(target = "priorLegalAid", source = "priorLegalAid")
  ScopingQuestions toScopingQuestionsFromDetails(UpdateApplicationDetailsRequestBody request);

  /** Converts editable scoping answers into the datastore's loose JSON map. */
  @Named("toDatastoreScopingQuestionsFromDetails")
  default Map<String, Object> toDatastoreScopingQuestionsFromDetails(
      UpdateApplicationDetailsRequestBody request) {
    return toDatastoreScopingQuestions(toScopingQuestionsFromDetails(request));
  }

  private static JsonMapper jsonMapper() {
    return JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
        .defaultPropertyInclusion(
            JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
        .build();
  }
}
