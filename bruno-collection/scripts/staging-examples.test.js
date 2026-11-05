const assert = require("node:assert/strict");
const {
  assertARequest,
  assertCurrentAApplication,
  beginASetup,
  assertExpectedOffices,
  assertFixtureReference,
  assertStagingDestination,
  buildAApplicationCreateBody,
  buildADeclarationBody,
  captureAApplicationFirm,
  captureAApplicationResponse
} = require("./staging-examples.js");

const stagingUrl =
  "https://laa-record-controlled-work-api-staging.cloud-platform.service.justice.gov.uk/api/v1/applications";

assert.throws(
  () => assertStagingDestination("uat", stagingUrl),
  /staging environment/i
);
assert.throws(
  () => assertStagingDestination("staging", "http://localhost:8081/api/v1/applications"),
  /staging destination/i
);
assert.doesNotThrow(() => assertStagingDestination("staging", stagingUrl));

function accessToken(claims) {
  const encodedClaims = Buffer.from(JSON.stringify(claims)).toString("base64url");
  return `header.${encodedClaims}.signature`;
}

assert.throws(
  () => assertExpectedOffices(undefined, ["0R125Q"]),
  /identity/i
);
assert.throws(
  () => assertExpectedOffices(accessToken({}), ["0R125Q"]),
  /office/i
);
assert.throws(
  () => assertExpectedOffices(accessToken({ LAA_ACCOUNTS: ["0R125Q", "0R128U"] }), ["0R125Q"]),
  /office/i
);
assert.doesNotThrow(() =>
  assertExpectedOffices(accessToken({ LAA_ACCOUNTS: ["0R125Q"] }), ["0R125Q"])
);

const applicationId = "11111111-2222-4333-8444-555555555555";
assert.throws(
  () => assertFixtureReference("", applicationId, "", "created"),
  /dataset/i
);
assert.throws(
  () => assertFixtureReference("run-current", applicationId, "run-previous", "created"),
  /dataset/i
);
assert.throws(
  () => assertFixtureReference("run-current", "", "run-current", "created"),
  /dataset/i
);
assert.doesNotThrow(() =>
  assertFixtureReference("run-current", applicationId, "run-current", "created")
);

function createVariables(initialValues = {}) {
  const values = new Map(Object.entries(initialValues));
  return {
    values,
    getVar: name => values.get(name),
    setVar: (name, value) => values.set(name, value),
    deleteVar: name => values.delete(name),
    interpolate: value => value
  };
}

const variables = createVariables({
  exampleDatasetRunId: "run-old",
  exampleAApplicationId: applicationId,
  exampleAApplicationRunId: "run-old",
  exampleASetupStatus: "created"
});
const runId = beginASetup(variables, {
  now: 1_767_671_180_000,
  random: () => 0.123456789
});

assert.notEqual(runId, "run-old");
assert.equal(variables.getVar("exampleASetupStatus"), "creating");
assert.equal(variables.values.has("exampleAApplicationId"), false);
assert.equal(variables.values.has("exampleAApplicationRunId"), false);
assert.throws(() => assertCurrentAApplication(variables), /dataset/i);
assert.match(variables.getVar("exampleAUfn"), /^\d{6}\/\d{3}$/);

const createBody = buildAApplicationCreateBody(variables);
assert.equal(createBody.providerOfficeCode, "0R125Q");
assert.equal(createBody.clientDetails.firstName, "Synthetic");
assert.match(createBody.clientDetails.lastName, /^Example/);
assert.equal(createBody.clientDetails.hasFixedAddress, false);
assert.equal(createBody.scopingQuestions.priorLegalAid, "yesSameMatter");
assert.equal(createBody.ecfFlag, false);

captureAApplicationResponse(variables, {
  getStatus: () => 201,
  getBody: () => ({ id: applicationId })
});
assert.equal(variables.getVar("exampleAApplicationId"), applicationId);
assert.equal(variables.getVar("exampleAApplicationRunId"), runId);
assert.equal(variables.getVar("exampleASetupStatus"), "created");

beginASetup(variables, {
  now: 1_767_671_180_001,
  random: () => 0.223456789
});
captureAApplicationResponse(variables, {
  getStatus: () => 500,
  getBody: () => ({})
});
assert.equal(variables.getVar("exampleASetupStatus"), "failed");
assert.equal(variables.values.has("exampleAApplicationId"), false);

function createBrunoContext(overrides = {}, fixtureVariables = createVariables()) {
  return {
    getEnvName: () => overrides.environmentName ?? "staging",
    getOauth2CredentialVar: () => overrides.accessToken ?? accessToken({ LAA_ACCOUNTS: ["0R125Q"] }),
    getVar: fixtureVariables.getVar
  };
}

const request = { getUrl: () => stagingUrl };
assert.doesNotThrow(() => assertARequest(createBrunoContext(), request, false));
assert.doesNotThrow(() =>
  assertARequest(
    createBrunoContext({}, createVariables({
      exampleDatasetRunId: "run-current",
      exampleAApplicationId: applicationId,
      exampleAApplicationRunId: "run-current",
      exampleASetupStatus: "created"
    })),
    request,
    true
  )
);
assert.throws(
  () => assertARequest(createBrunoContext({ environmentName: "uat" }), request, false),
  /staging environment/i
);
assert.throws(
  () => assertARequest(
    createBrunoContext({
      accessToken: accessToken({ LAA_ACCOUNTS: ["0R128U"] })
    }),
    request,
    false
  ),
  /provider-office access/i
);
assert.throws(
  () => assertARequest(createBrunoContext(), request, true),
  /dataset/i
);

const verifiedVariables = createVariables({
  exampleDatasetRunId: "run-current",
  exampleAApplicationId: applicationId,
  exampleAApplicationRunId: "run-current",
  exampleASetupStatus: "created",
  exampleAClientLastName: "ExampleRunCurrent"
});
captureAApplicationFirm(verifiedVariables, {
  getStatus: () => 200,
  getBody: () => ({
    id: applicationId,
    providerFirmCode: "997",
    providerOfficeCode: "0R125Q",
    applicationState: "DRAFT",
    clientDetails: {
      firstName: "Synthetic",
      lastName: "ExampleRunCurrent"
    }
  })
});
assert.equal(verifiedVariables.getVar("exampleAProviderFirmCode"), "997");
assert.doesNotThrow(() =>
  assertARequest(
    createBrunoContext({}, verifiedVariables),
    request,
    true,
    true
  )
);
captureAApplicationFirm(verifiedVariables, {
  getStatus: () => 200,
  getBody: () => ({
    id: applicationId,
    providerFirmCode: "997",
    providerOfficeCode: "0R125Q",
    applicationState: "DRAFT",
    clientDetails: {
      firstName: "Synthetic",
      lastName: "ExampleOldRun"
    }
  })
});
assert.equal(verifiedVariables.values.has("exampleAProviderFirmCode"), false);
captureAApplicationFirm(verifiedVariables, {
  getStatus: () => 200,
  getBody: () => ({
    id: applicationId,
    providerFirmCode: "998",
    providerOfficeCode: "0R125Q",
    applicationState: "DRAFT"
  })
});
assert.equal(verifiedVariables.values.has("exampleAProviderFirmCode"), false);
assert.throws(
  () =>
    assertARequest(
      createBrunoContext({}, verifiedVariables),
      request,
      true,
      true
    ),
  /firm/i
);
const completedVariables = createVariables({
  exampleAApplicationId: applicationId
});
captureAApplicationFirm(completedVariables, {
  getStatus: () => 200,
  getBody: () => ({
    id: applicationId,
    providerFirmCode: "997",
    providerOfficeCode: "0R125Q",
    applicationState: "COMPLETED"
  })
});
assert.equal(completedVariables.values.has("exampleAProviderFirmCode"), false);

const declarationVariables = createVariables({ exampleAUfn: "123456/789" });
assert.deepEqual(
  buildADeclarationBody(declarationVariables, "2026-11-05"),
  {
    ufn: "123456/789",
    declarationConfirmation: true,
    dateSigned: "2026-11-05"
  }
);
assert.equal(declarationVariables.getVar("exampleADeclarationDate"), "2026-11-05");
