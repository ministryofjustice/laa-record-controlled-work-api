package uk.gov.justice.laa.rcw.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Locale;
import java.util.regex.Pattern;

final class ApplicationRequestBodyNormalizer {

  private static final Pattern FORMATTING_CHARACTERS = Pattern.compile("[^A-Za-z0-9]");

  private ApplicationRequestBodyNormalizer() {}

  static void normalize(JsonNode request) {
    if (!(request instanceof ObjectNode root)) {
      return;
    }

    JsonNode clientDetails = root.get("clientDetails");
    if (!(clientDetails instanceof ObjectNode client)) {
      return;
    }

    normalizeField(client, "niNumber");
    JsonNode address = client.get("address");
    if (address instanceof ObjectNode addressObject) {
      JsonNode country = addressObject.get("country");
      if (country != null && country.isTextual() && "GB".equalsIgnoreCase(country.textValue())) {
        normalizeField(addressObject, "postCode");
      }
    }
  }

  private static void normalizeField(ObjectNode object, String fieldName) {
    JsonNode field = object.get(fieldName);
    if (field != null && field.isTextual()) {
      String normalized =
          FORMATTING_CHARACTERS.matcher(field.textValue()).replaceAll("").toUpperCase(Locale.ROOT);
      object.put(fieldName, normalized);
    }
  }
}
