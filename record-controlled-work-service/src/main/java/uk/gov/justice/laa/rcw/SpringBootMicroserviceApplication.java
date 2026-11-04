package uk.gov.justice.laa.rcw;

import org.openapitools.configuration.EnumConverterConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/** Entry point for the Spring Boot microservice application. */
@SpringBootApplication
// Generated outside the scanned package; binds query enums by their spec values.
@Import(EnumConverterConfiguration.class)
public class SpringBootMicroserviceApplication {

  /**
   * The application main method.
   *
   * @param args the application arguments.
   */
  public static void main(String[] args) {
    SpringApplication.run(SpringBootMicroserviceApplication.class, args);
  }
}
