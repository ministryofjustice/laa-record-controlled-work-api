const generateApplicationDetailsRequestBody = require("../application-details.js");

function buildSyntheticApplicationCreateBody(bru, { providerOfficeCode, lastName }) {
  return generateApplicationDetailsRequestBody(bru, {
    providerOfficeCode,
    legalAidLast6Months: false,
    reasonForReapplication: "",
    ecfFlag: false,
    scopingQuestions: { priorLegalAid: "yesSameMatter" },
    clientDetails: {
      firstName: "Synthetic",
      lastName,
      dateOfBirth: "1990-01-01",
      niNumber: undefined,
      hasFixedAddress: false,
      address: undefined
    }
  });
}

function applicationHasBMeans(application, fixture) {
  const data = application?.eligibilityAssessment?.data;
  const incomes = data?.incomes;
  return (
    data?.level_of_help === "controlled" &&
    data?.employment_status === "employed" &&
    Array.isArray(incomes) &&
    incomes.length === 1 &&
    incomes[0]?.gross_income === fixture.income &&
    incomes[0]?.income_frequency === "monthly" &&
    application?.eligibilityAssessment?.result?.version === "6"
  );
}

module.exports = {
  applicationHasBMeans,
  buildSyntheticApplicationCreateBody
};
