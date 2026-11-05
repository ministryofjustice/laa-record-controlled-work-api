const STAGING_ORIGIN =
  "https://laa-record-controlled-work-api-staging.cloud-platform.service.justice.gov.uk";
const OAUTH_ACCESS_TOKEN = "$oauth2.applications-token.access_token";
const A_OFFICE_CODES = ["0R125Q"];
const B_OFFICE_CODES = ["0R128U", "0R695K"];
const C_OFFICE_CODES = ["0R695K"];
const B_FIXTURES = {
  private: { officeCode: "0R128U", income: 1100, label: "Private" },
  shared: { officeCode: "0R695K", income: 1300, label: "Shared" }
};
const B_FIXTURE_VARIABLE_SUFFIXES = [
  "ApplicationId",
  "ApplicationRunId",
  "SetupStatus",
  "ClientLastName",
  "ProviderFirmCode",
  "MeansIncome",
  "OwnerObservedState",
  "OwnerObservedMeansIncome",
  "OwnerObservedClientLastName",
  "DetailsETag",
  "DetailsETagApplicationId",
  "OwnerReadbackStatus"
];
const UUID_PATTERN = /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i;
const STRONG_ETAG_PATTERN = /^"\d+"$/;

function bFixture(fixtureName) {
  if (!Object.prototype.hasOwnProperty.call(B_FIXTURES, fixtureName)) {
    throw new Error("Select the private-office or shared-office B fixture.");
  }

  return B_FIXTURES[fixtureName];
}

function bVariable(fixtureName, suffix) {
  const prefix = fixtureName === "private" ? "Private" : "Shared";
  return `exampleB${prefix}${suffix}`;
}

function failBSetup(bru, fixtureName) {
  bru.setVar(bVariable(fixtureName, "SetupStatus"), "failed");
  bru.setVar("exampleBSetupStatus", "failed");
  [
    "DetailsETag",
    "DetailsETagApplicationId"
  ].forEach(suffix => bru.deleteVar(bVariable(fixtureName, suffix)));
}

function assertACompletedSetup(bru) {
  assertCurrentAApplication(bru);
  if (
    bru.getVar("exampleAProviderFirmCode") !== "997" ||
    bru.getVar("exampleACompletionStatus") !== "verified"
  ) {
    throw new Error("Complete and verify A's application before starting B's setup.");
  }
}

function generateUfn(random = Math.random) {
  const digits = Math.floor(random() * 1_000_000_000)
    .toString()
    .padStart(9, "0");

  return `${digits.slice(0, 6)}/${digits.slice(6)}`;
}

function assertStagingDestination(environmentName, requestUrl) {
  if (environmentName !== "staging") {
    throw new Error("Select the staging environment before sending this example request.");
  }

  let destination;
  try {
    destination = new URL(requestUrl);
  } catch {
    throw new Error("This example request must target the configured staging destination.");
  }

  if (destination.origin !== STAGING_ORIGIN || destination.username || destination.password) {
    throw new Error("This example request must target the configured staging destination.");
  }
}

function decodeAccessTokenClaims(accessToken) {
  if (typeof accessToken !== "string" || accessToken.length === 0) {
    throw new Error("The active identity could not be checked. Sign in through the applications folder.");
  }

  const tokenParts = accessToken.split(".");
  if (tokenParts.length !== 3 || tokenParts[1].length === 0) {
    throw new Error("The active identity could not be checked. Sign in again before sending.");
  }

  try {
    const claims = JSON.parse(Buffer.from(tokenParts[1], "base64url").toString("utf8"));
    if (!claims || typeof claims !== "object" || Array.isArray(claims)) {
      throw new Error();
    }

    return claims;
  } catch {
    throw new Error("The active identity could not be checked. Sign in again before sending.");
  }
}

function assertExpectedOffices(accessToken, expectedOfficeCodes) {
  const claims = decodeAccessTokenClaims(accessToken);
  const actualOfficeCodes = claims.LAA_ACCOUNTS;
  const expectedOffices = [...expectedOfficeCodes].sort();
  const isExactOfficeSet =
    Array.isArray(actualOfficeCodes) &&
    actualOfficeCodes.length === expectedOfficeCodes.length &&
    actualOfficeCodes.every(code => typeof code === "string") &&
    [...actualOfficeCodes]
      .sort()
      .every((code, index) => code === expectedOffices[index]);

  if (!isExactOfficeSet) {
    throw new Error("The active identity does not have the expected provider-office access.");
  }
}

function assertFixtureReference(runId, applicationId, applicationRunId, setupStatus) {
  const isUuid = typeof applicationId === "string" && UUID_PATTERN.test(applicationId);

  if (
    typeof runId !== "string" ||
    runId.length === 0 ||
    !isUuid ||
    applicationRunId !== runId ||
    setupStatus !== "created"
  ) {
    throw new Error(
      "A's application is not available in this dataset run. Complete its explicit creation request first. This request will not use the general application ID or create a replacement."
    );
  }
}

function assertCurrentAApplication(bru) {
  assertFixtureReference(
    bru.getVar("exampleDatasetRunId"),
    bru.getVar("exampleAApplicationId"),
    bru.getVar("exampleAApplicationRunId"),
    bru.getVar("exampleASetupStatus")
  );
}

function assertARequest(bru, request, requiresFixture, requiresFirmVerification) {
  assertStagingDestination(bru.getEnvName(), request.getUrl());
  assertExpectedOffices(
    bru.getOauth2CredentialVar(OAUTH_ACCESS_TOKEN),
    A_OFFICE_CODES
  );
  if (requiresFixture) {
    assertCurrentAApplication(bru);
  }
  if (requiresFirmVerification && bru.getVar("exampleAProviderFirmCode") !== "997") {
    throw new Error("Verify A's created application belongs to firm 997 before continuing.");
  }
}

function assertBRequest(bru, request) {
  assertStagingDestination(bru.getEnvName(), request.getUrl());
  assertExpectedOffices(
    bru.getOauth2CredentialVar(OAUTH_ACCESS_TOKEN),
    B_OFFICE_CODES
  );
  assertACompletedSetup(bru);

  if (bru.getVar("exampleBSetupStatus") === "failed") {
    throw new Error("B's setup failed. Start a new dataset with A's explicit setup request.");
  }
}

function assertBFixtureReference(bru, fixtureName, requiredStatus) {
  const fixture = bFixture(fixtureName);
  const runId = bru.getVar("exampleDatasetRunId");
  const applicationId = bru.getVar(bVariable(fixtureName, "ApplicationId"));

  if (
    typeof runId !== "string" ||
    runId.length === 0 ||
    !UUID_PATTERN.test(applicationId || "") ||
    bru.getVar(bVariable(fixtureName, "ApplicationRunId")) !== runId ||
    bru.getVar(bVariable(fixtureName, "SetupStatus")) !== requiredStatus
  ) {
    throw new Error(
      `B's ${fixture.label.toLowerCase()}-office draft is unavailable in this dataset. Complete its explicit setup steps first.`
    );
  }

  return applicationId;
}

function assertBFixtureRequest(bru, request, fixtureName, requiredStatus) {
  assertBRequest(bru, request);
  return assertBFixtureReference(bru, fixtureName, requiredStatus);
}

function beginBApplicationRead(bru, request, fixtureName, requiredStatus) {
  const applicationId = assertBFixtureRequest(
    bru,
    request,
    fixtureName,
    requiredStatus
  );
  bru.deleteVar(bVariable(fixtureName, "DetailsETag"));
  bru.deleteVar(bVariable(fixtureName, "DetailsETagApplicationId"));
  return applicationId;
}

function beginBApplicationCreate(bru, request, fixtureName) {
  bFixture(fixtureName);
  assertBRequest(bru, request);

  const setupStatus = bru.getVar(bVariable(fixtureName, "SetupStatus"));
  const applicationId = bru.getVar(bVariable(fixtureName, "ApplicationId"));
  if (setupStatus !== undefined || applicationId !== undefined) {
    throw new Error(
      "This B fixture was already attempted. Start a new dataset instead of creating a replacement."
    );
  }

  if (
    bru.getVar("exampleBSetupStatus") !== undefined &&
    bru.getVar("exampleBSetupStatus") !== "in-progress"
  ) {
    throw new Error("Start a new dataset before creating B's drafts.");
  }
  if (fixtureName === "private" && bru.getVar("exampleBSetupStatus") !== undefined) {
    throw new Error("Private-office draft creation has already started. Start a new dataset.");
  }
  if (
    fixtureName === "shared" &&
    (bru.getVar("exampleBSetupStatus") !== "in-progress" ||
      !["means-saved", "populated"].includes(
        bru.getVar("exampleBPrivateSetupStatus")
      ))
  ) {
    throw new Error("Complete the private-office draft setup before creating the shared draft.");
  }

  bru.setVar("exampleBSetupStatus", "in-progress");
  bru.setVar(bVariable(fixtureName, "SetupStatus"), "creating");
}

function buildBApplicationCreateBody(bru, fixtureName) {
  const fixture = bFixture(fixtureName);
  assertACompletedSetup(bru);
  if (bru.getVar(bVariable(fixtureName, "SetupStatus")) !== "creating") {
    throw new Error("Start the explicit B draft creation request first.");
  }

  const runId = bru.getVar("exampleDatasetRunId");
  const lastName = `SyntheticB${fixture.label}${runId.replace(/[^a-z0-9]/gi, "")}`;
  const generateApplicationDetailsRequestBody = require("./application-details.js");
  const body = generateApplicationDetailsRequestBody(bru, {
    providerOfficeCode: fixture.officeCode,
    legalAidLast6Months: false,
    reasonForReapplication: "",
    ecfFlag: false,
    scopingQuestions: { priorLegalAid: "yesSameMatter" },
    clientDetails: {
      firstName: "Synthetic",
      lastName,
      dateOfBirth: "1990-01-01",
      niNumber: undefined,
      hasFixedAddress: false,
      address: undefined
    }
  });

  bru.setVar(bVariable(fixtureName, "ClientLastName"), lastName);
  return body;
}

function captureBApplicationResponse(bru, response, fixtureName) {
  bFixture(fixtureName);
  const runId = bru.getVar("exampleDatasetRunId");
  const applicationId = response.getBody()?.id;
  if (
    bru.getVar(bVariable(fixtureName, "SetupStatus")) === "creating" &&
    typeof runId === "string" &&
    runId.length > 0 &&
    response.getStatus() === 201 &&
    UUID_PATTERN.test(applicationId || "")
  ) {
    bru.setVar(bVariable(fixtureName, "ApplicationId"), applicationId);
    bru.setVar(bVariable(fixtureName, "ApplicationRunId"), runId);
    bru.setVar(bVariable(fixtureName, "SetupStatus"), "created");
    return;
  }

  failBSetup(bru, fixtureName);
}

function captureBApplicationVerification(bru, response, fixtureName) {
  const fixture = bFixture(fixtureName);
  const application = response.getBody();
  const isExpectedApplication =
    response.getStatus() === 200 &&
    application?.id === bru.getVar(bVariable(fixtureName, "ApplicationId")) &&
    application?.providerFirmCode === "998" &&
    application?.providerOfficeCode === fixture.officeCode &&
    application?.applicationState === "DRAFT" &&
    application?.clientDetails?.firstName === "Synthetic" &&
    application?.clientDetails?.lastName === bru.getVar(
      bVariable(fixtureName, "ClientLastName")
    );

  if (isExpectedApplication) {
    bru.setVar(bVariable(fixtureName, "ProviderFirmCode"), "998");
    bru.setVar(bVariable(fixtureName, "SetupStatus"), "verified");
    return;
  }

  failBSetup(bru, fixtureName);
}

function beginBMeansUpdate(bru, request, fixtureName) {
  assertBFixtureRequest(bru, request, fixtureName, "verified");
  bru.setVar(bVariable(fixtureName, "SetupStatus"), "means-updating");
}

function buildBMeansBody(fixtureName) {
  const fixture = bFixture(fixtureName);
  return {
    data: {
      level_of_help: "controlled",
      employment_status: "employed",
      incomes: [
        {
          gross_income: fixture.income,
          income_frequency: "monthly"
        }
      ]
    },
    result: { version: "6" }
  };
}

function captureBMeansResponse(bru, response, fixtureName) {
  bFixture(fixtureName);
  if (
    response.getStatus() === 204 &&
    bru.getVar(bVariable(fixtureName, "SetupStatus")) === "means-updating"
  ) {
    bru.setVar(bVariable(fixtureName, "SetupStatus"), "means-saved");
    return;
  }

  failBSetup(bru, fixtureName);
}

function applicationHasBMeans(application, fixture) {
  const data = application?.eligibilityAssessment?.data;
  const incomes = data?.incomes;
  return (
    data?.level_of_help === "controlled" &&
    data?.employment_status === "employed" &&
    Array.isArray(incomes) &&
    incomes.length === 1 &&
    incomes[0]?.gross_income === fixture.income &&
    incomes[0]?.income_frequency === "monthly" &&
    application?.eligibilityAssessment?.result?.version === "6"
  );
}

function captureBPopulatedApplication(bru, response, fixtureName) {
  const fixture = bFixture(fixtureName);
  const application = response.getBody();
  const etag = response.getHeader("ETag");
  const applicationId = bru.getVar(bVariable(fixtureName, "ApplicationId"));
  const isExpectedApplication =
    response.getStatus() === 200 &&
    application?.id === applicationId &&
    application?.providerFirmCode === "998" &&
    application?.providerOfficeCode === fixture.officeCode &&
    application?.applicationState === "DRAFT" &&
    application?.clientDetails?.firstName === "Synthetic" &&
    application?.clientDetails?.lastName === bru.getVar(
      bVariable(fixtureName, "ClientLastName")
    ) &&
    applicationHasBMeans(application, fixture) &&
    STRONG_ETAG_PATTERN.test(etag || "");

  if (!isExpectedApplication) {
    failBSetup(bru, fixtureName);
    return;
  }

  bru.setVar(bVariable(fixtureName, "MeansIncome"), fixture.income);
  bru.setVar(bVariable(fixtureName, "OwnerObservedState"), "DRAFT");
  bru.setVar(bVariable(fixtureName, "OwnerObservedMeansIncome"), fixture.income);
  bru.setVar(
    bVariable(fixtureName, "OwnerObservedClientLastName"),
    application.clientDetails.lastName
  );
  bru.setVar(bVariable(fixtureName, "DetailsETag"), etag);
  bru.setVar(bVariable(fixtureName, "DetailsETagApplicationId"), applicationId);
  bru.setVar(bVariable(fixtureName, "SetupStatus"), "populated");

  if (
    bru.getVar(bVariable("private", "SetupStatus")) === "populated" &&
    bru.getVar(bVariable("shared", "SetupStatus")) === "populated"
  ) {
    bru.setVar("exampleBSetupStatus", "ready");
  }
}

function assertCSharedRequest(bru, request, requiresETag = false) {
  assertStagingDestination(bru.getEnvName(), request.getUrl());
  assertExpectedOffices(
    bru.getOauth2CredentialVar(OAUTH_ACCESS_TOKEN),
    C_OFFICE_CODES
  );
  assertACompletedSetup(bru);
  assertBFixtureReference(bru, "private", "populated");
  assertBFixtureReference(bru, "shared", "populated");

  if (!["ready", "complete"].includes(bru.getVar("exampleBSetupStatus"))) {
    throw new Error("Complete B's explicit two-office setup before using shared access.");
  }
  if (bru.getVar("exampleCSharedUpdateStatus") === "updated") {
    throw new Error("C's shared-office update is complete. Switch back to B for owner read-back.");
  }
  if (bru.getVar("exampleBPrivateProviderFirmCode") !== "998" ||
      bru.getVar("exampleBSharedProviderFirmCode") !== "998") {
    throw new Error("Verify both B drafts belong to firm 998 before testing shared access.");
  }

  if (requiresETag) {
    const sharedApplicationId = bru.getVar("exampleBSharedApplicationId");
    const etag = bru.getVar("exampleCSharedDetailsETag");
    if (
      bru.getVar("exampleCSharedReadStatus") !== "verified" ||
      bru.getVar("exampleCSharedDetailsETagApplicationId") !== sharedApplicationId ||
      !STRONG_ETAG_PATTERN.test(etag || "") ||
      bru.getVar("exampleCSharedUpdateStatus") === "updated"
    ) {
      throw new Error("Retrieve the shared draft as C before its one permitted details update.");
    }
  }
}

function beginCSharedRead(bru, request) {
  assertCSharedRequest(bru, request);
  bru.deleteVar("exampleCSharedDetailsETag");
  bru.deleteVar("exampleCSharedDetailsETagApplicationId");
  bru.deleteVar("exampleCSharedReadStatus");
}

function captureCSharedApplication(bru, response) {
  const application = response.getBody();
  const sharedApplicationId = bru.getVar("exampleBSharedApplicationId");
  const etag = response.getHeader("ETag");
  const isExpectedApplication =
    response.getStatus() === 200 &&
    application?.id === sharedApplicationId &&
    application?.providerFirmCode === "998" &&
    application?.providerOfficeCode === C_OFFICE_CODES[0] &&
    application?.applicationState === "DRAFT" &&
    application?.clientDetails?.lastName === bru.getVar(
      "exampleBSharedClientLastName"
    ) &&
    applicationHasBMeans(application, B_FIXTURES.shared) &&
    STRONG_ETAG_PATTERN.test(etag || "");

  if (!isExpectedApplication) {
    bru.setVar("exampleBSetupStatus", "failed");
    bru.setVar("exampleCSharedReadStatus", "failed");
    bru.deleteVar("exampleCSharedDetailsETag");
    bru.deleteVar("exampleCSharedDetailsETagApplicationId");
    return;
  }

  bru.setVar("exampleCSharedDetailsETag", etag);
  bru.setVar("exampleCSharedDetailsETagApplicationId", sharedApplicationId);
  bru.setVar("exampleCSharedReadStatus", "verified");
}

function buildCSharedDetailsUpdateBody(bru) {
  const runId = bru.getVar("exampleDatasetRunId");
  if (
    typeof runId !== "string" ||
    runId.length === 0 ||
    bru.getVar("exampleCSharedReadStatus") !== "verified" ||
    bru.getVar("exampleCSharedDetailsETagApplicationId") !==
      bru.getVar("exampleBSharedApplicationId")
  ) {
    throw new Error("Retrieve the shared draft as C before updating its details.");
  }

  const updatedLastName = `SyntheticCShared${runId.replace(/[^a-z0-9]/gi, "")}`;
  const generateApplicationDetailsRequestBody = require("./application-details.js");
  bru.setVar("exampleCSharedUpdatedLastName", updatedLastName);
  return generateApplicationDetailsRequestBody(bru, {
    legalAidLast6Months: false,
    reasonForReapplication: "",
    ecfFlag: false,
    scopingQuestions: { priorLegalAid: "yesSameMatter" },
    clientDetails: {
      firstName: "Synthetic",
      lastName: updatedLastName,
      dateOfBirth: "1990-01-01",
      niNumber: undefined,
      hasFixedAddress: false,
      address: undefined
    }
  });
}

function captureCSharedUpdate(bru, response) {
  const etag = response.getHeader("ETag");
  const wasUpdated =
    response.getStatus() === 204 &&
    STRONG_ETAG_PATTERN.test(etag || "") &&
    bru.getVar("exampleCSharedDetailsETagApplicationId") ===
      bru.getVar("exampleBSharedApplicationId");

  bru.deleteVar("exampleCSharedDetailsETag");
  bru.deleteVar("exampleCSharedDetailsETagApplicationId");
  if (wasUpdated) {
    bru.setVar("exampleCSharedUpdateStatus", "updated");
    return;
  }

  bru.setVar("exampleCSharedUpdateStatus", "failed");
  bru.setVar("exampleBSetupStatus", "failed");
}

function assertBOwnerReadbackRequest(bru, request, fixtureName) {
  assertBFixtureRequest(bru, request, fixtureName, "populated");
  if (bru.getVar("exampleCSharedUpdateStatus") !== "updated") {
    throw new Error("Complete C's shared-office update before owner read-back.");
  }
  bru.deleteVar(bVariable(fixtureName, "DetailsETag"));
  bru.deleteVar(bVariable(fixtureName, "DetailsETagApplicationId"));
  bru.setVar(bVariable(fixtureName, "OwnerReadbackStatus"), "reading");
}

function captureBOwnerReadback(bru, response, fixtureName) {
  const fixture = bFixture(fixtureName);
  const application = response.getBody();
  const expectedLastName =
    fixtureName === "shared"
      ? bru.getVar("exampleCSharedUpdatedLastName")
      : bru.getVar(bVariable(fixtureName, "ClientLastName"));
  const etag = response.getHeader("ETag");
  const applicationId = bru.getVar(bVariable(fixtureName, "ApplicationId"));
  const isExpectedApplication =
    response.getStatus() === 200 &&
    application?.id === applicationId &&
    application?.providerFirmCode === "998" &&
    application?.providerOfficeCode === fixture.officeCode &&
    application?.applicationState === "DRAFT" &&
    application?.clientDetails?.lastName === expectedLastName &&
    applicationHasBMeans(application, fixture) &&
    STRONG_ETAG_PATTERN.test(etag || "");

  if (!isExpectedApplication) {
    failBSetup(bru, fixtureName);
    return;
  }

  bru.setVar(bVariable(fixtureName, "OwnerObservedState"), "DRAFT");
  bru.setVar(bVariable(fixtureName, "OwnerObservedMeansIncome"), fixture.income);
  bru.setVar(bVariable(fixtureName, "OwnerObservedClientLastName"), expectedLastName);
  bru.setVar(bVariable(fixtureName, "DetailsETag"), etag);
  bru.setVar(bVariable(fixtureName, "DetailsETagApplicationId"), applicationId);
  bru.setVar(bVariable(fixtureName, "OwnerReadbackStatus"), "verified");

  if (
    bru.getVar(bVariable("private", "OwnerReadbackStatus")) === "verified" &&
    bru.getVar(bVariable("shared", "OwnerReadbackStatus")) === "verified"
  ) {
    bru.setVar("exampleBSetupStatus", "complete");
  }
}

function beginASetup(bru, { now = Date.now(), random = Math.random } = {}) {
  const runId = `${Number(now).toString(36)}-${Math.floor(random() * 1_000_000_000).toString(36)}`;

  [
    "exampleAApplicationId",
    "exampleAApplicationRunId",
    "exampleAClientLastName",
    "exampleAProviderFirmCode",
    "exampleAUfn",
    "exampleASetupStatus",
    "exampleACompletionStatus"
  ].forEach(name => bru.deleteVar(name));

  B_FIXTURE_VARIABLE_SUFFIXES.forEach(suffix => {
    Object.keys(B_FIXTURES).forEach(fixtureName => {
      bru.deleteVar(bVariable(fixtureName, suffix));
    });
  });
  [
    "exampleBSetupStatus",
    "exampleCSharedDetailsETag",
    "exampleCSharedDetailsETagApplicationId",
    "exampleCSharedReadStatus",
    "exampleCSharedUpdateStatus",
    "exampleCSharedUpdatedLastName"
  ].forEach(name => bru.deleteVar(name));

  bru.setVar("exampleDatasetRunId", runId);
  bru.setVar("exampleAUfn", generateUfn(random));
  bru.setVar("exampleASetupStatus", "creating");
  return runId;
}

function captureACompletion(bru, response) {
  const application = response.getBody();
  const meansData = application?.eligibilityAssessment?.data;
  const incomes = meansData?.incomes;
  const isCompletedSetup =
    response.getStatus() === 200 &&
    bru.getVar("exampleASetupStatus") === "created" &&
    application?.id === bru.getVar("exampleAApplicationId") &&
    bru.getVar("exampleAApplicationRunId") === bru.getVar("exampleDatasetRunId") &&
    bru.getVar("exampleAProviderFirmCode") === "997" &&
    application?.providerFirmCode === "997" &&
    application?.providerOfficeCode === "0R125Q" &&
    application?.applicationState === "COMPLETED" &&
    meansData?.level_of_help === "controlled" &&
    Array.isArray(incomes) &&
    incomes.length > 0 &&
    application?.eligibilityAssessment?.result?.version === "6" &&
    application?.evidence?.incomeEvidenceChecklist?.payslips === true &&
    application?.evidence?.expenditureCapitalEvidenceChecklist?.bankStatements === true &&
    application?.declaration?.ufn === bru.getVar("exampleAUfn") &&
    application?.declaration?.declarationConfirmation === true &&
    application?.declaration?.dateSigned === bru.getVar("exampleADeclarationDate");

  bru.setVar("exampleACompletionStatus", isCompletedSetup ? "verified" : "failed");
}

function buildAApplicationCreateBody(bru) {
  const runId = bru.getVar("exampleDatasetRunId");
  if (typeof runId !== "string" || runId.length === 0) {
    throw new Error("Start a new A dataset run before creating its application.");
  }

  const generateApplicationDetailsRequestBody = require("./application-details.js");
  const lastName = `Example${runId.replace(/[^a-z0-9]/gi, "")}`;
  const body = generateApplicationDetailsRequestBody(bru, {
    providerOfficeCode: "0R125Q",
    legalAidLast6Months: false,
    reasonForReapplication: "",
    ecfFlag: false,
    scopingQuestions: { priorLegalAid: "yesSameMatter" },
    clientDetails: {
      firstName: "Synthetic",
      lastName,
      dateOfBirth: "1990-01-01",
      niNumber: undefined,
      hasFixedAddress: false,
      address: undefined
    }
  });

  bru.setVar("exampleAClientLastName", lastName);
  return body;
}

function buildADeclarationBody(bru, signedDate = new Date().toISOString().slice(0, 10)) {
  const ufn = bru.getVar("exampleAUfn");
  if (typeof ufn !== "string" || !/^\d{6}\/\d{3}$/.test(ufn)) {
    throw new Error("Create A's application first to generate its valid office UFN.");
  }

  bru.setVar("exampleADeclarationDate", signedDate);
  return {
    ufn,
    declarationConfirmation: true,
    dateSigned: signedDate
  };
}

function captureAApplicationResponse(bru, response) {
  const runId = bru.getVar("exampleDatasetRunId");
  if (typeof runId !== "string" || runId.length === 0) {
    throw new Error("The A dataset run is missing. Start setup again before recording its response.");
  }

  const applicationId = response.getBody()?.id;
  if (response.getStatus() === 201 && typeof applicationId === "string" && UUID_PATTERN.test(applicationId)) {
    bru.setVar("exampleAApplicationId", applicationId);
    bru.setVar("exampleAApplicationRunId", runId);
    bru.setVar("exampleASetupStatus", "created");
    return;
  }

  bru.deleteVar("exampleAApplicationId");
  bru.deleteVar("exampleAApplicationRunId");
  bru.setVar("exampleASetupStatus", "failed");
}

function captureAApplicationFirm(bru, response) {
  const application = response.getBody();
  const belongsToA =
    response.getStatus() === 200 &&
    application?.id === bru.getVar("exampleAApplicationId") &&
    application?.providerFirmCode === "997" &&
    application?.providerOfficeCode === "0R125Q" &&
    application?.applicationState === "DRAFT" &&
    application?.clientDetails?.firstName === "Synthetic" &&
    application?.clientDetails?.lastName === bru.getVar("exampleAClientLastName");

  if (belongsToA) {
    bru.setVar("exampleAProviderFirmCode", application.providerFirmCode);
  } else {
    bru.deleteVar("exampleAProviderFirmCode");
  }
}

module.exports = {
  assertARequest,
  assertBFixtureRequest,
  assertBRequest,
  assertBOwnerReadbackRequest,
  assertCSharedRequest,
  assertCurrentAApplication,
  assertExpectedOffices,
  assertFixtureReference,
  assertStagingDestination,
  beginBApplicationCreate,
  beginBApplicationRead,
  beginBMeansUpdate,
  beginCSharedRead,
  beginASetup,
  buildAApplicationCreateBody,
  buildADeclarationBody,
  buildBApplicationCreateBody,
  buildBMeansBody,
  buildCSharedDetailsUpdateBody,
  captureAApplicationFirm,
  captureAApplicationResponse,
  captureACompletion,
  captureBApplicationResponse,
  captureBApplicationVerification,
  captureBMeansResponse,
  captureBPopulatedApplication,
  captureBOwnerReadback,
  captureCSharedApplication,
  captureCSharedUpdate,
  generateUfn
};
