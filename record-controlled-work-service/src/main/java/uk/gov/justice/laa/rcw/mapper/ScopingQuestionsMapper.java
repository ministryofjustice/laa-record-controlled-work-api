package uk.gov.justice.laa.rcw.mapper;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.Map;
import org.mapstruct.Mapper;
import uk.gov.justice.laa.rcw.model.ScopingQuestions;

/** Maps scoping questions between the RCW model and the datastore's loose JSON. */
@Mapper(componentModel = "spring")
public interface ScopingQuestionsMapper {

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

  private static JsonMapper jsonMapper() {
    return JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
        .defaultPropertyInclusion(
            JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
        .build();
  }
}
