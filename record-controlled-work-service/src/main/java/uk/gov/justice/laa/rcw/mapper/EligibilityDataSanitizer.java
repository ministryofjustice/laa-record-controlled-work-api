package uk.gov.justice.laa.rcw.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.openapitools.jackson.nullable.JsonNullableModule;

final class EligibilityDataSanitizer {

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().registerModule(new JsonNullableModule());

  private EligibilityDataSanitizer() {}

  static Object omitNullProperties(Object value) {
    JsonNode tree = OBJECT_MAPPER.valueToTree(value);
    removeNullProperties(tree);
    return OBJECT_MAPPER.convertValue(tree, Object.class);
  }

  private static void removeNullProperties(JsonNode value) {
    if (value.isObject()) {
      ObjectNode object = (ObjectNode) value;
      List<String> nullProperties = new ArrayList<>();
      Iterator<Map.Entry<String, JsonNode>> fields = object.fields();

      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        if (field.getValue().isNull()) {
          nullProperties.add(field.getKey());
        } else {
          removeNullProperties(field.getValue());
        }
      }

      nullProperties.forEach(object::remove);
    } else if (value.isArray()) {
      value.forEach(EligibilityDataSanitizer::removeNullProperties);
    }
  }
}
