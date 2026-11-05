const STAGING_ORIGIN =
  "https://laa-record-controlled-work-api-staging.cloud-platform.service.justice.gov.uk";
const OAUTH_ACCESS_TOKEN = "$oauth2.applications-token.access_token";
const A_OFFICE_CODES = ["0R125Q"];
const B_OFFICE_CODES = ["0R128U", "0R695K"];
const C_OFFICE_CODES = ["0R695K"];
const B_FIXTURES = {
  private: { officeCode: "0R128U", income: 1100, label: "Private" },
  shared: { officeCode: "0R695K", income: 1300, label: "Shared" }
};
const B_FIXTURE_VARIABLE_SUFFIXES = [
  "ApplicationId",
  "ApplicationRunId",
  "SetupStatus",
  "ClientLastName",
  "ProviderFirmCode",
  "MeansIncome",
  "OwnerObservedState",
  "OwnerObservedMeansIncome",
  "OwnerObservedClientLastName",
  "DetailsETag",
  "DetailsETagApplicationId",
  "OwnerReadbackStatus"
];
const UUID_PATTERN = /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i;
const STRONG_ETAG_PATTERN = /^"\d+"$/;

module.exports = {
  A_OFFICE_CODES,
  B_FIXTURES,
  B_FIXTURE_VARIABLE_SUFFIXES,
  B_OFFICE_CODES,
  C_OFFICE_CODES,
  OAUTH_ACCESS_TOKEN,
  STAGING_ORIGIN,
  STRONG_ETAG_PATTERN,
  UUID_PATTERN
};
