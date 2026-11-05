const { STRONG_ETAG_PATTERN } = require("./constants.js");
const { applicationHasBMeans } = require("./application-contracts.js");
const { assertBFixtureRequest } = require("./b-application-setup.js");
const { bFixture, bVariable, failBSetup } = require("./fixture-state.js");

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

module.exports = {
  beginBMeansUpdate,
  buildBMeansBody,
  captureBMeansResponse,
  captureBPopulatedApplication
};
