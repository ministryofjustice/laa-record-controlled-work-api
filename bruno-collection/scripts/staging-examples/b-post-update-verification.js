const { STRONG_ETAG_PATTERN } = require("./constants.js");
const { applicationHasBMeans } = require("./application-contracts.js");
const { assertBFixtureRequest } = require("./b-application-setup.js");
const { bFixture, bVariable, failBSetup } = require("./fixture-state.js");

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

module.exports = {
  assertBOwnerReadbackRequest,
  captureBOwnerReadback
};
