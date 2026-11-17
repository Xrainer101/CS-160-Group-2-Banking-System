# Android client integration

This folder combines the Android feature branch with the Auth0 authentication flow.

## Run

Start the backend with AUTH0_DOMAIN and AUTH0_AUDIENCE configured as described in the root README.
Open this folder as an Android Studio project and run the debug app on an emulator.
The API client uses http://10.0.2.2:8000; debug builds permit HTTP only to that emulator host.
Release builds need an HTTPS backend URL before deployment.

## Authentication and local data

Login, registration, MFA and password reset use Auth0. After the backend validates the access token,
its auth0_user_id maps to a separate local SQLite user ID. Passwords are not copied to SQLite.
The first login creates a local profile and a checking account with a zero balance. Later logins
reuse that profile and its accounts.

Banking features and contact-profile edits are local to the device. They do not synchronize with the
web client or backend accounts. Settings password reset sends an Auth0 reset email; Auth0 account
deletion is not implemented in this local client.

Existing databases from version 3 onward retain their data during upgrade. Existing sessions require
one new login to replace backend numeric user IDs with local IDs. Older, unlinked local profiles are
never automatically assigned to an Auth0 identity based on email, username or numeric ID. A collision
with an old local username/email stops local provisioning and requires an explicit data migration;
the existing data is preserved.

## Verification

From this folder:

```bash
bash gradlew :app:assembleDebug :app:testDebugUnitTest
bash gradlew :app:connectedDebugAndroidTest
```

The instrumentation tests use separate databases and preferences. They cover repeated Auth0 identity
mapping, user isolation, legacy profile collisions, session restoration/logout and version-4 upgrades.
Live Auth0 login, MFA and registration additionally require a configured backend and test credentials.
