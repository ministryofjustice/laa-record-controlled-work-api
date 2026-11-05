const {
  B_OFFICE_CODES,
  OAUTH_ACCESS_TOKEN,
  UUID_PATTERN
} = require("./constants.js");
const {
  assertExpectedOffices,
  assertStagingDestination
} = require("./identity.js");
const { assertACompletedSetup } = require("./a-guards.js");
const { buildSyntheticApplicationCreateBody } = require("./application-contracts.js");
const { bFixture, bVariable, failBSetup } = require("./fixture-state.js");

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
  const body = buildSyntheticApplicationCreateBody(bru, {
    providerOfficeCode: fixture.officeCode,
    lastName
  });

  bru.setVar(bVariable(fixtureName, "ClientLastName"), lastName);
  return body;
}

function captureBApplicationResponse(bru, response, fixtureName) {
  bFixture(fixtureName);
  const runId = bru.getVar("exampleDatasetRunId");
  const applicationId = response.getBody()?.id;
  const location = response.getHeader("Location");
  const wasCreated =
    bru.getVar(bVariable(fixtureName, "SetupStatus")) === "creating" &&
    typeof runId === "string" &&
    runId.length > 0 &&
    response.getStatus() === 201 &&
    UUID_PATTERN.test(applicationId || "");

  if (wasCreated) {
    bru.setVar(bVariable(fixtureName, "ApplicationId"), applicationId);
    bru.setVar(bVariable(fixtureName, "ApplicationRunId"), runId);
  }

  if (
    wasCreated &&
    typeof location === "string" &&
    location.endsWith(`/api/v1/applications/${applicationId}`)
  ) {
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

module.exports = {
  assertBFixtureRequest,
  assertBFixtureReference,
  assertBRequest,
  beginBApplicationCreate,
  beginBApplicationRead,
  buildBApplicationCreateBody,
  captureBApplicationResponse,
  captureBApplicationVerification
};
