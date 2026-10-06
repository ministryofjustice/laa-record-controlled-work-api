package uk.gov.justice.laa.rcw.config.schemas;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.dialect.Dialects;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configures schema validation for application write requests. */
@Configuration(proxyBeanMethods = false)
public class ApplicationRequestSchemaConfiguration {

  private static final String SPECIFICATION_LOCATION = "classpath:open-api-specification.yml";
  private static final String VALIDATION_SCHEMA_DIRECTORY =
      "classpath:open-api-specifications/validation/";

  @Bean
  Clock applicationRequestValidationClock() {
    return Clock.systemUTC();
  }

  @Bean
  @ApplicationRequestSchemaQualifier(ApplicationRequestSchema.UPDATE_APPLICATION_DETAILS)
  Schema updateApplicationDetailsSchema() {
    return schemaFor(ApplicationRequestSchema.UPDATE_APPLICATION_DETAILS);
  }

  @Bean
  @ApplicationRequestSchemaQualifier(ApplicationRequestSchema.CREATE_APPLICATION)
  Schema createApplicationSchema() {
    return schemaFor(ApplicationRequestSchema.CREATE_APPLICATION);
  }

  private Schema schemaFor(ApplicationRequestSchema requestSchema) {
    SchemaRegistry registry =
        SchemaRegistry.withDialect(
            Dialects.getOpenApi30(),
            builder -> {
              builder.schemaLoader(
                  loader ->
                      loader.allow(
                          location ->
                              SPECIFICATION_LOCATION.equals(location.toString())
                                  || ("classpath".equals(location.getScheme())
                                      && location
                                          .toString()
                                          .startsWith(VALIDATION_SCHEMA_DIRECTORY))));
              builder.schemaRegistryConfig(
                  SchemaRegistryConfig.builder()
                      .formatAssertionsEnabled(true)
                      .typeLoose(false)
                      .failFast(true)
                      .build());
            });
    String schemaLocation =
        SPECIFICATION_LOCATION + "#/components/schemas/" + requestSchema.componentName();
    Schema schema = registry.getSchema(SchemaLocation.of(schemaLocation));
    schema.initializeValidators();
    return schema;
  }
}
