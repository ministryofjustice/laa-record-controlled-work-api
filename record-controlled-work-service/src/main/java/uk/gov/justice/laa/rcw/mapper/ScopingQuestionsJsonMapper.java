package uk.gov.justice.laa.rcw.mapper;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

final class ScopingQuestionsJsonMapper {

  static final JsonMapper INSTANCE =
      JsonMapper.builder()
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
          .defaultPropertyInclusion(
              JsonInclude.Value.construct(
                  JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
          .build();

  private ScopingQuestionsJsonMapper() {}
}
