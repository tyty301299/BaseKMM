# SHADOW internal TestFlight

The app's default backend is `https://hackathon.trioxstudios.com`. Connection settings
remain editable and any previously saved URL is preserved. Debug retains the local
development Bundle ID; Release uses `com.trioxstudios.shadow` and team `GJD9MS8W54`
(Tran Minh Tien, from the existing local provisioning profile).

## Apple setup

1. In [Certificates, Identifiers & Profiles](https://developer.apple.com/account/resources/identifiers/list),
   register an explicit App ID: `com.trioxstudios.shadow`, description `SHADOW`.
2. In [App Store Connect](https://appstoreconnect.apple.com/apps), create an iOS app:
   name `SHADOW by Triox` (subject to availability), primary language Vietnamese,
   that Bundle ID, SKU `shadow-ios-2026`. This creates the app record; it does not
   submit an App Store review.
3. Use a Mac or macOS runner with Xcode 26+ / iOS SDK 26+, JDK 17, and access to the
   Apple team for automatic signing. Sign in through Xcode Settings > Accounts, or
   configure `ASC_KEY_PATH`, `ASC_KEY_ID`, `ASC_ISSUER_ID` for an App Store Connect key.
   Keep the `.p8` key and signing assets outside this repository.
4. Run `./tools/testflight.sh check`, then `./tools/testflight.sh upload`.
   `archive` builds an archive without uploading. Override `APPLE_TEAM_ID`,
   `IOS_BUNDLE_ID`, `BUILD_NUMBER` or `RELEASE_VERSION` when needed.
5. After Apple's build processing, add the build to an **internal** TestFlight group
   with eligible App Store Connect users. The script exports with
   `testFlightInternalTestingOnly=true`; it cannot distribute the build publicly,
   to external testers, or submit it to the App Store.

Device UDID registration is not needed for TestFlight. Export compliance questions,
if shown by Apple, still need to be answered for the app's actual SDKs and behavior.

The app privacy manifest declares UserDefaults access for its own preferences
(language, endpoint, access code, resumable lesson import). Archive validation must
also check the bundled SDKs' manifests; this is not a full privacy-label audit.

## Verified on 2026-11-14

- Shared tests and the iOS simulator Kotlin framework compile on the local toolchain.
- The TestFlight preflight correctly stops on this Mac's Xcode 15.4. No archive or
  upload has been completed with Xcode 26; compatibility of the existing Kotlin and
  Agora dependencies with that SDK still needs archive validation.
- No signing identity is accessible to this tool session. A current provisioning
  profile identifies the team, but does not provide a signing private key or Apple login.
- The Apple browser tab is not exposed by the connected-document tool, so the App ID
  and App Store Connect app record have not been created.
- The production backend is live at `https://hackathon.trioxstudios.com`.
  Public HTTPS health, wardrobe, conversation and WSS checks pass with normal DNS
  and certificate validation. DNS/HTTPS setup is complete.

[Apple SDK requirements](https://developer.apple.com/news/upcoming-requirements/?id=04282026a)
and [internal TestFlight testing](https://developer.apple.com/help/app-store-connect/test-a-beta-version/add-internal-testers/).
