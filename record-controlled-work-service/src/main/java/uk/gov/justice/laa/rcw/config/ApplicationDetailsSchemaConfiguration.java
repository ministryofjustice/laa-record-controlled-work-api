package uk.gov.justice.laa.rcw.config;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.dialect.Dialects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configures schema validation for editable application details. */
@Configuration(proxyBeanMethods = false)
public class ApplicationDetailsSchemaConfiguration {

  private static final String SPECIFICATION_LOCATION = "classpath:open-api-specification.yml";
  private static final String REQUEST_SCHEMA_LOCATION =
      SPECIFICATION_LOCATION + "#/components/schemas/UpdateApplicationDetailsRequestBody";

  @Bean
  Schema applicationDetailsSchema() {
    SchemaRegistry registry =
        SchemaRegistry.withDialect(
            Dialects.getOpenApi30(),
            builder -> {
              builder.schemaLoader(
                  loader ->
                      loader.allow(location -> SPECIFICATION_LOCATION.equals(location.toString())));
              builder.schemaRegistryConfig(
                  SchemaRegistryConfig.builder()
                      .formatAssertionsEnabled(true)
                      .typeLoose(false)
                      .failFast(true)
                      .build());
            });
    Schema schema = registry.getSchema(SchemaLocation.of(REQUEST_SCHEMA_LOCATION));
    schema.initializeValidators();
    return schema;
  }
}
