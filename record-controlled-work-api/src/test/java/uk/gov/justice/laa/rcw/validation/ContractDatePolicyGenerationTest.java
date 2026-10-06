package uk.gov.justice.laa.rcw.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ContractDatePolicyGenerationTest {

  private static final String SPECIFICATION_PREFIX =
      """
      openapi: 3.0.3
      info:
        title: Invalid date policy fixture
        version: 1.0.0
      paths: {}
      components:
        schemas:
          CreateClientDetailsRequestBody:
            type: object
            properties:
              dateOfBirth:
                type: string
                format: date
      """;

  @TempDir private Path temporaryDirectory;

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidDatePolicyConfigurations")
  void invalidDatePolicyMetadataFailsGeneration(String caseId, String metadata)
      throws IOException {
    Path specification = temporaryDirectory.resolve("invalid-specification.yml");
    Files.writeString(specification, SPECIFICATION_PREFIX + metadata);

    BuildResult result =
        GradleRunner.create()
            .withProjectDir(
                Path.of(System.getProperty("rcw.api.repository.root")).toFile())
            .withArguments(
                ":record-controlled-work-api:generateContractDatePolicy",
                "-PcontractDatePolicySpecification=" + specification,
                "--stacktrace")
            .buildAndFail();

    assertThat(result.getOutput()).contains("Invalid x-rcw-date metadata");
  }

  private static Stream<Arguments> invalidDatePolicyConfigurations() {
    return Stream.of(
        Arguments.of("missing metadata", ""),
        Arguments.of(
            "malformed minimum",
            dateMetadata("1900-02-30", "true", "Europe/London")),
        Arguments.of(
            "unsupported today policy",
            dateMetadata("1900-01-01", "false", "Europe/London")));
  }

  private static String dateMetadata(
      String exclusiveMinimum, String inclusiveToday, String timezone) {
    return "          x-rcw-date:\n"
        + "            exclusiveMinimum: \"%s\"\n".formatted(exclusiveMinimum)
        + "            inclusiveToday: %s\n".formatted(inclusiveToday)
        + "            timezone: %s\n".formatted(timezone);
  }
}
