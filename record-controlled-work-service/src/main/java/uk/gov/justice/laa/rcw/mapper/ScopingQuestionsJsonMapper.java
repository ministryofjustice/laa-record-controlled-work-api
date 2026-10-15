package uk.gov.justice.laa.rcw.mapper;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

final class ScopingQuestionsJsonMapper {

  static final JsonMapper INSTANCE = create(JsonInclude.Include.NON_NULL);
  static final JsonMapper EDIT_INSTANCE = create(JsonInclude.Include.ALWAYS);

  private static JsonMapper create(JsonInclude.Include inclusion) {
    return JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
        .defaultPropertyInclusion(JsonInclude.Value.construct(inclusion, inclusion))
        .build();
  }

  private ScopingQuestionsJsonMapper() {}
}
