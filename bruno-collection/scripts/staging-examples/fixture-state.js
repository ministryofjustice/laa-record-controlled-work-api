const {
  B_FIXTURES,
  B_FIXTURE_VARIABLE_SUFFIXES
} = require("./constants.js");

function bFixture(fixtureName) {
  if (!Object.prototype.hasOwnProperty.call(B_FIXTURES, fixtureName)) {
    throw new Error("Select the private-office or shared-office B fixture.");
  }

  return B_FIXTURES[fixtureName];
}

function bVariable(fixtureName, suffix) {
  const prefix = fixtureName === "private" ? "Private" : "Shared";
  return `exampleB${prefix}${suffix}`;
}

function failBSetup(bru, fixtureName) {
  bru.setVar(bVariable(fixtureName, "SetupStatus"), "failed");
  bru.setVar("exampleBSetupStatus", "failed");
  [
    "DetailsETag",
    "DetailsETagApplicationId"
  ].forEach(suffix => bru.deleteVar(bVariable(fixtureName, suffix)));
}

function resetBAndCFixtureState(bru) {
  B_FIXTURE_VARIABLE_SUFFIXES.forEach(suffix => {
    Object.keys(B_FIXTURES).forEach(fixtureName => {
      bru.deleteVar(bVariable(fixtureName, suffix));
    });
  });
  [
    "exampleBSetupStatus",
    "exampleCSharedDetailsETag",
    "exampleCSharedDetailsETagApplicationId",
    "exampleCSharedReadStatus",
    "exampleCSharedUpdateStatus",
    "exampleCSharedUpdatedLastName"
  ].forEach(name => bru.deleteVar(name));
}

module.exports = {
  bFixture,
  bVariable,
  failBSetup,
  resetBAndCFixtureState
};
