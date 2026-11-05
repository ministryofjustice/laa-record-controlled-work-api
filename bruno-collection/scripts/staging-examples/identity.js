const { OAUTH_ACCESS_TOKEN, STAGING_ORIGIN } = require("./constants.js");

function assertStagingDestination(environmentName, requestUrl) {
  if (environmentName !== "staging") {
    throw new Error("Select the staging environment before sending this example request.");
  }

  let destination;
  try {
    destination = new URL(requestUrl);
  } catch {
    throw new Error("This example request must target the configured staging destination.");
  }

  if (destination.origin !== STAGING_ORIGIN || destination.username || destination.password) {
    throw new Error("This example request must target the configured staging destination.");
  }
}

function decodeAccessTokenClaims(accessToken) {
  if (typeof accessToken !== "string" || accessToken.length === 0) {
    throw new Error("The active identity could not be checked. Sign in through the applications folder.");
  }

  const tokenParts = accessToken.split(".");
  if (tokenParts.length !== 3 || tokenParts[1].length === 0) {
    throw new Error("The active identity could not be checked. Sign in again before sending.");
  }

  try {
    const claims = JSON.parse(Buffer.from(tokenParts[1], "base64url").toString("utf8"));
    if (!claims || typeof claims !== "object" || Array.isArray(claims)) {
      throw new Error();
    }

    return claims;
  } catch {
    throw new Error("The active identity could not be checked. Sign in again before sending.");
  }
}

function assertExpectedOffices(accessToken, expectedOfficeCodes) {
  const claims = decodeAccessTokenClaims(accessToken);
  const actualOfficeCodes = claims.LAA_ACCOUNTS;
  const expectedOffices = [...expectedOfficeCodes].sort();
  const isExactOfficeSet =
    Array.isArray(actualOfficeCodes) &&
    actualOfficeCodes.length === expectedOfficeCodes.length &&
    actualOfficeCodes.every(code => typeof code === "string") &&
    [...actualOfficeCodes]
      .sort()
      .every((code, index) => code === expectedOffices[index]);

  if (!isExactOfficeSet) {
    throw new Error("The active identity does not have the expected provider-office access.");
  }
}

module.exports = {
  assertExpectedOffices,
  assertStagingDestination
};
