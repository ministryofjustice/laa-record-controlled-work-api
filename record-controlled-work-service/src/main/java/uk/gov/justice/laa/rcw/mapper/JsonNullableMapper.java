package uk.gov.justice.laa.rcw.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Named;
import org.openapitools.jackson.nullable.JsonNullable;

/** Maps values into datastore nullable wrappers. */
@Mapper(componentModel = "spring")
public interface JsonNullableMapper {

  /** Wraps mapped values for existing means-data mappings. */
  default <T> JsonNullable<T> toJsonNullable(T value) {
    return wrap(value);
  }

  /** Wraps an editable value as present, including explicit null clears. */
  @Named("toPresentJsonNullable")
  default <T> JsonNullable<T> toPresentJsonNullable(T value) {
    return wrap(value);
  }

  private <T> JsonNullable<T> wrap(T value) {
    return JsonNullable.of(value);
  }
}
