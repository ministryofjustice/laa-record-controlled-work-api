# Bruno Collection

[Bruno](https://www.usebruno.com/) is an offline-first API client. Open the repository's
`bruno-collection` folder in the Bruno desktop app.

## Environments

Select an environment from Bruno's dropdown before sending requests:

| Environment | API | Authentication |
|---|---|---|
| `local` | `http://localhost:8081` | Local mock OAuth2 server |
| `local-https` | `http://localhost:8081` | UI repo's HTTPS mock OAuth2 server |
| `uat` | Deployed UAT API | Entra ID dev tenant |
| `local-entra` | `http://localhost:8081` | Entra ID dev tenant |

The `applications` folder provides shared authentication for its requests. Start the API before using
`local` or `local-entra` (see [Docker setup](../README.md#run-application-via-docker)).

For `uat` and `local-entra`, copy
[`.env.example`](../bruno-collection/.env.example) to `bruno-collection/.env` and fill in
`ENTRA_TENANT_ID`, `ENTRA_CLIENT_ID`, and `ENTRA_CLIENT_SECRET` for the UI app registration. Bruno loads
these values automatically. Do not commit the `.env` file. It is separate from the repository-root `.env`
used for Docker configuration.

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
