# Native Xmax try-on

The wardrobe opens LiveTryOnView with an explicitly selected item. Opening the try-on screen without an item leaves selection empty. Items with a reference image can be selected. The current UI sends one item per session; it does not combine multiple selected garments.

The native model uses the connected ShadowStore endpoint and its scoped authentication headers:

- POST /api/tryon/xmax/session with {"item_ids":["selected-id"]}.
- Decode token, environment, prompt, reference_image_url (or item.image_url), maxSeconds.
- Download the reference with same-origin authentication, encode JPEG, upload via Xmax storage.
- Create the front-camera stream, attach the native preview, connect and attach the remote track, start x2.1 generation.
- Mark the item applied only when remote rendering reports positive FPS.
- On stop, dismissal, background, SDK failure, startup timeout, or session expiry: close SDK resources and POST /api/tryon/xmax/close with the temporary token.

No permanent Xmax key is embedded in the app. The deployed backend must issue short-lived tokens. The Vidu config endpoint is not used by this screen.

SDK is resolved from the GitHub fork pinned below. The existing Vendor/XmaxSDK source snapshot and LOCAL_CHANGES.md are retained for reference, but are not used as the Xcode package dependency. Agora is pinned to 4.6.4 to match this SDK.

## Device acceptance checks

1. Connect to the intended backend; open wardrobe and select an item with an image.
2. Grant camera access. Confirm local preview while the generated stream starts, then inspect that the selected garment appears and follows body movement.
3. Select a different garment and apply; verify the old session closes and the new garment appears.
4. Stop, close, and background during both startup and active generation; verify camera indicator turns off and backend session closes.
5. Deny camera permission or disconnect network; verify an error is shown, no applied checkmark appears, and retry remains possible.
6. Leave running to the 120-second limit; verify teardown and retry.

A successful unsigned device build verifies compilation, not visual try-on quality or real-device streaming. Run these checks on an iPhone with a configured signing team.

## Home camera versus separate session

The wardrobe Apply action presents “Thử trên camera Shadow”, “Mở phiên thử đồ mới”, and Cancel. Opening or cancelling the menu does not start a session. The Home destination creates a native try-on model owned by RootView and renders only its camera/video surface behind ShadowCameraScreen's existing controls and conversation. No wardrobe strip, progress card, or additional dimming layer is placed over the Home camera. Errors use the same alert/retry flow. The separate destination still presents LiveTryOnView.

While Home try-on owns camera capture, NativeCameraChatModel suspends its own camera and avoids submitting stale original-camera snapshots; text/audio conversation remains available. Closing try-on waits for its cleanup before returning capture to the original camera. Verify camera switching, dismissing the wardrobe, backgrounding, and retry from an error on an iPhone.

Camera handoff releases native capture inputs before starting Xmax, and creates a fresh native session/preview after SDK close completes. `Library/Caches/camera-handoff.log` contains the last 100 lifecycle events (no frames, credentials, or garment data), including SDK close start/end and the first native frame after returning.

## Remote SDK dependency

Xcode resolves `https://github.com/tyty301299/XmaxSDK-iOS.git` at revision `afd9e00c963f9794fb8c025c60243adce5696861` (branch `fix/ski-native-compatibility`). No local SDK directory is needed. This fork preserves the Swift compiler/concurrency and COS endpoint fixes; see `SKI_CHANGES.md` in the fork.

Signed iPhone device build passed after resolving the pinned remote SDK revision. The reported frozen camera after closing try-on still needs device reproduction/verification; do not treat a successful build as runtime confirmation.
