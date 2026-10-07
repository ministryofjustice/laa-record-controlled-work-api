package uk.gov.justice.laa.rcw.datastore.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.lang.reflect.Type;
import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import uk.gov.justice.laa.ia.datastore.client.model.EditApplicationCommand;

@SuppressWarnings({"deprecation", "removal"})
final class EditApplicationCommandHttpMessageConverter extends MappingJackson2HttpMessageConverter {

  EditApplicationCommandHttpMessageConverter() {
    super(editApplicationCommandObjectMapper());
  }

  @Override
  protected boolean supports(Class<?> clazz) {
    return EditApplicationCommand.class.isAssignableFrom(clazz);
  }

  private boolean supports(Type type) {
    Class<?> clazz = ResolvableType.forType(type).resolve();
    return clazz != null && supports(clazz);
  }

  @Override
  public boolean canRead(Class<?> clazz, MediaType mediaType) {
    return supports(clazz) && super.canRead(clazz, mediaType);
  }

  @Override
  public boolean canRead(Type type, Class<?> contextClass, MediaType mediaType) {
    return supports(type) && super.canRead(type, contextClass, mediaType);
  }

  @Override
  public boolean canWrite(Class<?> clazz, MediaType mediaType) {
    return supports(clazz) && super.canWrite(clazz, mediaType);
  }

  @Override
  public boolean canWrite(Type type, Class<?> clazz, MediaType mediaType) {
    return supports(clazz) && super.canWrite(type, clazz, mediaType);
  }

  private static ObjectMapper editApplicationCommandObjectMapper() {
    ObjectMapper objectMapper =
        JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .addModule(new JsonNullableModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    return objectMapper;
  }
}
