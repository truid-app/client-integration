# Integrate Client App with Backend

<!--
TODO: Build script that generates .png from .puml files
TODO: Add very simple example backend and app, that demonstrates each of the steps below
TODO: Add build script that generates HTML from the markup
TODO: Include generated REST API documentation when building
TODO: add publish script that publishes to truid.app or to github.com pages
-->

```mermaid
sequenceDiagram

  participant APP as App
  participant TID as TruID App
  participant BE as Backend
  participant API as TruID REST API

  APP ->> BE: C-1: https://example.com/confirm-signup

  BE -->> APP: C-2: 200 OK
  note over APP,BE: Location: https://api.truid.app/oauth2/v1/authorize/confirm-signup

  APP ->> TID: O-1: https://api.truid.app/oauth2/v1/authorize/confirm-signup
  note over APP,TID: response_type=code<br/>client_id=123<br/>scope=veritru.me/claim/email/v1<br/>redirect_uri=https://example.com/continue-signup<br/>state=ABC<br/>nonce=DEF

  TID ->> TID: Secure Identity

  TID ->> APP: O-2: https://example.com/continue-signup
  note over TID,APP: code=XYZ<br/>state=ABC<br/>nonce=DEF

  APP ->> BE: C-3: https://example.com/continue-signup
  note over APP,BE: code=XYZ<br/>state=SABC<br/>nonce=NABC

  BE ->> API: O-3: https://api.truid.app/oauth2/v1/token
  note over BE,API: grant_type=authorization_code<br/>code=XYZ<br/>redirect_uri=https://example.com/continue-signup<br/>client_id=123<br/>client_secret=ABC

  API -->> BE: O-4: 200 OK
  note over API,BE: access_token=ACCESS-ABCDEF<br/>token_type=bearer<br/>expires_in=3600<br/>refresh_token=REFRESH-ABCDEF<br/>scope=veritru.me/claim/email/v1

  BE -->> APP: C:4: 200 OK

  BE ->> API: O-5: https://api.truid.app/oidc/v1/user-info3
  note over BE,API: Bearer: ACCESS-ABCDEF34
  API -->> BE: O-6: 200 OK 44
```

TBD: Overview, description of usecase and reference to oauth2 code flow

![app-with-backend-flow](images/app-with-backend-flow.svg)

TBD: include image app-with-backend.puml

TBD: description of each of the steps in the diagram. - Link to RFC/oauth.net

TBD: Short overview of steps and links to each step. Sort of a table of contents. Only needed if the descriptions become long.

1. Create endpoint for authorization URL
- Link to RFC/oauth.net
- Link to TruID API documentation
- Link to example code in this repo

2. Configure Service in TruID

3. Fetch authorization URL and rediect to TruID

4. Add Deep Linking for redirect URI

5. Add endpoint for completing authorization

6. Access the TruID User Info endpoint
