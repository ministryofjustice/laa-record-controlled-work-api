package uk.gov.justice.laa.rcw.controller;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.networknt.schema.Schema;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import uk.gov.justice.laa.rcw.exception.ApplicationRequestTooLargeException;
import uk.gov.justice.laa.rcw.exception.ApplicationRequestValidationException;
import uk.gov.justice.laa.rcw.model.UpdateApplicationDetailsRequestBody;

/** Validates the complete JSON details snapshot before generated models lose key presence. */
@ControllerAdvice(assignableTypes = ApplicationController.class)
public class ApplicationDetailsRequestBodyValidator extends RequestBodyAdviceAdapter {

  private static final ObjectMapper STRICT_MAPPER =
      new ObjectMapper(
          JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());

  private final int maxBodySizeBytes;
  private final Schema applicationDetailsSchema;

  /** Creates the details request validator with a configured byte limit. */
  public ApplicationDetailsRequestBodyValidator(
      @Value("${laa.request.body.max-size-bytes}") int maxBodySizeBytes,
      Schema applicationDetailsSchema) {
    if (maxBodySizeBytes < 1 || maxBodySizeBytes == Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Request body size limit must be a positive integer");
    }
    this.maxBodySizeBytes = maxBodySizeBytes;
    this.applicationDetailsSchema = applicationDetailsSchema;
  }

  @Override
  public boolean supports(
      MethodParameter methodParameter,
      java.lang.reflect.Type targetType,
      Class<? extends HttpMessageConverter<?>> converterType) {
    return methodParameter.getMethod() != null
        && methodParameter.getMethod().getName().equals("updateApplicationDetails")
        && UpdateApplicationDetailsRequestBody.class.isAssignableFrom(
            methodParameter.getParameterType());
  }

  @Override
  public HttpInputMessage beforeBodyRead(
      HttpInputMessage inputMessage,
      MethodParameter parameter,
      java.lang.reflect.Type targetType,
      Class<? extends HttpMessageConverter<?>> converterType)
      throws IOException {
    byte[] body = inputMessage.getBody().readNBytes(maxBodySizeBytes + 1);
    if (body.length > maxBodySizeBytes) {
      throw new ApplicationRequestTooLargeException();
    }
    validate(STRICT_MAPPER.readTree(body));
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

  private void validate(JsonNode request) {
    JsonNode body = request == null ? NullNode.getInstance() : request;
    if (!applicationDetailsSchema.validate(body).isEmpty()) {
      invalid();
    }

    String priorLegalAid = request.path("priorLegalAid").textValue();
    boolean legalAidLast6Months = request.path("legalAidLast6Months").booleanValue();
    JsonNode reason = request.path("reasonForReapplication");
    boolean sameMatter = "yesSameMatter".equals(priorLegalAid);
    if (legalAidLast6Months && !sameMatter) {
      invalid();
    }
    if (sameMatter && legalAidLast6Months) {
      if (!reason.isTextual() || reason.textValue().isBlank()) {
        invalid();
      }
    } else if (!reason.isNull()) {
      invalid();
    }

    JsonNode client = request.path("clientDetails");
    boolean hasFixedAddress = client.path("hasFixedAddress").booleanValue();
    if (hasFixedAddress == client.path("address").isNull()) {
      invalid();
    }
  }

  private void invalid() {
    throw new ApplicationRequestValidationException("INVALID_APPLICATION_DETAILS");
  }
}
