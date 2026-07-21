# `ms-counter` — the Counter CLI

A small Kotlin/JVM command-line client for the Counter service. It signs you in
with your `medusa.software` Google account and drives the counter from a
terminal.

```
ms-counter login        # sign in (opens a browser) and cache the session
ms-counter increment    # +1, prints the new count
ms-counter decrement    # -1, prints the new count
ms-counter get          # print the current count
ms-counter logout       # forget the cached session on this machine
```

## Install (Homebrew)

```
brew install medusa-software-hq/tap/counter
```

It depends on `openjdk@21` (installed automatically) and runs as `ms-counter`.

## How auth works

`login` runs the standard **installed-app OAuth flow**: it opens your browser to
Google, catches the redirect on an ephemeral `127.0.0.1:<port>` loopback, and
exchanges the code (PKCE) for tokens. It caches a refresh token under
`~/.config/ms-counter/credentials.json` (dir `0700`, file `0600`) and silently
refreshes the short-lived ID token as needed — you only re-`login` if the
refresh token is revoked.

Each request sends your Google **ID token** as `Authorization: Bearer …`. The API
accepts it because its audience is the counter CLI's Desktop OAuth client (one of
the API's allowed audiences) and it carries the `medusa.software` hosted-domain
claim.

## Local development

The CLI talks to the prod API by default. Point it elsewhere with:

```
COUNTER_API_URL=http://localhost:8081 ms-counter get
```

The local backend uses no-op auth, so `get`/`increment`/`decrement` work against
it without `login`. A local build has no OAuth client secret baked in; set
`COUNTER_CLI_OAUTH_CLIENT_SECRET` (the Desktop client's secret) if you need
`login` to run against a real environment from a dev build.

Common tasks (via [Task](https://taskfile.dev)):

```
task cli:compile      # compile
task cli:test         # unit tests
task cli:lint         # detekt
task cli:run -- get   # build + run locally
```
