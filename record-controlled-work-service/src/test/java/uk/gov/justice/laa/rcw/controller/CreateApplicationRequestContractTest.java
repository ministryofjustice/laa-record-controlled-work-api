package uk.gov.justice.laa.rcw.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.rcw.controller.ApplicationRequestFixtures.createRequestObjectMapper;
import static uk.gov.justice.laa.rcw.controller.ApplicationRequestFixtures.requestFromContractCase;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import uk.gov.justice.laa.rcw.config.schemas.ApplicationRequestSchemaConfiguration;
import uk.gov.justice.laa.rcw.generator.ApplicationGenerator;
import uk.gov.justice.laa.rcw.generator.CreateApplicationRequestGenerator;
import uk.gov.justice.laa.rcw.model.CreateApplicationRequestBody;
import uk.gov.justice.laa.rcw.model.FamilyLawClassification;
import uk.gov.justice.laa.rcw.model.ScopingQuestions;
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
class CreateApplicationRequestContractTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private Clock mockClock;
  @MockitoBean private ApplicationQueryService mockApplicationQueryService;
  @MockitoBean private ApplicationDetailsService mockApplicationDetailsService;
  @MockitoBean private ApplicationMeansService mockApplicationMeansService;
  @MockitoBean private ApplicationEvidenceService mockApplicationEvidenceService;
  @MockitoBean private ApplicationUpdateService mockApplicationUpdateService;
  @MockitoBean private ApplicationCreationService mockApplicationCreationService;

  @BeforeEach
  void setUp() {
    useFixedInstant("2026-10-20T12:00:00Z");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("scopingQuestionCases")
  void createApplication_enforcesScopingQuestionRules(ScopingCase testCase) throws Exception {
    ObjectNode request = ApplicationRequestFixtures.createApplicationRequestBody();
    ObjectNode validStartingAnswers = testCase.validStartingAnswers().get();
    request.set("scopingQuestions", validStartingAnswers);
    when(mockApplicationCreationService.createApplication(any()))
        .thenReturn(ApplicationGenerator.create(null));

    performCreate(request).andExpect(status().isCreated());
    verify(mockApplicationCreationService).createApplication(any());
    clearInvocations(mockApplicationCreationService);

    ObjectNode answers = validStartingAnswers.deepCopy();
    testCase.answerMutation().accept(answers);
    request.set("scopingQuestions", answers);
    ResultActions result = performCreate(request);
    if (testCase.expectedToBeAccepted()) {
      result.andExpect(status().isCreated());
      ArgumentCaptor<CreateApplicationRequestBody> captor =
          ArgumentCaptor.forClass(CreateApplicationRequestBody.class);
      verify(mockApplicationCreationService).createApplication(captor.capture());
      assertEquals(
          createRequestObjectMapper().treeToValue(answers, ScopingQuestions.class),
          captor.getValue().getScopingQuestions());
    } else {
      result
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.reason").value("INVALID_APPLICATION"));
      verifyNoInteractions(mockApplicationCreationService);
    }
  }

  private ResultActions performCreate(ObjectNode request) throws Exception {
    return mockMvc.perform(
        post("/api/v1/applications")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request.toString()));
  }

  private record ScopingCase(
      String description,
      Supplier<ObjectNode> validStartingAnswers,
      Consumer<ObjectNode> answerMutation,
      boolean expectedToBeAccepted) {
    @Override
    public String toString() {
      return description;
    }
  }

  private static ObjectNode privateFamilyLawWithDomesticAbuseEvidence() {
    ObjectNode answers =
        ApplicationRequestFixtures.scopingQuestions(FamilyLawClassification.PRIVATE);
    answers.remove("careProceedingsStarted");
    answers.remove("careProceedingsParentalResponsibility");
    return answers;
  }

  private static ObjectNode privateFamilyLawWithMaintenanceAdvice() {
    ObjectNode answers = privateFamilyLawWithDomesticAbuseEvidence();
    answers.put("needsAdviceOnEUOrInternationalMaintenance", true);
    answers.remove("needsProtectiveInjunction");
    answers.remove("laspoEvidenceScope");
    return answers;
  }

  private static ObjectNode privateFamilyLawWithNoEvidenceScope() {
    return privateFamilyLawWithDomesticAbuseEvidence().put("laspoEvidenceScope", "none");
  }

  private static ObjectNode privateFamilyLawWithProtectiveInjunction() {
    ObjectNode answers = privateFamilyLawWithDomesticAbuseEvidence();
    answers.put("needsProtectiveInjunction", true);
    answers.remove("laspoEvidenceScope");
    return answers;
  }

  private static ObjectNode publicFamilyLawWithoutCareProceedings() {
    ObjectNode answers =
        ApplicationRequestFixtures.scopingQuestions(FamilyLawClassification.PUBLIC);
    answers.remove("needsAdviceOnEUOrInternationalMaintenance");
    answers.remove("needsProtectiveInjunction");
    answers.remove("laspoEvidenceScope");
    answers.remove("careProceedingsParentalResponsibility");
    return answers;
  }

  private static ObjectNode publicFamilyLawWithCareProceedings() {
    return publicFamilyLawWithoutCareProceedings()
        .put("careProceedingsStarted", true)
        .put("careProceedingsParentalResponsibility", false);
  }

  private static Stream<ScopingCase> scopingQuestionCases() {
    return Stream.of(
        new ScopingCase(
            "maintenance skips absent answers",
            CreateApplicationRequestContractTest::privateFamilyLawWithMaintenanceAdvice,
            answers -> answers.put("needsAdviceOnEUOrInternationalMaintenance", true),
            true),
        new ScopingCase(
            "maintenance skips null answers",
            CreateApplicationRequestContractTest::privateFamilyLawWithMaintenanceAdvice,
            answers ->
                answers
                    .putNull("needsProtectiveInjunction")
                    .putNull("laspoEvidenceScope")
                    .putNull("careProceedingsStarted")
                    .putNull("careProceedingsParentalResponsibility"),
            true),
        new ScopingCase(
            "injunction skips null answers",
            CreateApplicationRequestContractTest::privateFamilyLawWithProtectiveInjunction,
            answers ->
                answers
                    .putNull("laspoEvidenceScope")
                    .putNull("careProceedingsStarted")
                    .putNull("careProceedingsParentalResponsibility"),
            true),
        new ScopingCase(
            "none is an allowed scope",
            CreateApplicationRequestContractTest::privateFamilyLawWithDomesticAbuseEvidence,
            answers -> answers.put("laspoEvidenceScope", "none"),
            true),
        new ScopingCase(
            "none skips null public answers",
            CreateApplicationRequestContractTest::privateFamilyLawWithNoEvidenceScope,
            answers ->
                answers
                    .putNull("careProceedingsStarted")
                    .putNull("careProceedingsParentalResponsibility"),
            true),
        new ScopingCase(
            "private evidence skips null public answers",
            CreateApplicationRequestContractTest::privateFamilyLawWithDomesticAbuseEvidence,
            answers ->
                answers
                    .putNull("careProceedingsStarted")
                    .putNull("careProceedingsParentalResponsibility"),
            true),
        new ScopingCase(
            "domestic abuse scope",
            CreateApplicationRequestContractTest::privateFamilyLawWithDomesticAbuseEvidence,
            answers -> answers.put("laspoEvidenceScope", "domesticAbuse"),
            true),
        new ScopingCase(
            "child abuse scope",
            CreateApplicationRequestContractTest::privateFamilyLawWithDomesticAbuseEvidence,
            answers -> answers.put("laspoEvidenceScope", "childAbuse"),
            true),
        new ScopingCase(
            "under 18 scope",
            CreateApplicationRequestContractTest::privateFamilyLawWithDomesticAbuseEvidence,
            answers -> answers.put("laspoEvidenceScope", "under18"),
            true),
        new ScopingCase(
            "parent or guardian scope",
            CreateApplicationRequestContractTest::privateFamilyLawWithDomesticAbuseEvidence,
            answers -> answers.put("laspoEvidenceScope", "parentOrGuardian"),
            true),
        new ScopingCase(
            "public skips absent private answers",
            CreateApplicationRequestContractTest::publicFamilyLawWithoutCareProceedings,
            answers -> answers.put("careProceedingsStarted", false),
            true),
        new ScopingCase(
            "public skips null private answers",
            CreateApplicationRequestContractTest::publicFamilyLawWithoutCareProceedings,
            answers ->
                answers
                    .putNull("needsAdviceOnEUOrInternationalMaintenance")
                    .putNull("needsProtectiveInjunction")
                    .putNull("laspoEvidenceScope")
                    .putNull("careProceedingsParentalResponsibility"),
            true),
        new ScopingCase(
            "public parental responsibility false",
            CreateApplicationRequestContractTest::publicFamilyLawWithCareProceedings,
            answers -> answers.put("careProceedingsParentalResponsibility", false),
            true),
        new ScopingCase(
            "public parental responsibility true",
            CreateApplicationRequestContractTest::publicFamilyLawWithCareProceedings,
            answers ->
                answers
                    .put("careProceedingsParentalResponsibility", true)
                    .putNull("needsAdviceOnEUOrInternationalMaintenance")
                    .putNull("needsProtectiveInjunction")
                    .putNull("laspoEvidenceScope"),
            true),
        new ScopingCase(
            "missing maintenance",
            CreateApplicationRequestContractTest::privateFamilyLawWithMaintenanceAdvice,
            answers -> answers.remove("needsAdviceOnEUOrInternationalMaintenance"),
            false),
        new ScopingCase(
            "null maintenance",
            CreateApplicationRequestContractTest::privateFamilyLawWithMaintenanceAdvice,
            answers -> answers.putNull("needsAdviceOnEUOrInternationalMaintenance"),
            false),
        new ScopingCase(
            "missing maintenance merits",
            CreateApplicationRequestContractTest::privateFamilyLawWithMaintenanceAdvice,
            answers -> answers.remove("caseMeetsMeritsCriteria"),
            false),
        new ScopingCase(
            "null maintenance merits",
            CreateApplicationRequestContractTest::privateFamilyLawWithMaintenanceAdvice,
            answers -> answers.putNull("caseMeetsMeritsCriteria"),
            false),
        new ScopingCase(
            "false maintenance merits",
            CreateApplicationRequestContractTest::privateFamilyLawWithMaintenanceAdvice,
            answers -> answers.put("caseMeetsMeritsCriteria", false),
            false),
        new ScopingCase(
            "missing evidence merits",
            CreateApplicationRequestContractTest::privateFamilyLawWithDomesticAbuseEvidence,
            answers -> answers.remove("caseMeetsMeritsCriteria"),
            false),
        new ScopingCase(
            "null evidence merits",
            CreateApplicationRequestContractTest::privateFamilyLawWithDomesticAbuseEvidence,
            answers -> answers.putNull("caseMeetsMeritsCriteria"),
            false),
        new ScopingCase(
            "false evidence merits",
            CreateApplicationRequestContractTest::privateFamilyLawWithDomesticAbuseEvidence,
            answers -> answers.put("caseMeetsMeritsCriteria", false),
            false),
        new ScopingCase(
            "missing no evidence merits",
            CreateApplicationRequestContractTest::privateFamilyLawWithNoEvidenceScope,
            answers -> answers.remove("caseMeetsMeritsCriteria"),
            false),
        new ScopingCase(
            "null no evidence merits",
            CreateApplicationRequestContractTest::privateFamilyLawWithNoEvidenceScope,
            answers -> answers.putNull("caseMeetsMeritsCriteria"),
            false),
        new ScopingCase(
            "false no evidence merits",
            CreateApplicationRequestContractTest::privateFamilyLawWithNoEvidenceScope,
            answers -> answers.put("caseMeetsMeritsCriteria", false),
            false),
        new ScopingCase(
            "missing injunction",
            CreateApplicationRequestContractTest::privateFamilyLawWithDomesticAbuseEvidence,
            answers -> answers.remove("needsProtectiveInjunction"),
            false),
        new ScopingCase(
            "missing evidence scope",
            CreateApplicationRequestContractTest::privateFamilyLawWithDomesticAbuseEvidence,
            answers -> answers.remove("laspoEvidenceScope"),
            false),
        new ScopingCase(
            "injunction cannot carry evidence scope",
            CreateApplicationRequestContractTest::privateFamilyLawWithProtectiveInjunction,
            answers -> answers.put("laspoEvidenceScope", "domesticAbuse"),
            false),
        new ScopingCase(
            "missing care proceedings",
            CreateApplicationRequestContractTest::publicFamilyLawWithoutCareProceedings,
            answers -> answers.remove("careProceedingsStarted"),
            false),
        new ScopingCase(
            "missing parental responsibility",
            CreateApplicationRequestContractTest::publicFamilyLawWithCareProceedings,
            answers -> answers.remove("careProceedingsParentalResponsibility"),
            false),
        new ScopingCase(
            "maintenance cannot carry injunction",
            CreateApplicationRequestContractTest::privateFamilyLawWithMaintenanceAdvice,
            answers -> answers.put("needsProtectiveInjunction", false),
            false),
        new ScopingCase(
            "invalid evidence scope",
            CreateApplicationRequestContractTest::privateFamilyLawWithDomesticAbuseEvidence,
            answers -> answers.put("laspoEvidenceScope", "invalid"),
            false),
        new ScopingCase(
            "no care cannot carry parental responsibility",
            CreateApplicationRequestContractTest::publicFamilyLawWithoutCareProceedings,
            answers -> answers.put("careProceedingsParentalResponsibility", true),
            false),
        new ScopingCase(
            "care cannot carry maintenance",
            CreateApplicationRequestContractTest::publicFamilyLawWithCareProceedings,
            answers -> answers.put("needsAdviceOnEUOrInternationalMaintenance", false),
            false),
        new ScopingCase(
            "care proceedings must be boolean",
            CreateApplicationRequestContractTest::publicFamilyLawWithoutCareProceedings,
            answers -> answers.put("careProceedingsStarted", "false"),
            false),
        new ScopingCase(
            "merits must be boolean",
            CreateApplicationRequestContractTest::publicFamilyLawWithoutCareProceedings,
            answers -> answers.put("caseMeetsMeritsCriteria", "false"),
            false),
        new ScopingCase(
            "unknown scoping property",
            CreateApplicationRequestContractTest::privateFamilyLawWithDomesticAbuseEvidence,
            answers -> answers.put("unexpected", true),
            false),
        new ScopingCase(
            "missing prior legal aid",
            CreateApplicationRequestContractTest::publicFamilyLawWithoutCareProceedings,
            answers -> answers.remove("priorLegalAid"),
            false),
        new ScopingCase(
            "unsupported prior legal aid spelling",
            CreateApplicationRequestContractTest::publicFamilyLawWithoutCareProceedings,
            answers -> answers.put("priorLegalAid", "same_matter"),
            false),
        new ScopingCase(
            "missing family classification",
            CreateApplicationRequestContractTest::publicFamilyLawWithoutCareProceedings,
            answers -> answers.remove("familyLawClassification"),
            false),
        new ScopingCase(
            "null injunction",
            CreateApplicationRequestContractTest::privateFamilyLawWithDomesticAbuseEvidence,
            answers -> answers.putNull("needsProtectiveInjunction"),
            false),
        new ScopingCase(
            "null evidence scope",
            CreateApplicationRequestContractTest::privateFamilyLawWithDomesticAbuseEvidence,
            answers -> answers.putNull("laspoEvidenceScope"),
            false),
        new ScopingCase(
            "null care proceedings",
            CreateApplicationRequestContractTest::publicFamilyLawWithoutCareProceedings,
            answers -> answers.putNull("careProceedingsStarted"),
            false),
        new ScopingCase(
            "null parental responsibility",
            CreateApplicationRequestContractTest::publicFamilyLawWithCareProceedings,
            answers -> answers.putNull("careProceedingsParentalResponsibility"),
            false),
        new ScopingCase(
            "null prior legal aid",
            CreateApplicationRequestContractTest::publicFamilyLawWithoutCareProceedings,
            answers -> answers.putNull("priorLegalAid"),
            false),
        new ScopingCase(
            "null family classification",
            CreateApplicationRequestContractTest::publicFamilyLawWithoutCareProceedings,
            answers -> answers.putNull("familyLawClassification"),
            false),
        new ScopingCase(
            "maintenance must be boolean",
            CreateApplicationRequestContractTest::privateFamilyLawWithDomesticAbuseEvidence,
            answers -> answers.put("needsAdviceOnEUOrInternationalMaintenance", "false"),
            false),
        new ScopingCase(
            "injunction must be boolean",
            CreateApplicationRequestContractTest::privateFamilyLawWithDomesticAbuseEvidence,
            answers -> answers.put("needsProtectiveInjunction", "false"),
            false),
        new ScopingCase(
            "private cannot carry care proceedings",
            CreateApplicationRequestContractTest::privateFamilyLawWithMaintenanceAdvice,
            answers -> answers.put("careProceedingsStarted", false),
            false),
        new ScopingCase(
            "private cannot carry parental responsibility",
            CreateApplicationRequestContractTest::privateFamilyLawWithMaintenanceAdvice,
            answers -> answers.put("careProceedingsParentalResponsibility", false),
            false),
        new ScopingCase(
            "no care cannot carry injunction",
            CreateApplicationRequestContractTest::publicFamilyLawWithoutCareProceedings,
            answers -> answers.put("needsProtectiveInjunction", false),
            false),
        new ScopingCase(
            "no care cannot carry evidence scope",
            CreateApplicationRequestContractTest::publicFamilyLawWithoutCareProceedings,
            answers -> answers.put("laspoEvidenceScope", "none"),
            false),
        new ScopingCase(
            "care cannot carry injunction",
            CreateApplicationRequestContractTest::publicFamilyLawWithCareProceedings,
            answers -> answers.put("needsProtectiveInjunction", false),
            false),
        new ScopingCase(
            "care cannot carry evidence scope",
            CreateApplicationRequestContractTest::publicFamilyLawWithCareProceedings,
            answers -> answers.put("laspoEvidenceScope", "none"),
            false));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("dateOfBirthContractCases")
  void createApplication_enforcesDateOfBirthPolicy(
      String caseId, String dateOfBirth, String fixedInstant, boolean accepted) throws Exception {
    useFixedInstant(fixedInstant);
    when(mockApplicationCreationService.createApplication(any()))
        .thenReturn(ApplicationGenerator.create(null));

    var result = mockMvc.perform(postApplicationRequestWithDateOfBirth(dateOfBirth));

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
    mockMvc
        .perform(postApplicationRequestWithDateOfBirth("2026-10-07"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(mockApplicationCreationService);

    useFixedInstant("2026-10-06T23:00:00Z");
    mockMvc
        .perform(postApplicationRequestWithDateOfBirth("2026-10-07"))
        .andExpect(status().isCreated());
    verify(mockApplicationCreationService).createApplication(any());
  }

  private MockHttpServletRequestBuilder postApplicationRequestWithDateOfBirth(String dateOfBirth)
      throws IOException {
    ObjectMapper mapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    ObjectNode request =
        mapper.valueToTree(
            CreateApplicationRequestGenerator.createWithName(
                builder ->
                    builder
                        .legalAidLast6Months(true)
                        .reasonForReapplication("A separate legal issue")));
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

  @Test
  void createApplication_rejectsPostcodeThatRemainsInvalidAfterNormalization() throws Exception {
    ObjectMapper mapper = createRequestObjectMapper();
    ObjectNode request = mapper.valueToTree(CreateApplicationRequestGenerator.createWithName(null));
    ((ObjectNode) request.path("clientDetails").path("address")).put("postCode", "INVALID");

    mockMvc
        .perform(
            post("/api/v1/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request.toString()))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(mockApplicationCreationService);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("createApplicationContractCases")
  void createApplication_enforcesRequestContract(
      String contractCaseId,
      String expectedOperation,
      boolean expectedAccepted,
      JsonNode requestBody,
      JsonNode expectedPreservedValues)
      throws Exception {
    assertEquals("createApplication", expectedOperation);
    when(mockApplicationCreationService.createApplication(any()))
        .thenReturn(ApplicationGenerator.create(null));

    ResultActions result =
        mockMvc.perform(
            post("/api/v1/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody.toString()));

    if (!expectedAccepted) {
      result.andExpect(status().isBadRequest());
      verifyNoInteractions(mockApplicationCreationService);
      return;
    }

    result.andExpect(status().isCreated());
    ArgumentCaptor<CreateApplicationRequestBody> requestCaptor =
        ArgumentCaptor.forClass(CreateApplicationRequestBody.class);
    verify(mockApplicationCreationService).createApplication(requestCaptor.capture());
    JsonNode actual = createRequestObjectMapper().valueToTree(requestCaptor.getValue());
    assertEquals(requestBody, actual, contractCaseId + " should preserve the complete request");
    expectedPreservedValues
        .fields()
        .forEachRemaining(
            expected ->
                assertEquals(
                    expected.getValue(),
                    actual.at(expected.getKey()),
                    contractCaseId + " should preserve " + expected.getKey()));
  }

  @Test
  void createApplication_acceptsNamesWithoutFormatRestrictions_andPreservesThem() throws Exception {
    ObjectMapper mapper = createRequestObjectMapper();
    ObjectNode request = mapper.valueToTree(CreateApplicationRequestGenerator.createWithName(null));
    String firstName = Character.toString(0x2003);
    String lastName = "O'Connor 42 " + Character.toString(0x674E);
    ((ObjectNode) request.get("clientDetails"))
        .put("firstName", firstName)
        .put("lastName", lastName);
    when(mockApplicationCreationService.createApplication(any()))
        .thenReturn(ApplicationGenerator.create(null));

    mockMvc
        .perform(
            post("/api/v1/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request.toString()))
        .andExpect(status().isCreated());

    verify(mockApplicationCreationService)
        .createApplication(
            argThat(
                actual ->
                    firstName.equals(actual.getClientDetails().getFirstName())
                        && lastName.equals(actual.getClientDetails().getLastName())));
  }

  @Test
  void createApplication_rejectsDuplicateJsonProperties_beforeCreation() throws Exception {
    ObjectMapper mapper = createRequestObjectMapper();
    String request =
        mapper.writeValueAsString(CreateApplicationRequestGenerator.createWithName(null));
    String duplicateRequest =
        request.replace(
            "\"legalAidLast6Months\":false",
            "\"legalAidLast6Months\":false,\"legalAidLast6Months\":false");
    when(mockApplicationCreationService.createApplication(any()))
        .thenReturn(ApplicationGenerator.create(null));

    mockMvc
        .perform(
            post("/api/v1/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(duplicateRequest))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(mockApplicationCreationService);
  }

  @Test
  void createApplication_rejectsNullScopingQuestions() throws Exception {
    ObjectNode request = ApplicationRequestFixtures.createApplicationRequestBody();
    request.putNull("scopingQuestions");

    mockMvc
        .perform(
            post("/api/v1/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request.toString()))
        .andExpect(status().isBadRequest());
    org.mockito.Mockito.verifyNoInteractions(mockApplicationCreationService);
  }

  @Test
  void createApplication_rejectsMissingScopingQuestions() throws Exception {
    ObjectMapper mapper = createRequestObjectMapper();
    ObjectNode request = mapper.valueToTree(CreateApplicationRequestGenerator.createWithName(null));
    request.remove("scopingQuestions");

    mockMvc
        .perform(
            post("/api/v1/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request.toString()))
        .andExpect(status().isBadRequest());
    org.mockito.Mockito.verifyNoInteractions(mockApplicationCreationService);
  }

  private static Stream<Arguments> createApplicationContractCases() throws Exception {
    Resource[] resources =
        new PathMatchingResourcePatternResolver()
            .getResources("classpath*:/validation/create-application/*.json");
    if (resources.length == 0) {
      throw new IllegalStateException("Create application contract cases were not found");
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
                    testCase.path("operation").asText(),
                    testCase.path("accepted").asBoolean(),
                    requestFromContractCase(mapper, testCase),
                    testCase.path("preserves"));
              } catch (IOException exception) {
                throw new UncheckedIOException(exception);
              }
            });
  }
}
