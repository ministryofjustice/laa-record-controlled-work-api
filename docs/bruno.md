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

Both environments use the same folder-level OAuth2 config (see below) - only the URLs/credentials the
requests are interpolated from differ, so switching environments does not require any changes to auth setup.

## How authentication works

The `applications` folder (not the individual requests inside it) owns an OAuth2 **Authorization Code + PKCE**
configuration, which every request under it uses via `auth: inherit`. Configuring auth at the folder level
means:

- All requests in the folder share one sign-in and one cached token.
- The token is fetched interactively (a browser/login window opens), which is required here because the
  claims we care about (e.g. roles) are enriched by the identity provider (IdP) during an interactive
  sign-in - a `client_credentials` (service-to-service, no-browser) grant would not pick these up.

### Getting a token

1. Select the folder's own settings - click on the **applications** folder in the sidebar (not a request
   inside it), then open its **Auth** tab. Do not open a request's Auth tab; it just says "inherited" and has
   no button.
2. Click **Get Access Token**. This button is at the very bottom of the Auth panel, so scroll down if you
   don't see it.
3. A login window opens against the environment's IdP:
   - `local`: the mock OAuth2 server's debugger login page. You can type any username and set arbitrary
     claims (e.g. `roles`) directly on that page to simulate claim enrichment.
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

This URL must be registered once on the **UI app registration** used for `uat` in the dev Entra tenant:

1. Entra ID > App registrations > (the UI app registration) > Authentication.
2. Under **Platform configurations**, add a **Web** platform (if not already present).
3. Add `https://oauth.usebruno.com/callback` as a redirect URI and save.

Without this, sign-in fails after you authenticate, because Entra refuses to redirect back to an
unregistered URI.

### Local (mock OAuth2 server) flow

The `local` environment doesn't need any Entra/redirect URI setup - the mock OAuth2 server accepts any
callback URL. Prerequisites:

1. Add `host.docker.internal` to `/etc/hosts` (see [README.md](../README.md) Prerequisites) - Bruno runs on
   the host, so it needs to resolve this hostname the same way the container does.
2. Start the stack with `make docker-up` so `mock-oauth2-server` is running on `localhost:9090`.

With the `local` environment selected, follow [Getting a token](#getting-a-token) as normal. The login
window that opens is the mock server's debugger page: enter any username/subject and, optionally, add a
`roles` claim (or any other claim) to simulate whatever enrichment you want to test - the mock server also
automatically adds `aud: default` and `roles: ["Applications.Read", "Applications.Write"]` to every
authorization_code token regardless of what you enter (see `mock-oauth2-config.json`), so a plain sign-in
already satisfies the API's authorization checks.

### Scopes

The API exposes both `Applications.Read` and `Applications.Write`. The folder's `oauthScope` variable
requests both by default, so the one cached token works for read and write requests alike:

- `local`: the mock server ignores the requested scope string and always issues both roles (see above), so
  no changes are needed here.
- `uat`: `oauthScope` in `uat.yml` already lists both `.../Applications.Read` and `.../Applications.Write`.

If you need to test authorization boundaries with a token that's missing one of the scopes (e.g. to confirm
a write endpoint rejects a read-only token), temporarily edit `oauthScope` down to the single scope you want
in the environment, then click **Get Access Token** again to refresh the cached token - remember to restore
it afterwards since it's shared by every request in the folder.

### UAT secrets

The `uat` environment needs tenant/client credentials that must never be committed. These are loaded from
a `.env` file at the root of the collection (`bruno-collection/.env`, git-ignored) via Bruno's built-in
DotEnv support:

1. Copy `bruno-collection/.env.sample` to `bruno-collection/.env`.
2. Fill in `ENTRA_TENANT_ID`, `ENTRA_CLIENT_ID` and `ENTRA_CLIENT_SECRET` from the UI app registration in
   the dev Entra tenant.

Bruno loads this file automatically and the `uat` environment's secret variables reference it via
`{{process.env.<NAME>}}`.

## Inspecting the cached access token

The **Inspect cached access token** request (in the `applications` folder) decodes the currently cached
OAuth2 access token and prints its header and claims, without sending the token anywhere:

1. Make sure a token has already been fetched (see [Getting a token](#getting-a-token) above).
2. Send **Inspect cached access token**.
3. View the decoded output either:
   - In Bruno's console (View > Show DevTools, or the console icon at the bottom of the response pane), or
   - In the **Runtime Variables** panel, under `decodedAccessTokenHeader` / `decodedAccessTokenClaims`.

This is useful for confirming that claim enrichment (e.g. `roles`) is present on the token before debugging
further down the stack.

> **Note:** `local` and `uat` tokens are cached separately (they're fetched from different token URLs), but
> both share the same token ID (`applications-token` in `folder.yml`). If you've fetched a token in both
> environments, this request always decodes whichever one was fetched *most recently*, not necessarily the
> one matching the currently selected environment - re-run **Get Access Token** after switching environments
> if you want to inspect that environment's token.
