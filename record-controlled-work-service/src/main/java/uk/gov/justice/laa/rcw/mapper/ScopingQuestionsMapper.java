package uk.gov.justice.laa.rcw.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.Map;
import org.mapstruct.Mapper;
import org.mapstruct.Named;
import uk.gov.justice.laa.rcw.model.ApplicationScopingQuestions;
import uk.gov.justice.laa.rcw.model.CreateScopingQuestions;

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

  /** Serializes the RCW model to a map without null-valued answers. */
  default Map<String, Object> toDatastoreScopingQuestions(CreateScopingQuestions questions) {
    if (questions == null) {
      return Map.of();
    }
    return jsonMapper().convertValue(questions, new TypeReference<>() {});
  }

  /** Converts editable scoping answers into the datastore map, retaining explicit null answers. */
  @Named("toDatastoreScopingQuestionsFromDetails")
  default Map<String, Object> toDatastoreScopingQuestionsFromDetails(
      CreateScopingQuestions questions) {
    if (questions == null) {
      return Map.of();
    }
    return ScopingQuestionsJsonMapper.EDIT_INSTANCE.convertValue(
        questions, new TypeReference<>() {});
  }

  private static JsonMapper jsonMapper() {
    return ScopingQuestionsJsonMapper.INSTANCE;
  }
}
