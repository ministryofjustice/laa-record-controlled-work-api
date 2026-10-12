package uk.gov.justice.laa.rcw.logging;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Redacts sensitive structured fields without modifying their original values. */
public final class LogRedactor {

  private static final String REDACTED = "[REDACTED]";
  private static final Set<String> SENSITIVE_FIELDS =
      Set.of(
          "accesstoken",
          "address",
          "apikey",
          "authorization",
          "cookie",
          "dateofbirth",
          "firstname",
          "idtoken",
          "lastname",
          "ninumber",
          "password",
          "refreshtoken",
          "secret",
          "token");

  private LogRedactor() {}

  /**
   * Returns a redacted copy of a structured value, including nested maps, iterables and arrays.
   *
   * @param key field name, optionally using dotted notation
   * @param value structured field value
   * @return redacted value
   */
  public static Object redact(String key, Object value) {
    String field = key.substring(key.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    if (SENSITIVE_FIELDS.contains(field)) {
      return REDACTED;
    }
    if (value instanceof Map<?, ?> fields) {
      Map<Object, Object> redacted = new LinkedHashMap<>();
      fields.forEach(
          (nestedKey, nestedValue) ->
              redacted.put(nestedKey, redact(String.valueOf(nestedKey), nestedValue)));
      return redacted;
    }
    if (value instanceof Iterable<?> items) {
      ArrayList<Object> redacted = new ArrayList<>();
      items.forEach(item -> redacted.add(redact("", item)));
      return redacted;
    }
    if (value != null && value.getClass().isArray()) {
      ArrayList<Object> redacted = new ArrayList<>();
      for (int index = 0; index < Array.getLength(value); index++) {
        redacted.add(redact("", Array.get(value, index)));
      }
      return redacted;
    }
    return value;
  }
}
