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

## Try it on Android + Mac/Windows
### Register a Service

Register a Service in Truid to obtain OAuth2 client credentials. The service need the following properties:
- Test Service: `true`
- `redirect_uris`: `http://localhost:8080/truid/v1/complete-signup`, `http://localhost:8080/truid/v1/complete-login`

_Note:_

The redirect uris runs over plain `http` and on `localhost`. This is not secure, and only works on a test client.

Currently, the process of configuring a service in Truid requires contacting the Truid support and ask for a service to be registered.

### Start the backend

Prerequisites:
- Client ID and Secret
- Docker

Start the backend:

```bash
$ export TRUID_CLIENT_ID=...
$ export TRUID_CLIENT_SECRET=...
$ docker-compose up
```

### Try the web client on separate device (QR flow)

Point your mac/windows browser to `http://localhost:8080/index.html`.

Select either `Onboarding session` or `Login session`

### Try the web client on same device

Connect the Android phone using USB, developer mode need to be activated on the phone

Forward the 8080 port to localhost:

```bash
$ adb reverse tcp:8080 tcp:8080
```

On the phone, point the browser to `http://localhost:8080/index.html`

Select either `Onboarding session` or `Login session`

### Try the App to App Flow

### Android

Prerequisites:
1. Android Studio
2. Emulator supporting Android API v28 or later and including Google Play Store or a USB connected Android Phone with developer mode activated
3. Yarn (Install by running `$ npm install -g yarn`)

Setup:
Start the emulator in Android Studio - Device manager or connect your phone with USB

Give emulator/phone access to host localhost:8080 where example-backend is running

```bash
$ adb reverse tcp:8080 tcp:8080
```

Start the example app

Look in example-app/.env and ensure it correct:
```
TRUID_EXAMPLE_DOMAIN=http://localhost:8080
```

```bash
$ yarn run android
```
The app should install and start automatically 

_Note_

When being redirected back to the Example App Android will ask if the link should be opened in the app or the browser. This can be avoided in a production environment if the domain in the redirect url is verified.
On Android the URL should work as an [Android App Link](https://developer.android.com/training/app-links/verify-android-applinks) to open the app, and on iOS it should works as an [Universal Link](https://developer.apple.com/documentation/xcode/allowing-apps-and-websites-to-link-to-your-content) to open the app.


## Try it on IOS + Mac

On iOS we need to run on a physical phone, the simulators doesn't work because we cannot reach app store and install the Truid App.


### Register a Service
On your Mac, find the hostname your phone can use to access example-backend running in docker locally. Can be found under System Preferences -> Sharing
It should be an address that ends with .local. (lets call this address `your_hostname.local`)

Register a Service in Truid to obtain OAuth2 client credentials. The service need the following properties:
- Test Service: `true`
- `redirect_uris`: `http://<your_hostname.local>:8080/truid/v1/complete-signup`, `http://<your_hostname.local>:8080/truid/v1/complete-login`, `truidtest://truid/v1/complete-signup`, `truidtest://truid/v1/complete-login`

The first redirect uri is used in the browser based use case and the second one is used in the app to app use case. In a production environment there only need to one redirect uri on the format https://your.domain/path
But because we are running localhost in this example code we had to split them up for ios.

_Note:_

The redirect uris runs over plain `http` on the special top domain `.local`. This is not secure, and only works on a test client.

Currently, the process of configuring a service in Truid requires contacting the Truid support and ask for a service to be registered.

### Start the backend

Prerequisites:
- Client ID and Secret
- Docker
- `your_hostname.local` from your Mac

Start the backend:
If you want to test web client:
```bash
$ export TRUID_REDIRECT_URI=http://<your_hostname.local>:8080/truid/v1/complete-signup
```
If you want to test app to app:
```bash
$ export TRUID_REDIRECT_URI=truidtest://truid/v1/complete-signup
```
In a production environment, where we can use a registered domain, the redirect uri can be the same in both cases.  

```bash
$ export TRUID_CLIENT_ID=...
$ export TRUID_CLIENT_SECRET=...
$ export TRUID_EXAMPLE_DOMAIN=http://<your_hostname.local>:8080
$ docker-compose up
```

### Try the web client on separate device (QR flow)

Point your mac/windows browser to `http://<your_hostname.local>:8080/index.html`.

Select either `Onboarding session` or `Login session`.

_Note:_

The QR flow for remote login is not yet fully implemented, and will return access denied.

### Try the web client on same device

Connect the iPhone using USB

On the phone, point the browser to `http://<your_hostname.local>:8080/index.html`

Select either `Onboarding session` or `Login session`.

### Try the App to App Flow
Prerequisites:
1. Xcode
2. Yarn (Install by running `$ npm install -g yarn`)

Open the ios workspace in Xcode (example-app/ios/ExampleApp.xcworkspace (note: not ExampleApp.xcodeproj))
In order to run react native on an iphone code signing is required in Xcode, this is covered in [react-native documentation](https://reactnative.dev/docs/running-on-device) 

Edit example-app/.env to
```
TRUID_EXAMPLE_DOMAIN=http://<your_hostname.local>:8080
```

Start the example app on your device in Xcode

