Vendored from https://github.com/Xmax-AI/XmaxSDK-iOS at d4c9c3340a0095370efdd163ae8c85e6c2c8c594 (MIT).

Compatibility with Xcode 15.4 / Swift 5.10: explicit closure captures, legacy AVCaptureSession notification names and UIWindowScene orientation access, and compiler guards around optional iOS 26 frame interpolation. Core streaming and model APIs are unchanged. Agora remains pinned to 4.6.4 and Tencent COS to the upstream checksummed 6.5.7 binaries.

SKI integration disables Agora audio-session management because Gemini Live exclusively owns microphone capture and playback. This package is used for video-only external streams.

Custom storage endpoints use `QCloudCOSXMLEndPoint(literalURL:)`, as documented by Tencent at https://cloud.tencent.com/document/product/460/79504. The upstream base `QCloudEndPoint` raises an uncaught Objective-C exception in `serverURLWithBucket:appID:regionName:`. Confirmed by device crash reports on 2026-11-14.
