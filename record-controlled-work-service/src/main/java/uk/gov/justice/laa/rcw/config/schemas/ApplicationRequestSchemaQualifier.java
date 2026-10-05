package uk.gov.justice.laa.rcw.config.schemas;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.beans.factory.annotation.Qualifier;

/** Qualifies a request schema bean by its OpenAPI operation schema. */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE})
public @interface ApplicationRequestSchemaQualifier {
  /** Identifies the OpenAPI request schema used to qualify the bean. */
  ApplicationRequestSchema value();
}
