const STAGING_ORIGIN =
  "https://laa-record-controlled-work-api-staging.cloud-platform.service.justice.gov.uk";
const OAUTH_ACCESS_TOKEN = "$oauth2.applications-token.access_token";
const A_OFFICE_CODES = ["0R125Q"];
const UUID_PATTERN = /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i;

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

function beginASetup(bru, { now = Date.now(), random = Math.random } = {}) {
  const runId = `${Number(now).toString(36)}-${Math.floor(random() * 1_000_000_000).toString(36)}`;

  [
    "exampleAApplicationId",
    "exampleAApplicationRunId",
    "exampleAClientLastName",
    "exampleAProviderFirmCode",
    "exampleAUfn",
    "exampleASetupStatus"
  ].forEach(name => bru.deleteVar(name));

  bru.setVar("exampleDatasetRunId", runId);
  bru.setVar("exampleAUfn", generateUfn(random));
  bru.setVar("exampleASetupStatus", "creating");
  return runId;
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
  assertCurrentAApplication,
  assertExpectedOffices,
  assertFixtureReference,
  assertStagingDestination,
  beginASetup,
  buildAApplicationCreateBody,
  buildADeclarationBody,
  captureAApplicationFirm,
  captureAApplicationResponse,
  generateUfn
};
