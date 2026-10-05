package uk.gov.justice.laa.rcw.controller;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Schema;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import uk.gov.justice.laa.rcw.config.schemas.ApplicationRequestSchema;
import uk.gov.justice.laa.rcw.config.schemas.ApplicationRequestSchemaQualifier;
import uk.gov.justice.laa.rcw.exception.ApplicationRequestValidationException;
import uk.gov.justice.laa.rcw.model.CreateApplicationRequestBody;

/** Validates create request JSON against the OpenAPI contract before model binding. */
@ControllerAdvice(assignableTypes = ApplicationController.class)
public class CreateApplicationRequestBodyValidator extends RequestBodyAdviceAdapter {

  private static final ObjectMapper STRICT_MAPPER =
      new ObjectMapper(
          JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());

  private final Schema createApplicationSchema;

  /** Creates the validator using the create request schema. */
  public CreateApplicationRequestBodyValidator(
      @ApplicationRequestSchemaQualifier(ApplicationRequestSchema.CREATE_APPLICATION)
          Schema createApplicationSchema) {
    this.createApplicationSchema = createApplicationSchema;
  }

  @Override
  public boolean supports(
      MethodParameter methodParameter,
      java.lang.reflect.Type targetType,
      Class<? extends HttpMessageConverter<?>> converterType) {
    return methodParameter.getMethod() != null
        && methodParameter.getMethod().getName().equals("createApplication")
        && CreateApplicationRequestBody.class.isAssignableFrom(methodParameter.getParameterType());
  }

  @Override
  public HttpInputMessage beforeBodyRead(
      HttpInputMessage inputMessage,
      MethodParameter parameter,
      java.lang.reflect.Type targetType,
      Class<? extends HttpMessageConverter<?>> converterType)
      throws IOException {
    byte[] body = inputMessage.getBody().readAllBytes();
    validateSchema(body);
    return new HttpInputMessage() {
      @Override
      public java.io.InputStream getBody() {
        return new ByteArrayInputStream(body);
      }

      @Override
      public org.springframework.http.HttpHeaders getHeaders() {
        return inputMessage.getHeaders();
      }
    };
  }

  private void validateSchema(byte[] body) {
    try {
      JsonNode request = STRICT_MAPPER.readTree(body);
      if (request == null || !createApplicationSchema.validate(request).isEmpty()) {
        invalid();
      }
    } catch (IOException exception) {
      invalid();
    }
  }

  private void invalid() {
    throw new ApplicationRequestValidationException("INVALID_APPLICATION");
  }
}
