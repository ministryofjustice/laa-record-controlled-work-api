const assert = require("node:assert/strict");
const {
  assertARequest,
  beginASetup,
  buildAApplicationCreateBody,
  buildADeclarationBody,
  captureAApplicationFirm,
  captureAApplicationResponse
} = require("./staging-examples.js");

const stagingUrl =
  "https://laa-record-controlled-work-api-staging.cloud-platform.service.justice.gov.uk/api/v1/applications";
const applicationId = "11111111-2222-4333-8444-555555555555";

function accessToken(claims) {
  const payload = Buffer.from(JSON.stringify(claims)).toString("base64url");
  return `header.${payload}.signature`;
}

function createBrunoContext({
  environmentName = "staging",
  token = accessToken({ LAA_ACCOUNTS: ["0R125Q"] }),
  initialVariables = {}
} = {}) {
  const values = new Map(Object.entries(initialVariables));
  return {
    getEnvName: () => environmentName,
    getOauth2CredentialVar: () => token,
    getVar: name => values.get(name),
    setVar: (name, value) => values.set(name, value),
    deleteVar: name => values.delete(name),
    interpolate: value => value
  };
}

function createRequest(url = stagingUrl) {
  return { getUrl: () => url };
}

function fixtureVariables(overrides = {}) {
  return {
    exampleDatasetRunId: "run-current",
    exampleAApplicationId: applicationId,
    exampleAApplicationRunId: "run-current",
    exampleASetupStatus: "created",
    exampleAClientLastName: "ExampleRunCurrent",
    ...overrides
  };
}

const request = createRequest();
assert.doesNotThrow(() => assertARequest(createBrunoContext(), request, false));

[
  [createBrunoContext({ environmentName: "uat" }), request],
  [createBrunoContext(), createRequest("http://localhost:8081/api/v1/applications")],
  [createBrunoContext({ token: null }), request],
  [createBrunoContext({ token: accessToken({}) }), request],
  [createBrunoContext({ token: accessToken({ LAA_ACCOUNTS: ["0R128U"] }) }), request],
  [
    createBrunoContext({
      token: accessToken({ LAA_ACCOUNTS: ["0R125Q", "0R128U"] })
    }),
    request
  ]
].forEach(([bru, target]) => {
  assert.throws(() => assertARequest(bru, target, false));
});

assert.doesNotThrow(() =>
  assertARequest(
    createBrunoContext({ initialVariables: fixtureVariables() }),
    request,
    true
  )
);
[
  {},
  fixtureVariables({ exampleAApplicationRunId: "run-old" }),
  fixtureVariables({ exampleASetupStatus: "creating" })
].forEach(initialVariables => {
  assert.throws(
    () => assertARequest(createBrunoContext({ initialVariables }), request, true),
    /dataset/i
  );
});
assert.throws(
  () =>
    assertARequest(
      createBrunoContext({ initialVariables: fixtureVariables() }),
      request,
      true,
      true
    ),
  /firm/i
);

const setup = createBrunoContext({
  initialVariables: fixtureVariables({
    exampleAProviderFirmCode: "997",
    exampleAUfn: "123456/789"
  })
});
const runId = beginASetup(setup, {
  now: 1_767_671_180_000,
  random: () => 0.123456789
});
assert.notEqual(runId, "run-current");
assert.equal(setup.getVar("exampleASetupStatus"), "creating");
assert.equal(setup.getVar("exampleAApplicationId"), undefined);
assert.throws(() => assertARequest(setup, request, true), /dataset/i);
assert.match(setup.getVar("exampleAUfn"), /^\d{6}\/\d{3}$/);

const createBody = buildAApplicationCreateBody(setup);
assert.equal(createBody.providerOfficeCode, "0R125Q");
assert.equal(createBody.clientDetails.firstName, "Synthetic");
assert.match(createBody.clientDetails.lastName, /^Example/);
assert.equal(createBody.clientDetails.hasFixedAddress, false);

assert.deepEqual(buildADeclarationBody(setup, "2026-11-05"), {
  ufn: setup.getVar("exampleAUfn"),
  declarationConfirmation: true,
  dateSigned: "2026-11-05"
});

captureAApplicationResponse(setup, {
  getStatus: () => 201,
  getBody: () => ({ id: applicationId })
});
assert.doesNotThrow(() => assertARequest(setup, request, true));

captureAApplicationFirm(setup, {
  getStatus: () => 200,
  getBody: () => ({
    id: applicationId,
    providerFirmCode: "997",
    providerOfficeCode: "0R125Q",
    applicationState: "DRAFT",
    clientDetails: {
      firstName: "Synthetic",
      lastName: setup.getVar("exampleAClientLastName")
    }
  })
});
assert.doesNotThrow(() => assertARequest(setup, request, true, true));

captureAApplicationFirm(setup, {
  getStatus: () => 200,
  getBody: () => ({
    id: applicationId,
    providerFirmCode: "998",
    providerOfficeCode: "0R125Q",
    applicationState: "DRAFT",
    clientDetails: {
      firstName: "Synthetic",
      lastName: setup.getVar("exampleAClientLastName")
    }
  })
});
assert.throws(() => assertARequest(setup, request, true, true), /firm/i);

beginASetup(setup, {
  now: 1_767_671_180_001,
  random: () => 0.223456789
});
captureAApplicationResponse(setup, {
  getStatus: () => 500,
  getBody: () => ({})
});
assert.equal(setup.getVar("exampleASetupStatus"), "failed");
assert.throws(() => assertARequest(setup, request, true), /dataset/i);
