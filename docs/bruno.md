# Bruno Collection

[Bruno](https://www.usebruno.com/) is a Git-friendly, offline-first API client. A collection is checked into
this repo at `bruno-collection` so requests (and their auth setup) are version-controlled alongside the API.

Open it in the Bruno desktop app via **Open Collection** and select the `bruno-collection` folder.

## Environments

Bruno environments live under `bruno-collection/environments`. Select one from the environment dropdown
in the top-right of the Bruno window before sending any request:

- `local` - targets the app running locally (`http://localhost:8081`) and signs in against the
  [mock-oauth2-server](https://github.com/navikt/mock-oauth2-server) started by `make docker-up`.
- `uat` - targets the deployed UAT environment and signs in against the real Entra ID (Azure AD) dev tenant.
- `local-entra` - targets the app running locally (`http://localhost:8081`), same as `local`, but signs in
  against the real Entra dev tenant, same as `uat`. Use this to test the local Docker stack (including the
  real datastore OBO exchange) with real Entra-issued tokens instead of the mock OAuth2 server. Requires
  `docker-compose.yml`'s Entra vars to be set (see [.env.example](../.env.example)) before running
  `make docker-up` - see [README.md](../README.md#run-application-via-docker).

All three environments use the same folder-level OAuth2 config (see below) - only the URLs/credentials the
requests are interpolated from differ, so switching environments does not require any changes to auth setup.

## How authentication works

The `applications` folder (not the individual requests inside it) owns an OAuth2 **Authorization Code + PKCE**
configuration, which every request under it uses via `auth: inherit`. Configuring auth at the folder level
means:

- All requests in the folder share one sign-in and one cached token.
- The token is fetched interactively (a browser/login window opens), which is required here because the
  claims we care about (e.g. `scp` scopes) are enriched by the identity provider (IdP) during an interactive
  sign-in - a `client_credentials` (service-to-service, no-browser) grant would not pick these up.

### Getting a token

1. Select the folder's own settings - click on the **applications** folder in the sidebar (not a request
   inside it), then open its **Auth** tab. Do not open a request's Auth tab; it just says "inherited" and has
   no button.
2. Click **Get Access Token**. This button is at the very bottom of the Auth panel, so scroll down if you
   don't see it.

By default this opens the sign-in page in Bruno's embedded browser window. If you'd rather sign in via your
system's default browser, enable it once in **Preferences** (bottom of Bruno's left sidebar) > **General** >
**Use System Browser for OAuth2**. This is a global Bruno app preference (not stored in the collection), and
works with our existing `callbackUrl` without any other changes.
3. A login window opens against the environment's IdP:
   - `local`: the mock OAuth2 server's debugger login page. You can type any username and set arbitrary
     claims (e.g. `scp`) directly on that page to simulate claim enrichment.
   - `uat`: the real Entra sign-in page for the dev tenant (`devlexternal.onmicrosoft.com`). Sign in with an
     account that has the required role assignment.
4. Once signed in, Bruno stores the token against the folder and automatically attaches it (as an
   `Authorization: Bearer ...` header) to every request in the folder. `autoRefreshToken` is enabled, so
   expired tokens are refreshed silently using the refresh token where possible.

Bruno's own Auth tab shows a preview of the fetched token, but its JWT payload decoder has a known bug: it
calls `atob()` directly instead of handling base64url (`-`/`_`) encoding, so it sometimes renders garbled
text (e.g. `SyntaxError: Unexpected token ... is not valid JSON`). This does **not** mean the token fetch
failed - it's purely a display bug in Bruno. Use the **Inspect cached access token** request described below
to reliably view the decoded claims instead.

### Redirect URI requirement (UAT / Entra only)

Entra rejects the sign-in unless the callback URL is pre-registered on the app registration. Our folder
config sets an explicit `callbackUrl` of `https://oauth.usebruno.com/callback` (Bruno's own hosted callback
page, which simply relays the authorization code back to the desktop app - no data is sent to a third
party beyond the code itself).

This URL must be registered once on the **UI app registration** used for `uat` and `local-entra` in the dev
Entra tenant (both sign in against the same app registration - only `baseUrl` differs):

1. Entra ID > App registrations > (the UI app registration) > Authentication.
2. Under **Platform configurations**, add a **Web** platform (if not already present).
3. Add `https://oauth.usebruno.com/callback` as a redirect URI and save.

Without this, sign-in fails after you authenticate, because Entra refuses to redirect back to an
unregistered URI.

### OBO admin consent requirement (`local-entra` only)

`local-entra` exercises the real datastore OBO (on-behalf-of) exchange (see
`DatastoreClientConfiguration`), which fails with `AADSTS65001` ("has not consented to use the
application") until an Entra admin grants **admin consent** for the `Record Controlled Work API` app
registration's `DataStore.Access` delegated permission on the Datastore API:

1. Entra ID > App registrations > `Record Controlled Work API` > API permissions.
2. Confirm the Datastore API's `DataStore.Access` delegated permission is listed (add it via **Add a
   permission** > APIs my organization uses if not).
3. Click **Grant admin consent for `<tenant>`** (requires an admin role such as Cloud Application
   Administrator, Application Administrator, or Global Administrator).

This is a one-time, tenant-wide grant - it covers every user (including ones created afterwards), so it
doesn't need repeating per user or per new sign-in. Per-user consent doesn't apply here even though the
permission allows it: the OBO call is a back-channel server-to-server request with no browser step, so
there's never an interactive prompt for an individual user to accept.

### Local (mock OAuth2 server) flow

The `local` environment doesn't need any Entra/redirect URI setup - the mock OAuth2 server accepts any
callback URL. Prerequisites:

1. Add `host.docker.internal` to `/etc/hosts` (see [README.md](../README.md) Prerequisites) - Bruno runs on
   the host, so it needs to resolve this hostname the same way the container does.
2. Start the stack with `make docker-up` so `mock-oauth2-server` is running on `localhost:9090`.

With the `local` environment selected, follow [Getting a token](#getting-a-token) as normal. The login
window that opens is the mock server's custom login page (`mock-oauth2-login.html`, wired in via
`loginPagePath` in `mock-oauth2-config.json`), pre-filled with a default username/subject (`test-user`) and
a default claims JSON (`aud`, `scp`, `FIRM_CODE`) so you can just click **Sign-in**. Unlike the `jwt-bearer`
grant (whose claims are fixed in `mock-oauth2-config.json` for the non-interactive RCW -> datastore OBO
exchange), the authorization_code grant has no config-level claims override - the claims box on the login
page is the sole source of truth for these tokens. Edit its JSON before
submitting to simulate different enrichment or to test authorization boundaries (e.g. remove
`Applications.Write` from `scp` to confirm a write endpoint rejects a read-only token).

### Scopes

The API exposes both `Applications.Read` and `Applications.Write`. The folder's `oauthScope` variable
requests both by default, so the one cached token works for read and write requests alike:

- `local`: the mock server doesn't read `oauthScope` for the authorization_code grant - the `scp` claim
  comes from the login page's claims box instead (see above), which defaults to both scopes.
- `uat` / `local-entra`: `oauthScope` already lists both `.../Applications.Read` and `.../Applications.Write`.

If you need to test authorization boundaries with a token that's missing one of the scopes (e.g. to confirm
a write endpoint rejects a read-only token):

- `local`: edit the `scp` value in the login page's claims box down to the single scope you want, then sign
  in again to refresh the cached token.
- `uat` / `local-entra`: temporarily edit `oauthScope` down to the single scope you want in the environment,
  then click **Get Access Token** again to refresh the cached token.

Remember to restore whichever default you changed afterwards, since the token is shared by every request in
the folder.

### UAT / local-entra secrets

The `uat` and `local-entra` environments need tenant/client credentials that must never be committed. These
are loaded from a `.env` file at the root of the collection (`bruno-collection/.env`, git-ignored) via
Bruno's built-in DotEnv support:

1. Copy `bruno-collection/.env.sample` to `bruno-collection/.env`.
2. Fill in `ENTRA_TENANT_ID`, `ENTRA_CLIENT_ID` and `ENTRA_CLIENT_SECRET` from the UI app registration in
   the dev Entra tenant.

Bruno loads this file automatically and both environments' secret variables reference it via
`{{process.env.<NAME>}}`. Note this is a separate `.env` from the repo root one used to switch
`docker-compose.yml` itself to Entra - see [README.md](../README.md#run-application-via-docker).

## Inspecting the cached access token

The **Inspect cached access token** request (in the `applications` folder) decodes the currently cached
OAuth2 access token and prints its header and claims, without sending the token anywhere:

1. Make sure a token has already been fetched (see [Getting a token](#getting-a-token) above).
2. Send **Inspect cached access token**.
3. View the decoded output either:
   - In Bruno's console (View > Show DevTools, or the console icon at the bottom of the response pane), or
   - In the **Runtime Variables** panel, under `decodedAccessTokenHeader` / `decodedAccessTokenClaims`.

This is useful for confirming that claim enrichment (e.g. `scp`) is present on the token before debugging
further down the stack.

> **Note:** `local` and `uat` tokens are cached separately (they're fetched from different token URLs), but
> both share the same token ID (`applications-token` in `folder.yml`). If you've fetched a token in both
> environments, this request always decodes whichever one was fetched *most recently*, not necessarily the
> one matching the currently selected environment - re-run **Get Access Token** after switching environments
> if you want to inspect that environment's token.
