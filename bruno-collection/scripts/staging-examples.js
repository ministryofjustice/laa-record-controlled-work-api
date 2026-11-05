const identity = require("./staging-examples/identity.js");
const aGuards = require("./staging-examples/a-guards.js");
const aSetup = require("./staging-examples/a-setup.js");
const bApplicationSetup = require("./staging-examples/b-application-setup.js");
const bMeans = require("./staging-examples/b-means.js");
const sharedAccess = require("./staging-examples/shared-access.js");
const postUpdateVerification = require("./staging-examples/b-post-update-verification.js");

module.exports = {
  assertARequest: aGuards.assertARequest,
  assertBFixtureRequest: bApplicationSetup.assertBFixtureRequest,
  assertBRequest: bApplicationSetup.assertBRequest,
  assertBPostUpdateVerificationRequest:
    postUpdateVerification.assertBPostUpdateVerificationRequest,
  assertCSharedRequest: sharedAccess.assertCSharedRequest,
  assertCurrentAApplication: aGuards.assertCurrentAApplication,
  assertExpectedOffices: identity.assertExpectedOffices,
  assertFixtureReference: aGuards.assertFixtureReference,
  assertStagingDestination: identity.assertStagingDestination,
  beginBApplicationCreate: bApplicationSetup.beginBApplicationCreate,
  beginBApplicationRead: bApplicationSetup.beginBApplicationRead,
  beginBMeansUpdate: bMeans.beginBMeansUpdate,
  beginCSharedRead: sharedAccess.beginCSharedRead,
  beginASetup: aSetup.beginASetup,
  buildAApplicationCreateBody: aSetup.buildAApplicationCreateBody,
  buildADeclarationBody: aSetup.buildADeclarationBody,
  buildBApplicationCreateBody: bApplicationSetup.buildBApplicationCreateBody,
  buildBMeansBody: bMeans.buildBMeansBody,
  buildCSharedDetailsUpdateBody: sharedAccess.buildCSharedDetailsUpdateBody,
  captureAApplicationFirm: aSetup.captureAApplicationFirm,
  captureAApplicationResponse: aSetup.captureAApplicationResponse,
  captureACompletion: aSetup.captureACompletion,
  captureBApplicationResponse: bApplicationSetup.captureBApplicationResponse,
  captureBApplicationVerification: bApplicationSetup.captureBApplicationVerification,
  captureBMeansResponse: bMeans.captureBMeansResponse,
  captureBPopulatedApplication: bMeans.captureBPopulatedApplication,
  captureBPostUpdateVerification:
    postUpdateVerification.captureBPostUpdateVerification,
  captureCSharedApplication: sharedAccess.captureCSharedApplication,
  captureCSharedUpdate: sharedAccess.captureCSharedUpdate,
  generateUfn: aSetup.generateUfn
};
