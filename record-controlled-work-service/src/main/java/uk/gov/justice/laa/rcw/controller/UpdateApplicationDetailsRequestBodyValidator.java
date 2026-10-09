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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import uk.gov.justice.laa.rcw.config.schemas.ApplicationRequestSchema;
import uk.gov.justice.laa.rcw.config.schemas.ApplicationRequestSchemaQualifier;
import uk.gov.justice.laa.rcw.exception.ApplicationRequestTooLargeException;
import uk.gov.justice.laa.rcw.exception.ApplicationRequestValidationException;
import uk.gov.justice.laa.rcw.model.PriorLegalAid;
import uk.gov.justice.laa.rcw.model.UpdateApplicationDetailsRequestBody;
import uk.gov.justice.laa.rcw.model.UpdateClientDetailsRequestBody;

/** Validates the complete JSON details snapshot before generated models lose key presence. */
@ControllerAdvice(assignableTypes = ApplicationController.class)
public class UpdateApplicationDetailsRequestBodyValidator extends RequestBodyAdviceAdapter {

  private static final ObjectMapper STRICT_MAPPER =
      new ObjectMapper(
          JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());

  private final int maxBodySizeBytes;
  private final Schema updateApplicationDetailsSchema;

  /** Creates the details request validator with a configured byte limit. */
  public UpdateApplicationDetailsRequestBodyValidator(
      @Value("${laa.request.body.max-size-bytes}") int maxBodySizeBytes,
      @ApplicationRequestSchemaQualifier(ApplicationRequestSchema.UPDATE_APPLICATION_DETAILS)
          Schema updateApplicationDetailsSchema) {
    if (maxBodySizeBytes < 1 || maxBodySizeBytes == Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Request body size limit must be a positive integer");
    }
    this.maxBodySizeBytes = maxBodySizeBytes;
    this.updateApplicationDetailsSchema = updateApplicationDetailsSchema;
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
    JsonNode request = STRICT_MAPPER.readTree(body);
    ApplicationRequestBodyNormalizer.normalize(request);
    validateSchema(request);
    byte[] normalizedBody = STRICT_MAPPER.writeValueAsBytes(request);
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

  @Override
  public Object afterBodyRead(
      Object body,
      HttpInputMessage inputMessage,
      MethodParameter parameter,
      java.lang.reflect.Type targetType,
      Class<? extends HttpMessageConverter<?>> converterType) {
    validateBusinessRules((UpdateApplicationDetailsRequestBody) body);
    return body;
  }

  private void validateSchema(JsonNode request) {
    JsonNode body = request == null ? NullNode.getInstance() : request;
    if (!updateApplicationDetailsSchema.validate(body).isEmpty()) {
      invalid();
    }
  }

  private void validateBusinessRules(UpdateApplicationDetailsRequestBody request) {
    boolean legalAidLast6Months = request.getLegalAidLast6Months();
    boolean sameMatter = request.getPriorLegalAid() == PriorLegalAid.YES_SAME_MATTER;
    String reason = request.getReasonForReapplication();
    if (legalAidLast6Months && !sameMatter) {
      invalid();
    }
    if (sameMatter && legalAidLast6Months) {
      if (reason == null || reason.isBlank()) {
        invalid();
      }
    } else if (reason != null) {
      invalid();
    }

    UpdateClientDetailsRequestBody client = request.getClientDetails();
    boolean hasFixedAddress = client.getHasFixedAddress();
    if (hasFixedAddress == (client.getAddress() == null)) {
      invalid();
    }
  }

  private void invalid() {
    throw new ApplicationRequestValidationException("INVALID_APPLICATION_DETAILS");
  }
}
