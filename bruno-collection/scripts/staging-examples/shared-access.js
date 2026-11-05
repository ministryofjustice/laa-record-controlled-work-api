const {
  B_FIXTURES,
  C_OFFICE_CODES,
  OAUTH_ACCESS_TOKEN,
  STRONG_ETAG_PATTERN
} = require("./constants.js");
const {
  assertExpectedOffices,
  assertStagingDestination
} = require("./identity.js");
const { assertACompletedSetup } = require("./a-guards.js");
const { assertBFixtureReference } = require("./b-application-setup.js");
const { applicationHasBMeans } = require("./application-contracts.js");
const generateApplicationDetailsRequestBody = require("../application-details.js");

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
    throw new Error("C's update is complete. Switch back to B to verify both drafts.");
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
      niNumber: null,
      hasFixedAddress: false,
      address: null
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

module.exports = {
  assertCSharedRequest,
  beginCSharedRead,
  buildCSharedDetailsUpdateBody,
  captureCSharedApplication,
  captureCSharedUpdate
};
