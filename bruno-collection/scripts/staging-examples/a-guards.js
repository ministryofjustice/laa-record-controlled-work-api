const {
  A_OFFICE_CODES,
  OAUTH_ACCESS_TOKEN,
  UUID_PATTERN
} = require("./constants.js");
const {
  assertExpectedOffices,
  assertStagingDestination
} = require("./identity.js");

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

function assertACompletedSetup(bru) {
  assertCurrentAApplication(bru);
  if (
    bru.getVar("exampleAProviderFirmCode") !== "997" ||
    bru.getVar("exampleACompletionStatus") !== "verified"
  ) {
    throw new Error("Complete and verify A's application before starting B's setup.");
  }
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

module.exports = {
  assertACompletedSetup,
  assertARequest,
  assertCurrentAApplication,
  assertFixtureReference
};
