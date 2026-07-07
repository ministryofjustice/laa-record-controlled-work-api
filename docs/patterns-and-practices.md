# Record Controlled Work Java practices 

This document is to assist onboarding developers into the general practices implemented as part of this project

When in doubt refer to [Data Access](https://github.com/ministryofjustice/laa-data-access-api), [Info and advice data store](https://github.com/ministryofjustice/laa-info-and-advice-datastore), [Data claims[(https://github.com/ministryofjustice/laa-data-claims-api) or [Submit a bulk claim](https://github.com/ministryofjustice/laa-submit-a-bulk-claim) practices to get consistency across the java development teams.

As a general direction favour aspect orientated programming paradigms given by Lombok, this greatly reduces the amount of code that we need to write, as an example using the `@RequiredArgsConstructor` to create a constructor for a data object rather than writing a full constructor this make data models and code in general much easier to read.

When defining contracts outbound or inbound to the service make use of the OpenAPI specification generator. For inbound models this is located in `record-controlled-work-api/open-api-specification.yml`.

## Project Structure

Our project should follow the pattern of controller -> service -> data layer.
Controllers should exist in `record-controlled-work-service/src/main/java/uk/gov/justice/laa/rcw/controller`.
Services should exist in the `record-controlled-work-service/src/main/java/uk/gov/justice/laa/rcw/service`.
Data layer for this project is yet to be defined but will be generated HttpClient objects.

### Data mapping
Any mapping between models should be created in `record-controlled-work-service/src/main/java/uk/gov/justice/laa/rcw/mapper` using MapStruct which will automatically map properties of the same name and provides mechanisms to map to different objects, and in the last case scenario provide a direction implementation of the mapping behaviour.

## Testing

Unit/Integration testing is done via JUnit, use parametrised tests where possible to reduce code. TestContainer can be used for any common outside services.


## Libraries Used

- [Spring Boot Actuator](https://docs.spring.io/spring-boot/reference/actuator/index.html) - used to provide various endpoints to help monitor the application, such as view application health and information.
- [Spring Boot Web](https://docs.spring.io/spring-boot/reference/web/index.html) - used to provide features for building the REST API implementation.
- [Spring Data JPA](https://docs.spring.io/spring-data/jpa/reference/jpa.html) - used to simplify database access and interaction, by providing an abstraction over persistence technologies, to help reduce boilerplate code.
- [Springdoc OpenAPI](https://springdoc.org/) - used to generate OpenAPI documentation. It automatically generates Swagger UI, JSON documentation based on your Spring REST APIs.
- used to capture application exception events at runtime, which can be monitored via the Sentry UI.
- [Lombok](https://projectlombok.org/) - used to help to reduce boilerplate Java code by automatically generating common
  methods like getters, setters, constructors etc. at compile-time using annotations.
- [MapStruct](https://mapstruct.org/) - used for object mapping, specifically for converting between different Java object types, such as Data Transfer Objects (DTOs)
  and Entity objects. It generates mapping code at compile code.
- [H2](https://www.h2database.com/html/main.html) - used to provide an example database and should not be used in production.
- [Sentry for Java SDK](https://docs.sentry.io/platforms/java/) - used to capture application exception events at runtime, which can be monitored via the Sentry UI.