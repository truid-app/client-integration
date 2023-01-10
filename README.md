# Truid Client Integration

This repository contains example code showing how to integrate the Truid products.

See full documentation at https://developer.truid.app

## Packages

### example-backend

This is an example project showing how to access the Truid REST API from a backend service to read data. This example is written using Kotlin and `spring-boot`, but should be easy enough to understand to know how to access the Truid REST API using any lanuguage and technology.

Build:

```bash
$ docker build . -t example-backend
```

### example-app

This is an example project that shows how to start a Truid flow from an app, running on the same device as the Truid App. The app is written in `react-native` for iOS and Android, but should be easy enough to understand to know how to start a flow using any language and technology.

### example-web

This is an example project that shows how to start a Truid flow from a webapp, running in a browser either on the same device as the Truid App, or on a separate device.

Build:

```bash
$ docker build . -t example-web
```

## Try it

There are two main flows with slightly different setup
1. Web Client Flow: The flow starts in the browser, either on the same device where Truid is installed or a browser on a second device
2. App to App Flow: The flow starts in an app that uses Truid for login

### Try the Web Client Flow

#### Register a Service

Register a Service and a Consent Template in Truid to obtain OAuth2 client credentials. The service need the following property:
- Test Service: `true`

For web client the Consent Template need the following property:
- `redirect_uris`: `http://localhost:8080/truid/v1/complete-signup`

_Note:_

One of the redirect uris runs over plain `http` and on `localhost`. This is not secure, and only works on a test client.

Currently, the process of configuring a service in Truid requires contacting the Truid support and ask for a service to be registered.

#### Start the backend

Prerequisites:
- Client ID and Secret
- Docker

Start the backend:

```bash
$ export TRUID_CLIENT_ID=...
$ export TRUID_CLIENT_SECRET=...
$ docker-compose up
```

#### Try the web client

Point your browser to `http://localhost:8080/index.html`.

_Note:_

The QR flow for remote login is not yet fully implemented, and will return access denied.

#### Try the web client on an Android phone

Connect the Android phone using USB, developer mode need to be activated on the phone

Forward the 8080 port to localhost:

```bash
$ adb reverse tcp:8080 tcp:8080
```

On the phone, point the browser to `http://localhost:8080/index.html`

#### Try the web client on an iPhone

Connect the iPhone using USB

On the phone, point the browser to `http://localhost:8080/index.html`

### Try the App to App Flow

#### Register a Service

Register a Service and a Consent Template in Truid to obtain OAuth2 client credentials. The service need the following property:
- Test Service: `true`

The Consent Template need the following property:
- `redirect_uris`: `truidtest://complete-signup`

_Note:_

The redirect `truidtest://complete-signup` is used for app to app flow. It uses a custom scheme `truidtest` that only works on a test client.
Only `https` scheme is allowed for app to app integrations outside test.

Currently, the process of configuring a service in Truid requires contacting the Truid support and ask for a service to be registered.

#### Start the backend

Prerequisites:
- Client ID and Secret
- Docker

Start the backend:

```bash
$ export TRUID_REDIRECT_URI=truidtest://complete-signup
$ export TRUID_CLIENT_ID=...
$ export TRUID_CLIENT_SECRET=...
$ docker-compose up
```

### Android

Prerequisites:
Android Studio with emulator supporting Android API v28 or later and including Google Play Store

Install Yarn  
```bash
$ npm install -g yarn
```

Setup:

```bash
$ cd example-app
$ yarn install
```

Change the redirect uri to the one used by the example app
```bash
$ export TRUID_REDIRECT_URI=truidtest://complete-signup
```

Start the emulator in Android Studio - Device manager

Give emulator access to host localhost:8080 where example-backend is running

```bash
$ adb reverse tcp:8080 tcp:8080
```

Start the example app

```bash
$ yarn run android
```

### Ios
TBD
