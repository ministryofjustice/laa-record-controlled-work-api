package uk.gov.justice.laa.rcw.controller;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import uk.gov.justice.laa.rcw.exception.ApplicationRequestValidationException;
import uk.gov.justice.laa.rcw.model.UpdateApplicationDetailsRequestBody;

/** Validates the complete JSON details snapshot before generated models lose key presence. */
@ControllerAdvice(assignableTypes = ApplicationController.class)
public class ApplicationDetailsRequestBodyAdvice extends RequestBodyAdviceAdapter {

  private static final ObjectMapper STRICT_MAPPER =
      new ObjectMapper(
          JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
  private static final Pattern NATIONAL_INSURANCE_NUMBER =
      Pattern.compile(
          "^(?!BG|GB|KN|NK|NT|TN|ZZ)[A-CEGHJ-PR-TW-Z][A-CEGHJ-NPR-TW-Z][0-9]{6}[ABCD]$");
  private static final Set<String> REQUEST_PROPERTIES =
      Set.of(
          "priorLegalAid",
          "legalAidLast6Months",
          "reasonForReapplication",
          "ecfFlag",
          "clientDetails");
  private static final Set<String> CLIENT_PROPERTIES =
      Set.of("firstName", "lastName", "dateOfBirth", "niNumber", "hasFixedAddress", "address");
  private static final Set<String> ADDRESS_PROPERTIES =
      Set.of(
          "addressLine1",
          "addressLine2",
          "addressLine3",
          "addressLine4",
          "townOrCity",
          "postCode",
          "county",
          "country");

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
    byte[] body = StreamUtils.copyToByteArray(inputMessage.getBody());
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
    JsonNode root = requireObject(request);
    requireProperties(root, REQUEST_PROPERTIES);
    rejectUnknownProperties(root, REQUEST_PROPERTIES);
    requireText(root.get("priorLegalAid"));
    String priorLegalAid = root.get("priorLegalAid").textValue();
    if (!Set.of("no", "yesDifferentMatter", "yesSameMatter").contains(priorLegalAid)) {
      invalid();
    }
    boolean legalAidLast6Months = requireBoolean(root.get("legalAidLast6Months"));
    requireBoolean(root.get("ecfFlag"));
    validateReason(root.get("reasonForReapplication"), priorLegalAid, legalAidLast6Months);
    validateClient(root.get("clientDetails"));
  }

  private void validateReason(JsonNode reason, String priorLegalAid, boolean last6Months) {
    boolean sameMatter = "yesSameMatter".equals(priorLegalAid);
    if (last6Months && !sameMatter) {
      invalid();
    }
    if (sameMatter && last6Months) {
      if (!reason.isTextual() || reason.textValue().isBlank()) {
        invalid();
      }
    } else if (!reason.isNull()) {
      invalid();
    }
  }

  private void validateClient(JsonNode clientNode) {
    JsonNode client = requireObject(clientNode);
    requireProperties(client, CLIENT_PROPERTIES);
    rejectUnknownProperties(client, CLIENT_PROPERTIES);
    requireText(client.get("firstName"));
    requireText(client.get("lastName"));
    validateDate(client.get("dateOfBirth"));
    validateNationalInsuranceNumber(client.get("niNumber"));
    boolean hasFixedAddress = requireBoolean(client.get("hasFixedAddress"));
    JsonNode address = client.get("address");
    if (!hasFixedAddress) {
      if (!address.isNull()) {
        invalid();
      }
      return;
    }
    validateAddress(address);
  }

  private void validateDate(JsonNode dateOfBirth) {
    String value = requireText(dateOfBirth);
    try {
      LocalDate.parse(value);
    } catch (DateTimeParseException exception) {
      invalid();
    }
  }

  private void validateNationalInsuranceNumber(JsonNode niNumber) {
    if (!niNumber.isNull()
        && (!niNumber.isTextual()
            || !NATIONAL_INSURANCE_NUMBER.matcher(niNumber.textValue()).matches())) {
      invalid();
    }
  }

  private void validateAddress(JsonNode addressNode) {
    JsonNode address = requireObject(addressNode);
    requireProperties(address, ADDRESS_PROPERTIES);
    rejectUnknownProperties(address, ADDRESS_PROPERTIES);
    requireText(address.get("addressLine1"));
    for (String property :
        Set.of(
            "addressLine2", "addressLine3", "addressLine4", "townOrCity", "postCode", "county")) {
      JsonNode value = address.get(property);
      if (!value.isNull() && !value.isTextual()) {
        invalid();
      }
    }
    String country = requireText(address.get("country"));
    if (country.length() != 2) {
      invalid();
    }
  }

  private JsonNode requireObject(JsonNode value) {
    if (value == null || !value.isObject()) {
      invalid();
    }
    return value;
  }

  private void requireProperties(JsonNode object, Set<String> properties) {
    for (String property : properties) {
      if (!object.has(property)) {
        invalid();
      }
    }
  }

  private void rejectUnknownProperties(JsonNode object, Set<String> allowedProperties) {
    Iterator<String> names = object.fieldNames();
    while (names.hasNext()) {
      if (!allowedProperties.contains(names.next())) {
        invalid();
      }
    }
  }

  private String requireText(JsonNode value) {
    if (value == null || !value.isTextual()) {
      invalid();
    }
    return value.textValue();
  }

  private boolean requireBoolean(JsonNode value) {
    if (value == null || !value.isBoolean()) {
      invalid();
    }
    return value.booleanValue();
  }

  private void invalid() {
    throw new ApplicationRequestValidationException("INVALID_APPLICATION_DETAILS");
  }
}
