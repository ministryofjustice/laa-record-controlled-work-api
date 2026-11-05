const assert = require("node:assert/strict");
const {
  assertARequest,
  assertBFixtureRequest,
  assertBRequest,
  assertBOwnerReadbackRequest,
  assertCSharedRequest,
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
  captureCSharedUpdate
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

const bOfficeCodes = ["0R128U", "0R695K"];
const bToken = accessToken({ LAA_ACCOUNTS: bOfficeCodes });
const cToken = accessToken({ LAA_ACCOUNTS: ["0R695K"] });
const privateApplicationId = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
const sharedApplicationId = "ffffffff-aaaa-4bbb-8ccc-dddddddddddd";

function completedASetupVariables(overrides = {}) {
  return {
    ...fixtureVariables(),
    exampleAProviderFirmCode: "997",
    exampleACompletionStatus: "verified",
    ...overrides
  };
}

function bApplication(id, officeCode, lastName, income) {
  return {
    id,
    providerFirmCode: "998",
    providerOfficeCode: officeCode,
    applicationState: "DRAFT",
    clientDetails: { firstName: "Synthetic", lastName },
    eligibilityAssessment: income === undefined
      ? { data: null, result: null }
      : {
      data: {
        level_of_help: "controlled",
        employment_status: "employed",
        incomes: [{ gross_income: income, income_frequency: "monthly" }]
      },
      result: { version: "6" }
    }
  };
}

function populateBFixture(bru, fixture, id, officeCode, income, etag) {
  beginBApplicationCreate(bru, request, fixture);
  const createBody = buildBApplicationCreateBody(bru, fixture);
  assert.equal(createBody.providerOfficeCode, officeCode);
  assert.equal(createBody.clientDetails.firstName, "Synthetic");
  assert.match(createBody.clientDetails.lastName, /^SyntheticB/);
  assert.match(createBody.clientDetails.lastName, /runcurrent/i);
  captureBApplicationResponse(bru, {
    getStatus: () => 201,
    getBody: () => ({ id })
  }, fixture);
  assert.doesNotThrow(() => assertBFixtureRequest(bru, request, fixture, "created"));

  const prefix = fixture === "private" ? "exampleBPrivate" : "exampleBShared";
  captureBApplicationVerification(bru, {
    getStatus: () => 200,
    getBody: () => bApplication(
      id,
      officeCode,
      bru.getVar(`${prefix}ClientLastName`)
    )
  }, fixture);
  beginBMeansUpdate(bru, request, fixture);
  assert.equal(buildBMeansBody(fixture).data.incomes[0].gross_income, income);
  captureBMeansResponse(bru, { getStatus: () => 204 }, fixture);
  beginBApplicationRead(bru, request, fixture, "means-saved");
  captureBPopulatedApplication(bru, {
    getStatus: () => 200,
    getHeader: name => name === "ETag" ? etag : undefined,
    getBody: () => bApplication(
      id,
      officeCode,
      bru.getVar(`${prefix}ClientLastName`),
      income
    )
  }, fixture);
}

const bContext = createBrunoContext({
  token: bToken,
  initialVariables: completedASetupVariables()
});
assert.doesNotThrow(() => assertBRequest(bContext, request));
[
  createBrunoContext({
    token: accessToken({ LAA_ACCOUNTS: ["0R695K"] }),
    initialVariables: completedASetupVariables()
  }),
  createBrunoContext({
    token: accessToken({ LAA_ACCOUNTS: ["0R128U", "0R695K", "0R125Q"] }),
    initialVariables: completedASetupVariables()
  }),
  createBrunoContext({
    token: bToken,
    environmentName: "uat",
    initialVariables: completedASetupVariables()
  }),
  createBrunoContext({
    token: bToken,
    initialVariables: completedASetupVariables({ exampleACompletionStatus: undefined })
  })
].forEach(bru => assert.throws(() => assertBRequest(bru, request)));

assert.throws(
  () =>
    assertBRequest(
      createBrunoContext({
        token: accessToken({ LAA_ACCOUNTS: ["0R695K"] }),
        initialVariables: completedASetupVariables()
      }),
      request
    ),
  error => {
    assert.doesNotMatch(error.message, /0R128U|0R695K|header\./);
    return true;
  }
);

const incompleteASetup = createBrunoContext({
  initialVariables: completedASetupVariables({
    exampleACompletionStatus: undefined,
    exampleAUfn: "123456/789",
    exampleADeclarationDate: "2026-11-05"
  })
});
captureACompletion(incompleteASetup, {
  getStatus: () => 200,
  getBody: () => ({
    id: applicationId,
    providerFirmCode: "997",
    providerOfficeCode: "0R125Q",
    applicationState: "COMPLETED",
    eligibilityAssessment: {
      data: { level_of_help: "controlled", incomes: [{ gross_income: 1200 }] },
      result: { version: "6" }
    },
    evidence: {
      incomeEvidenceChecklist: { payslips: true },
      expenditureCapitalEvidenceChecklist: { bankStatements: true }
    },
    declaration: {
      ufn: "123456/789",
      declarationConfirmation: true,
      dateSigned: "2026-11-05"
    }
  })
});
assert.equal(incompleteASetup.getVar("exampleACompletionStatus"), "verified");
assert.doesNotThrow(() =>
  assertBRequest(
    createBrunoContext({
      token: bToken,
      initialVariables: completedASetupVariables({
        exampleACompletionStatus: incompleteASetup.getVar("exampleACompletionStatus")
      })
    }),
    request
  )
);

const newDataset = createBrunoContext({
  initialVariables: completedASetupVariables({
    exampleBSetupStatus: "complete",
    exampleBPrivateApplicationId: privateApplicationId,
    exampleBPrivateApplicationRunId: "run-current",
    exampleCSharedDetailsETag: '"3"',
    exampleCSharedDetailsETagApplicationId: sharedApplicationId
  })
});
beginASetup(newDataset, { now: 1_767_671_180_002, random: () => 0.323456789 });
[
  "exampleBSetupStatus",
  "exampleBPrivateApplicationId",
  "exampleBPrivateApplicationRunId",
  "exampleCSharedDetailsETag",
  "exampleCSharedDetailsETagApplicationId",
  "exampleACompletionStatus"
].forEach(name => assert.equal(newDataset.getVar(name), undefined));

populateBFixture(bContext, "private", privateApplicationId, "0R128U", 1100, '"7"');
populateBFixture(bContext, "shared", sharedApplicationId, "0R695K", 1300, '"9"');
assert.equal(bContext.getVar("exampleBPrivateApplicationId"), privateApplicationId);
assert.equal(bContext.getVar("exampleBSharedApplicationId"), sharedApplicationId);
assert.notEqual(
  bContext.getVar("exampleBPrivateClientLastName"),
  bContext.getVar("exampleBSharedClientLastName")
);
assert.equal(bContext.getVar("exampleBPrivateApplicationRunId"), "run-current");
assert.equal(bContext.getVar("exampleBSharedApplicationRunId"), "run-current");
assert.equal(bContext.getVar("exampleBPrivateDetailsETagApplicationId"), privateApplicationId);
assert.equal(bContext.getVar("exampleBSharedDetailsETagApplicationId"), sharedApplicationId);
assert.equal(bContext.getVar("exampleBSetupStatus"), "ready");

const partialSetup = createBrunoContext({
  token: bToken,
  initialVariables: completedASetupVariables()
});
populateBFixture(partialSetup, "private", privateApplicationId, "0R128U", 1100, '"7"');
beginBApplicationCreate(partialSetup, request, "shared");
captureBApplicationResponse(partialSetup, {
  getStatus: () => 500,
  getBody: () => ({})
}, "shared");
assert.equal(partialSetup.getVar("exampleBPrivateApplicationId"), privateApplicationId);
assert.equal(partialSetup.getVar("exampleBSetupStatus"), "failed");
assert.throws(() => assertBRequest(partialSetup, request), /failed/i);
assert.throws(() => beginBApplicationCreate(partialSetup, request, "shared"), /failed/i);

const sharedFixtureVariables = {
  ...completedASetupVariables(),
  exampleBSetupStatus: "ready",
  exampleBPrivateApplicationId: privateApplicationId,
  exampleBPrivateApplicationRunId: "run-current",
  exampleBPrivateSetupStatus: "populated",
  exampleBPrivateProviderFirmCode: bContext.getVar("exampleBPrivateProviderFirmCode"),
  exampleBSharedApplicationId: sharedApplicationId,
  exampleBSharedApplicationRunId: "run-current",
  exampleBSharedSetupStatus: "populated",
  exampleBSharedProviderFirmCode: bContext.getVar("exampleBSharedProviderFirmCode"),
  exampleBSharedClientLastName: bContext.getVar("exampleBSharedClientLastName"),
  exampleBSharedMeansIncome: 1300,
  exampleBSharedOwnerObservedState: "DRAFT",
  exampleBSharedOwnerObservedMeansIncome: 1300
};
const cContext = createBrunoContext({
  token: cToken,
  initialVariables: {
    ...sharedFixtureVariables,
    exampleCSharedDetailsETag: '"3"',
    exampleCSharedDetailsETagApplicationId: privateApplicationId,
    exampleCSharedReadStatus: "verified"
  }
});
assert.doesNotThrow(() => assertCSharedRequest(cContext, request));
assert.throws(() => assertCSharedRequest(bContext, request));

beginCSharedRead(cContext, request);
assert.equal(cContext.getVar("exampleCSharedDetailsETag"), undefined);
assert.equal(cContext.getVar("exampleCSharedReadStatus"), undefined);
captureCSharedApplication(cContext, {
  getStatus: () => 200,
  getHeader: name => name === "ETag" ? '"10"' : undefined,
  getBody: () => bApplication(
    sharedApplicationId,
    "0R695K",
    cContext.getVar("exampleBSharedClientLastName"),
    1300
  )
});
assert.doesNotThrow(() => assertCSharedRequest(cContext, request, true));
const cUpdateBody = buildCSharedDetailsUpdateBody(cContext);
assert.equal(cUpdateBody.clientDetails.firstName, "Synthetic");
assert.notEqual(
  cUpdateBody.clientDetails.lastName,
  cContext.getVar("exampleBSharedClientLastName")
);
assert.match(cUpdateBody.clientDetails.lastName, /^SyntheticC/);
captureCSharedUpdate(cContext, {
  getStatus: () => 204,
  getHeader: name => name === "ETag" ? '"11"' : undefined
});
assert.equal(cContext.getVar("exampleCSharedUpdateStatus"), "updated");
assert.equal(cContext.getVar("exampleCSharedDetailsETag"), undefined);

const ownerContext = createBrunoContext({
  token: bToken,
  initialVariables: {
    ...sharedFixtureVariables,
    exampleBPrivateClientLastName: bContext.getVar("exampleBPrivateClientLastName"),
    exampleBPrivateMeansIncome: 1100,
    exampleCSharedUpdateStatus: "updated",
    exampleCSharedUpdatedLastName: cContext.getVar("exampleCSharedUpdatedLastName")
  }
});
assert.doesNotThrow(() => assertBOwnerReadbackRequest(ownerContext, request, "private"));
captureBOwnerReadback(ownerContext, {
  getStatus: () => 200,
  getHeader: name => name === "ETag" ? '"12"' : undefined,
  getBody: () => bApplication(
    privateApplicationId,
    "0R128U",
    ownerContext.getVar("exampleBPrivateClientLastName"),
    1100
  )
}, "private");
captureBOwnerReadback(ownerContext, {
  getStatus: () => 200,
  getHeader: name => name === "ETag" ? '"13"' : undefined,
  getBody: () => bApplication(
    sharedApplicationId,
    "0R695K",
    ownerContext.getVar("exampleCSharedUpdatedLastName"),
    1300
  )
}, "shared");
assert.equal(ownerContext.getVar("exampleBPrivateOwnerObservedState"), "DRAFT");
assert.equal(ownerContext.getVar("exampleBSharedOwnerObservedState"), "DRAFT");
assert.equal(ownerContext.getVar("exampleBSetupStatus"), "complete");
