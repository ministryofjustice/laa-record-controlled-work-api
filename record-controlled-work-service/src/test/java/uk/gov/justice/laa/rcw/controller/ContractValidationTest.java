package uk.gov.justice.laa.rcw.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.justice.laa.rcw.config.schemas.ApplicationRequestSchemaConfiguration;
import uk.gov.justice.laa.rcw.generator.ApplicationGenerator;
import uk.gov.justice.laa.rcw.service.ApplicationCreationService;
import uk.gov.justice.laa.rcw.service.ApplicationDetailsService;
import uk.gov.justice.laa.rcw.service.ApplicationEvidenceService;
import uk.gov.justice.laa.rcw.service.ApplicationMeansService;
import uk.gov.justice.laa.rcw.service.ApplicationQueryService;
import uk.gov.justice.laa.rcw.service.ApplicationUpdateService;

@WebMvcTest(ApplicationController.class)
@Import(ApplicationRequestSchemaConfiguration.class)
@TestPropertySource(
    properties = {
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.security.oauth2.server.resource"
          + ".autoconfigure.OAuth2ResourceServerAutoConfiguration,"
          + "org.springframework.boot.security.oauth2.server.resource"
          + ".autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration,"
          + "org.springframework.boot.security.oauth2.client.autoconfigure.servlet"
          + ".OAuth2ClientWebSecurityAutoConfiguration"
    })
class ContractValidationTest {

  private static final String BASE_CREATE_CASE =
      "classpath:/validation/create-application/"
          + "matching-same-matter-values-and-active-reason-are-preserved.json";
  @Autowired private MockMvc mockMvc;

  @MockitoBean private Clock mockClock;
  @MockitoBean private ApplicationQueryService mockApplicationQueryService;
  @MockitoBean private ApplicationDetailsService mockApplicationDetailsService;
  @MockitoBean private ApplicationMeansService mockApplicationMeansService;
  @MockitoBean private ApplicationEvidenceService mockApplicationEvidenceService;
  @MockitoBean private ApplicationUpdateService mockApplicationUpdateService;
  @MockitoBean private ApplicationCreationService mockApplicationCreationService;

  @ParameterizedTest(name = "{0}")
  @MethodSource("dateOfBirthContractCases")
  void createApplication_matchesDatePolicyCases(
      String caseId, String dateOfBirth, String fixedInstant, boolean accepted) throws Exception {
    useFixedInstant(fixedInstant);
    when(mockApplicationCreationService.createApplication(any()))
        .thenReturn(ApplicationGenerator.create(null));

    var result = mockMvc.perform(createRequest(dateOfBirth));

    if (accepted) {
      result.andExpect(status().isCreated());
      verify(mockApplicationCreationService).createApplication(any());
    } else {
      result.andExpect(status().isBadRequest());
      verifyNoInteractions(mockApplicationCreationService);
    }
  }

  @Test
  void createApplication_recalculatesTodayAcrossLondonMidnight() throws Exception {
    when(mockApplicationCreationService.createApplication(any()))
        .thenReturn(ApplicationGenerator.create(null));

    useFixedInstant("2026-10-06T22:59:59Z");
    mockMvc.perform(createRequest("2026-10-07")).andExpect(status().isBadRequest());
    verifyNoInteractions(mockApplicationCreationService);

    useFixedInstant("2026-10-06T23:00:00Z");
    mockMvc.perform(createRequest("2026-10-07")).andExpect(status().isCreated());
    verify(mockApplicationCreationService).createApplication(any());
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder createRequest(
      String dateOfBirth) throws IOException {
    Resource resource = new PathMatchingResourcePatternResolver().getResource(BASE_CREATE_CASE);
    ObjectMapper mapper = new ObjectMapper();
    JsonNode contractCase;
    try (var input = resource.getInputStream()) {
      contractCase = mapper.readTree(input);
    }
    ObjectNode request = ((ObjectNode) contractCase.path("request")).deepCopy();
    ((ObjectNode) request.path("clientDetails")).put("dateOfBirth", dateOfBirth);
    return post("/api/v1/applications")
        .contentType(MediaType.APPLICATION_JSON)
        .content(request.toString());
  }

  private void useFixedInstant(String fixedInstant) {
    Instant instant = Instant.parse(fixedInstant);
    doAnswer(invocation -> Clock.fixed(instant, invocation.getArgument(0)))
        .when(mockClock)
        .withZone(any(ZoneId.class));
  }

  private static Stream<Arguments> dateOfBirthContractCases() throws IOException {
    Resource[] resources =
        new PathMatchingResourcePatternResolver()
            .getResources("classpath*:/validation/date-of-birth/*.json");
    if (resources.length == 0) {
      throw new IllegalStateException("Date of birth contract cases were not found");
    }

    ObjectMapper mapper = new ObjectMapper();
    return Arrays.stream(resources)
        .sorted(Comparator.comparing(Resource::getFilename))
        .map(
            resource -> {
              try (var input = resource.getInputStream()) {
                JsonNode testCase = mapper.readTree(input);
                return Arguments.of(
                    testCase.path("id").asText(),
                    testCase.path("dateOfBirth").asText(),
                    testCase.path("fixedInstant").asText(),
                    testCase.path("accepted").asBoolean());
              } catch (IOException exception) {
                throw new UncheckedIOException(exception);
              }
            });
  }
}
