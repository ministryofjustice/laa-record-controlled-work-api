const assert = require("node:assert/strict");
const generateApplicationDetailsRequestBody = require("./application-details.js");

const requestBody = generateApplicationDetailsRequestBody({
  interpolate: (value) => value
}, {
  scopingQuestions: {
    priorLegalAid: "yesSameMatter"
  },
  clientDetails: {
    firstName: "Synthetic"
  },
  ecfFlag: false
});

assert.equal(requestBody.scopingQuestions.priorLegalAid, "yesSameMatter");
assert.equal(requestBody.scopingQuestions.familyLawClassification, "private");
assert.equal(requestBody.clientDetails.firstName, "Synthetic");
assert.equal(requestBody.clientDetails.lastName, "{{$randomLastName}}");
assert.equal(requestBody.ecfFlag, false);

const createRequestBody = generateApplicationDetailsRequestBody({
  interpolate: (value) => value,
}, {
  providerOfficeCode: "AB12CD",
  legalAidLast6Months: false,
  reasonForReapplication: "",
  ecfFlag: false,
  scopingQuestions: {
    priorLegalAid: "yesSameMatter"
  },
  clientDetails: {
    niNumber: undefined,
    hasFixedAddress: false,
    address: undefined
  }
});
const serializedCreateRequestBody = JSON.parse(JSON.stringify(createRequestBody));

assert.equal(serializedCreateRequestBody.providerOfficeCode, "AB12CD");
assert.equal(serializedCreateRequestBody.reasonForReapplication, "");
assert.equal(serializedCreateRequestBody.scopingQuestions.priorLegalAid, "yesSameMatter");
assert.equal(serializedCreateRequestBody.clientDetails.hasFixedAddress, false);
assert.equal("niNumber" in serializedCreateRequestBody.clientDetails, false);
assert.equal("address" in serializedCreateRequestBody.clientDetails, false);
