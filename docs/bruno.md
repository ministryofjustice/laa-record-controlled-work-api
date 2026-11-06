# Bruno Collection

[Bruno](https://www.usebruno.com/) is an offline-first API client. Open the repository's
`bruno-collection` folder in the Bruno desktop app.

## Environments

Select an environment from Bruno's dropdown before sending requests:

| Environment   | API                     | Authentication                     |
| ------------- | ----------------------- | ---------------------------------- |
| `local`       | `http://localhost:8081` | Local mock OAuth2 server           |
| `local-https` | `http://localhost:8081` | UI repo's HTTPS mock OAuth2 server |
| `uat`         | Deployed UAT API        | Entra ID dev tenant                |
| `staging`     | Deployed staging API    | Entra ID NLE tenant                |
| `local-entra` | `http://localhost:8081` | Entra ID dev tenant                |

The `applications` folder provides shared authentication for its requests. Start the API before using
`local` or `local-entra` (see [Docker setup](../README.md#run-application-via-docker)).

For `uat`, `staging`, and `local-entra`, copy [`.env.example`](../bruno-collection/.env.example) to
`bruno-collection/.env`. Set the `UAT_ENTRA_*` values for `uat` and `local-entra`, and the
`STAGING_ENTRA_*` values for `staging`. Use the corresponding RCW UI app registration in the Entra ID NLE
tenant. Bruno loads these values automatically. Do not commit the `.env` file. It is separate from the
repository-root `.env` used for Docker configuration.

### Selecting an application

Set `applicationId` to a UI-created application's UUID, or use
[Create applications](../bruno-collection/Record%20Controlled%20Work%20API/applications/Create%20applications.yml).
Its response captures the new ID for subsequent requests. An explicit environment value takes precedence;
clear it to use the captured ID. The list filter `officeId` is an office UUID, unlike the creation field
`providerOfficeCode`.

### Synthetic drafts

The standard
[Create applications](../bruno-collection/Record%20Controlled%20Work%20API/applications/Create%20applications.yml)
request creates a persistent editable draft with no API delete endpoint. Use synthetic client details
and an authorized non-production office, never live office codes or real client details.

### Completed application example

This example reuses the core requests rather than duplicating them. Create or select a synthetic
application, then send [Update means](../bruno-collection/Record%20Controlled%20Work%20API/applications/Update%20means.yml)
and [Update evidence](../bruno-collection/Record%20Controlled%20Work%20API/applications/Update%20evidence.yml).
Then send [Update declaration](../bruno-collection/Record%20Controlled%20Work%20API/applications/Update%20declaration.yml)
and [Complete application](../bruno-collection/Record%20Controlled%20Work%20API/applications/Complete%20application.yml)
in that order. All requests use the selected `applicationId`.

After completing it, retrieve it with [Get application](../bruno-collection/Record%20Controlled%20Work%20API/applications/Get%20application.yml)
to confirm `COMPLETED`. You can then try [Update application details](../bruno-collection/Record%20Controlled%20Work%20API/applications/Update%20application%20details.yml)
to see that completed applications reject details updates. Send each request individually in Bruno
after signing in through the `applications` folder.

The status request includes the required numeric `eTag` field. The current status controller does not
use this field as the caller-supplied concurrency precondition for the details endpoint. Details updates
use the strong `If-Match` header captured by the retrieval request. The details update should return
`409` with reason `APPLICATION_COMPLETED`.

`COMPLETED` is an application state, not an eligibility result. The collection does not calculate or
verify eligibility. The core means request stores input data with an empty result.

The current Docker `local` profile propagates application exceptions and returned a `500` for this
completed-details request. The documented API contract is `409`; treat that local response as an
environment discrepancy.

### Guarded staging application lifecycle

The staging-only [Examples](../bruno-collection/Record%20Controlled%20Work%20API/applications/Examples/folder.yml)
workflow creates a controlled three-application dataset and proves shared-office access.

Select `staging`, keep its `baseUrl` unchanged, then sign in through
**applications > Auth > Get Access Token**. Before switching identity, sign out or choose
**Use another account**. Confirm the account in Bruno's interactive sign-in flow. API
authorization uses the `LAA_ACCOUNTS` office claim. The guards require these exact identity
sets: A has `0R125Q`, B has `0R128U` and `0R695K`, and C has only `0R695K`. Retrieved
applications confirm the expected firm and office. These are tester safeguards, not
token-signature verification or live entitlement checks. Tokens and claims are never printed.

Run the folders in this order:

1. As A, run steps **01–07** in order. This explicitly creates A's application, populates
   synthetic means, evidence and declaration data, completes it, then verifies it.
2. Switch to B and run steps **01–08**. Steps 01 and 04 explicitly create the private-office
   and shared-office drafts. Verify each returned firm before populating its means. Steps 07
   and 08 confirm the means, DRAFT state and separate strong ETags.
3. Switch to C and run steps **09–10**. C retrieves B's `0R695K` draft, then updates its
   synthetic client marker using the captured application-associated `If-Match` value.
4. Switch back to B and run steps **11–12** to verify both drafts. Confirm the private-office
   draft remains unchanged and the shared-office draft shows C's update and remains DRAFT.
   Both responses record the state B observes and refresh ETags for later examples.

Each successful setup run creates exactly three records: A's completed application and B's
two drafts. Run markers and separate IDs survive account switching. Reads and access checks
never create records. All records persist, including partial setup failures, and there is no
public delete operation. Do not retry an uncertain create. Inspect staging first. To recover
from a confirmed partial failure, deliberately start a new dataset with A's create request.
That creates new persistent records and clears old fixture references; it does not clean up
earlier records.

Means values are synthetic identifiers, not a CCQ calculation or eligibility determination.
COMPLETED is an application state, not an eligibility result. The status body's numeric
`eTag` is not the details route's caller-controlled `If-Match` guarantee.

Run `node bruno-collection/scripts/staging-examples.test.js` and
`node bruno-collection/scripts/application-details.test.js` for offline checks. They do not
contact Entra or staging. Real account switching and staging results remain unverified and
require separate authorization.

### Editing draft details

Retrieve the selected draft with [Get application](../bruno-collection/Record%20Controlled%20Work%20API/applications/Get%20application.yml)
before updating. A successful response captures its strong quoted integer `ETag`; the normal
[Update application details](../bruno-collection/Record%20Controlled%20Work%20API/applications/Update%20application%20details.yml)
request sends it as `If-Match`. Retrieval clears the previous ETag before sending, and the request refuses
to send if the ETag belongs to another selected application. After any update response, retrieve again
before the next normal update. This endpoint replaces the full editable details snapshot; other mutations
do not use this workflow's `If-Match` safeguard.

The shared [application-details.js](../bruno-collection/scripts/application-details.js) generator provides
defaults for creation, details updates, and valid variations. Variations pass only schema-specific
overrides. Bruno Developer Mode is required to import the script; use `--sandbox developer` with Bruno CLI.
Run `node bruno-collection/scripts/application-details.test.js` to check it
([self-check](../bruno-collection/scripts/application-details.test.js)).

- [Invalid input](../bruno-collection/Record%20Controlled%20Work%20API/applications/Variations/Update%20application%20details/Invalid%20input.yml):
  empty body; expect `400` (`INVALID_APPLICATION_DETAILS`).
- [Missing ETag](../bruno-collection/Record%20Controlled%20Work%20API/applications/Variations/Update%20application%20details/Missing%20ETag.yml):
  valid body without `If-Match`; expect `428` (`IF_MATCH_REQUIRED`).
- [Stale ETag](../bruno-collection/Record%20Controlled%20Work%20API/applications/Variations/Update%20application%20details/Stale%20ETag.yml):
  `If-Match: "0"`; first make a successful update so the draft version is newer; expect `412`
  (`APPLICATION_VERSION_CONFLICT`).
- [Get missing application](../bruno-collection/Record%20Controlled%20Work%20API/applications/Get%20missing%20application.yml):
  retrieves a nonexistent UUID without changing the selected ID. After a successful retrieval, use it to
  confirm a failed lookup clears the captured ETag.

## Authentication

The `applications` folder uses OAuth2 **Authorization Code + PKCE**. Its requests inherit the folder
configuration and share an interactive token, which Bruno attaches and refreshes automatically.

### Sign in

Click the **applications** folder, open **Auth**, then click **Get Access Token**. Sign in through Bruno's
embedded browser, or enable **Preferences > General > Use System Browser for OAuth2**. The access token is
shared by requests in the folder.

## Inspect the cached token

Send **Inspect cached access token** after signing in. It decodes the token locally; view
`decodedAccessTokenHeader` and `decodedAccessTokenClaims` in Runtime Variables or Bruno's console.
