package uk.gov.justice.laa.rcw.controller;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Schema;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import uk.gov.justice.laa.rcw.config.schemas.ApplicationRequestSchema;
import uk.gov.justice.laa.rcw.config.schemas.ApplicationRequestSchemaQualifier;
import uk.gov.justice.laa.rcw.exception.ApplicationRequestTooLargeException;
import uk.gov.justice.laa.rcw.exception.ApplicationRequestValidationException;
import uk.gov.justice.laa.rcw.model.CreateApplicationRequestBody;
import uk.gov.justice.laa.rcw.validation.CreateApplicationDatePolicy;

/** Validates create request JSON against the OpenAPI contract before model binding. */
@ControllerAdvice(assignableTypes = ApplicationController.class)
public class CreateApplicationRequestBodyValidator extends RequestBodyAdviceAdapter {

  private final int maxBodySizeBytes;
  private final ObjectMapper strictMapper;
  private final Schema createApplicationSchema;
  private final Clock clock;

  /** Creates the validator using the configured body limit and create request schema. */
  public CreateApplicationRequestBodyValidator(
      @Value("${laa.request.body.max-size-bytes}") int maxBodySizeBytes,
      @Value("${spring.jackson.factory.constraints.read.max-nesting-depth}") int maxNestingDepth,
      @Value("${spring.jackson.factory.constraints.read.max-string-length}") int maxStringLength,
      @Value("${spring.jackson.factory.constraints.read.max-document-length}")
          long maxDocumentLength,
      @ApplicationRequestSchemaQualifier(ApplicationRequestSchema.CREATE_APPLICATION)
          Schema createApplicationSchema,
      Clock clock) {
    if (maxBodySizeBytes < 1 || maxBodySizeBytes == Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Request body size limit must be a positive integer");
    }
    this.maxBodySizeBytes = maxBodySizeBytes;
    this.strictMapper =
        new ObjectMapper(
            JsonFactory.builder()
                .streamReadConstraints(
                    StreamReadConstraints.builder()
                        .maxNestingDepth(maxNestingDepth)
                        .maxStringLength(maxStringLength)
                        .maxDocumentLength(maxDocumentLength)
                        .build())
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build());
    this.createApplicationSchema = createApplicationSchema;
    this.clock = clock;
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
    if (inputMessage.getHeaders().getContentLength() > maxBodySizeBytes) {
      throw new ApplicationRequestTooLargeException();
    }
    byte[] body = inputMessage.getBody().readNBytes(maxBodySizeBytes + 1);
    if (body.length > maxBodySizeBytes) {
      throw new ApplicationRequestTooLargeException();
    }
    byte[] normalizedBody = normalizeAndValidate(body);
    HttpHeaders headers = new HttpHeaders();
    headers.putAll(inputMessage.getHeaders());
    headers.setContentLength(normalizedBody.length);
    return new HttpInputMessage() {
      @Override
      public java.io.InputStream getBody() {
        return new ByteArrayInputStream(normalizedBody);
      }

      @Override
      public org.springframework.http.HttpHeaders getHeaders() {
        return headers;
      }
    };
  }

  private byte[] normalizeAndValidate(byte[] body) {
    try {
      JsonNode request = strictMapper.readTree(body);
      ApplicationRequestBodyNormalizer.normalize(request);
      if (request == null || !createApplicationSchema.validate(request).isEmpty()) {
        throw invalid();
      }
      if (!CreateApplicationDatePolicy.isValid(
          request.path("clientDetails").path("dateOfBirth").asText(null), clock)) {
        throw invalid();
      }
      return strictMapper.writeValueAsBytes(request);
    } catch (IOException exception) {
      throw invalid();
    }
  }

  private ApplicationRequestValidationException invalid() {
    return new ApplicationRequestValidationException("INVALID_APPLICATION");
  }
}
