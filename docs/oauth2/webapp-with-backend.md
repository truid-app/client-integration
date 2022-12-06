# Client webapp with Backend

## Overview

The goal of this integration flow is to give a service backend access to user data from Truid. The user is interacting with the service using a webapp.

This flow uses the OAuth2 Authorization Code Grant with the PKCE extension to allow the user to authorize the backend to access the Truid API to fetch data about the user.

Documentation about different Truid flows:
- TBD: Link to documentation about confirm-signup, single transaction, login, ...

Standards:
- [RFC 6749 - Authorization Code Grant](https://www.rfc-editor.org/rfc/rfc6749#section-4.1)
- [RFC 7636 - Proof Key for Code Exchange by OAuth Public Clients (PKCE)](https://www.rfc-editor.org/rfc/rfc7636)
- [OAuth2 on oauth.net](https://oauth.net/2/)
- [PKCE extension on oauth.net](https://oauth.net/2/pkce/)
- [Authorization Code Grant on oauth.net](https://oauth.net/2/grant-types/authorization-code/)

## Flow

This sequence diagram gives an overview of the flow.

```mermaid
sequenceDiagram

  participant WEB as Browser
  participant TID as Truid App
  participant BE as Backend
  participant API as Truid REST API

  WEB ->> BE: C-1: https://example.com/confirm-signup

  BE -->> WEB: C-2: 302 Found
  note over WEB,BE: Location: https://api.truid.app/oauth2/v1/authorize/confirm-signup

  WEB ->> API: O-3: https://api.truid.app/oauth2/v1/authorize/confirm-signup
  note over WEB,API: response_type=code<br/>client_id=123<br/>scope=veritru.me/claim/email/v1<br/>redirect_uri=https://example.com/complete-signup<br/>state=ABC<br/>code_challenge=CH1234<br/>code_challenge_method=S256

  API -->> WEB: T-4: 200 OK (QR code page)

  WEB ->> TID: Scan QR code

  TID ->> TID: Secure Identity

  WEB ->> BE: O-5: https://example.com/complete-signup
  note over WEB,BE: code=XYZ<br/>state=ABC

  BE ->> API: O-6: https://api.truid.app/oauth2/v1/token
  note over BE,API: grant_type=authorization_code<br/>code=XYZ<br/>redirect_uri=https://example.com/complete-signup<br/>client_id=123<br/>client_secret=ABC<br/>code_verifier=VR1234

  API -->> BE: O-7: 200 OK
  note over API,BE: access_token=ACCESS-ABCDEF<br/>token_type=bearer<br/>expires_in=3600<br/>refresh_token=REFRESH-ABCDEF<br/>scope=veritru.me/claim/email/v1

  BE -->> WEB: C-8: 200 OK

  BE ->> API: O-9: https://api.truid.app/oidc/v1/user-info
  note over BE,API: Bearer: ACCESS-ABCDEF
  API -->> BE: O-10: 200 OK
```

#### Legend

&nbsp; &nbsp; *C-N* denotes service specific messages

&nbsp; &nbsp; *O-N* denotes OAuth2 standard messages

&nbsp; &nbsp; *T-N* denotes Truid specific messages

#### Steps

&nbsp; &nbsp; *C-1:* The webapp is initiating the flow by asking the backend for an URL to use for starting the flow towards Truid

&nbsp; &nbsp; *C-2:* Return authorization URL

&nbsp; &nbsp; *O-3:* The browser is redirected to a Truid authorization page

&nbsp; &nbsp; *T-4:* The Truid API returns a page containing a QR code for authentication

&nbsp; &nbsp; *O-5:* After completing authorization in the Truid App, the browser is redirected back to the service

&nbsp; &nbsp; *O-6:* The backend is using the code to get an access token from Truid

&nbsp; &nbsp; *O-7:* The Truid API is returning an access token

&nbsp; &nbsp; *C-8:* The backend is returning a response

&nbsp; &nbsp; *O-9:* The backend uses the access token to fetch data from the Truid API

&nbsp; &nbsp; *O-10:* The Truid API returns data about the user

## Integrarion

This section describes the steps needed to integrate a service with Truid.

### 1. Configure Service in Truid

Currently the process of configuring a service in Truid requires contacting the Truid support and ask for a service to be registered. Eventually this process will be replaced with a self service UI.

The information needed to configure the service is:
- Service Name
- Service Icon, in png or jpeg format
- Redirect URI, to redirect back to the app
- A set of claims that the service should be allowed to request

When the service is configured, a `client_id` and a `client_secret` is created. The secret must be kept secure, if it is leaked an attacker will be able to use it to access protected resources.

_Links:_

- TBD: Link to claim definitions

### 2. Create endpoint for authorization URL

Add an endpoint in the backend which has the purpose of creating an Authorization Request URL, and redirect the app to this URL. This corresponds to the steps _C-1_ and _C-2_ in the flow above. The returned Authoization Request URL will then be used in step _O-3_ in the flow above.

The Authorization Request URL follows the OAuth2 standard for an Authorization Request. The URL will point to different endpoints in the Truid API depending on which Truid Authorization Flow that should be started.

To start a Truid Confirm Signup flow, the Authorization Request URL should point to `https://api.truid.app/oauth2/v1/authorize/confirm-signup`.

TBD: Link to Truid API specification for authorization URL

TBD: Link to documentation about different Truid flows

_Notes:_

The `redirect_uri` parameter is where the client will be redirected with an authorization code. This URL must match exactly what is configured in Truid.

The `scope` parameter must be a subset of the claims that were given when configuring the service in Truid.

The `state` parameter must be used to prevent cross-site request forgery, see [RFC-6749 Section 10.12](https://www.rfc-editor.org/rfc/rfc6749#section-10.12).

The PKCE extension must be used, see [RFC 7636 - Proof Key for Code Exchange by OAuth Public Clients (PKCE)](https://www.rfc-editor.org/rfc/rfc7636). The only supported code challenge method is `S256`.

Some clients, such as `react-native` based apps, have problems with manual handling of redirection responses, and in those cases it might make sense to return the authorization URL in a 2xx response. See TBD: link to example code

_Example:_

`https://api.truid.app/oauth2/v1/authorization/confirm-signup?response_type=code&client_id=abcdef&scope=veritru.me%2Fclaim%2Femail%2Fv1&redirect_uri=https%3A%2F%2Fexample.com%2Fcomplete-signup&state=123456&code_challenge=CH12345&code_challenge_method=S256`

_Links:_

- [RFC-6749 - Authorization Request](https://www.rfc-editor.org/rfc/rfc6749#section-4.1.1)
- [RFC-6749 - Cross-Site Request Forgery](https://www.rfc-editor.org/rfc/rfc6749#section-10.12)
- [RFC 7636 - Proof Key for Code Exchange by OAuth Public Clients (PKCE)](https://www.rfc-editor.org/rfc/rfc7636)
- TBD: Link to Truid API specification
- [Code Example](https://github.com/truid-app/client-integration/blob/main/example-backend/src/main/kotlin/app/truid/example/examplebackend/TruidSignupFlow.kt#L67-L67)

### 3. Open authorization URL

Start the Authorization Flow from the webapp, by directing the browser to the endpoint created in step 2 and then to be redirected to the Authorization Request URL. This corresponds to steps _C-1_, _C-2_, and _O-3_ in the flow above.

_Notes:_

The authorization URL can be opened in a new window to keep the webapp while doing the authorization.

_Links:_

- [Code Example](https://github.com/truid-app/client-integration/blob/main/example-web/src/html/index.html#L7-L7)

### 4. Add endpoint for completing authorization

Add an endpoint for completing the authorization. This corresponds to step _O-5_ in the flow above, and is the redirect URI that the browser will be directed to at the end of the authorization flow.

The service should invoke the Truid API token endpoint to complete the authorization flow, and to exchange the code into an access token and a refresh token. This corresponds to step _O-6_ and _O-7_ in the flow above.

The returned access token can be used to request user data from the Truid API. The access token represents users consent to share data with the service. The expiry time of the access token will be quite short, and the service should not store the access token after it has completed processing of the request.

The returned refresh token should be persisted and associated with the logged in user. The refresh token can be used to request a new access token. The refresh token must be kept secure, if it is leaked an attacker will be able to use it to access user data.

_Notes:_

The backend must authenticate the request to identify the logged in user that sent the request. The `state` parameter can not be used to identify the user, since it could be faked. How the service choses to authenticate users is up to the service to decide.

The service must verify that the `state` parameter is correct and matches the logged in user, to prevent cross-site request forgery, see [RFC-6749 Section 10.12](https://www.rfc-editor.org/rfc/rfc6749#section-10.12).

If the user cancels the authorization request, or if there is any other error during the authorization flow, the redirect will contain an `error` parameter that identifies the error. The service should return a 403 response from this endpoint in that case.

The backend must include the PKCE code verifier in the request to get an access token.

The `redirect_uri` paramteter in the token request must match exactly what is configured in Truid.

_Examples:_

Example authorization response:

```
GET https://example.com/complete-signup?code=XYZ&state=123
```

Example token request:

```
POST https://api.truid.app/oauth2/v1/token

grant_type=authorization_code
code=XYZ
redirect_uri=https://example.com/complete-signup
client_id=123
client_secret=ABC
code_verifier=VR1234
```

_Links:_

- [RFC-6749 - Authorization Response](https://www.rfc-editor.org/rfc/rfc6749#section-4.1.2)
- [RFC-6749 - Access Token Request](https://www.rfc-editor.org/rfc/rfc6749#section-4.1.3)
- [Authorization Code Request on oauth.net](https://www.oauth.com/oauth2-servers/access-tokens/authorization-code-request/)
- [RFC-6749 - Cross-Site Request Forgery](https://www.rfc-editor.org/rfc/rfc6749#section-10.12)
- TBD: link to Truid API documentation for token endpoint
- [Code Example](https://github.com/truid-app/client-integration/blob/main/example-backend/src/main/kotlin/app/truid/example/examplebackend/TruidSignupFlow.kt#L92-L92)

### 5. Access the Truid User Info endpoint

When the service want to use user data from Truid it should use the stored refresh token to get a new access token, and then use the access token to fetch the user data from the Truid API. This corresponds to the steps _O-9_ and _O-10_ in the flow above.

The Truid API contains several endpoints that supports multiple formats to fetch user data. There is an endpoint that returns user data in a proprietary format which contains complete information about each data point. There are also other endpoints that produces data in standard formats such as for example the OIDC user-info format.

_Notes:_

Data can be fetched multiple times and at any time, not necessarily at the same time that the authorization flow is completed and the access token is first fetched.

The service is not required to persist any user data that is retreived from the Truid API except the refresh token. The data will be available to fetch from the Truid API for as long as the user has a connection to the service.

_Links:_

- [RFC-6749 - Refreshing an Access Token](https://www.rfc-editor.org/rfc/rfc6749#section-6)
- [RFC-6749 - Accessing Protected Resources](https://www.rfc-editor.org/rfc/rfc6749#section-7)
- [Refresh Token on oauth.net](https://oauth.net/2/grant-types/refresh-token/)
- [Refreshing Access Tokens on oauth.net](https://www.oauth.com/oauth2-servers/access-tokens/refreshing-access-tokens/)
- TBD: link to RFC for userinfo endpoint
- TBD: link to Truid API documentation for refresh
- TBD: link to Truid API documentation for user-info
- TBD: link to code example