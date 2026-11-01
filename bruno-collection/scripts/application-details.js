function generateApplicationDetailsRequestBody(bru, overrides = {}) {
  const { scopingQuestions = {}, clientDetails = {}, ...detailsOverrides } = overrides;

  return {
    scopingQuestions: {
      priorLegalAid: "no",
      familyLawClassification: "private",
      needsAdviceOnEUOrInternationalMaintenance: false,
      needsProtectiveInjunction: false,
      laspoEvidenceScope: "domesticAbuse",
      caseMeetsMeritsCriteria: true,
      ...scopingQuestions
    },
    legalAidLast6Months: false,
    reasonForReapplication: null,
    ecfFlag: true,
    clientDetails: {
      firstName: bru.interpolate("{{$randomFirstName}}"),
      lastName: bru.interpolate("{{$randomLastName}}"),
      dateOfBirth: "1990-01-01",
      niNumber: null,
      hasFixedAddress: false,
      address: null,
      ...clientDetails
    },
    ...detailsOverrides
  };
}

module.exports = generateApplicationDetailsRequestBody;
