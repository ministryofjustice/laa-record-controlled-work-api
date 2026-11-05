const { UUID_PATTERN } = require("./constants.js");
const { resetBAndCFixtureState } = require("./fixture-state.js");
const { buildSyntheticApplicationCreateBody } = require("./application-contracts.js");

function generateUfn(random = Math.random) {
  const digits = Math.floor(random() * 1_000_000_000)
    .toString()
    .padStart(9, "0");

  return `${digits.slice(0, 6)}/${digits.slice(6)}`;
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

  resetBAndCFixtureState(bru);
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

  const lastName = `Example${runId.replace(/[^a-z0-9]/gi, "")}`;
  const body = buildSyntheticApplicationCreateBody(bru, {
    providerOfficeCode: "0R125Q",
    lastName
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
  beginASetup,
  buildAApplicationCreateBody,
  buildADeclarationBody,
  captureAApplicationFirm,
  captureAApplicationResponse,
  captureACompletion,
  generateUfn
};
