# Truid Client Integration

This repository contains example code showing how to integrate the Truid products.

See full documentation at https://developer.truid.app

## Packages

### example-backend

This is an example project showing how to access the Truid REST API from a backend service to read data. This example is written using Kotlin and `spring-boot`, but should be easy enough to understand to know how to access the Truid REST API using any lanuguage and technology.

### example-app

This is an example project that shows how to start a Truid flow from an app, running on the same device as the Truid App. The app is written in `react-native` for iOS and Android, but should be easy enough to understand to know how to start a flow using any language and technology.

### example-web

This is an example project that shows how to start a Truid flow from a webapp, running in a browser either on the same device as the Truid App, or on a separate device.

## Try it

This example can be run locally to test the flows.

### Register a client

Register an OAuth2 client in Truid, having the following properties:
- Test Client: `true`
- `redirect_uri`: `http://localhost:8080/truid/v1/complete-signup`

_Note:_

This example runs over plain `http` and on `localhost`. This is not secure, and only works on a test client.

Currently, the process of configuring a service in Truid requires contacting the Truid support and ask for a service to be registered.

### Start the backend

Prerequisites:
- Client ID and Secret
- Docker

Start the backend:

```
$ export TRUID_CLIENT_ID=...
$ export TRUID_CLIENT_SECRET=...
$ docker-compose up
```

### Try the web client

Point your browser to `http://localhost:8080/index.html`.

### Start the app

### Android 

Prerequisites:
Android Studio with emulator supporting Android API v28 or later and including Google Play Store


`$ npm install -g yarn`

Setup:

```
$ cd example-app
$ yarn install
```

Start the emulator in Android Studio - Device manager

Give emulator access to host localhost:8080 where example-backend is running

`$ adb reverse 8080`

Start the app

`$ yarn run android`

### Ios
TBD
