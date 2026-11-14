# SKI native Live conversation

The iOS camera starts Live automatically after backend and microphone access are
available. Audio goes directly to the same ephemeral-token WebSocket endpoint as
the website. A voice turn no longer uses speech recognition, one REST message and
device text-to-speech. The microphone stays active while SKI replies and after each
turn; a server interruption stops queued playback. Typed messages during Live use
the same connection. Explicit typing remains available if Live cannot connect.

The native audio engine sends mono signed PCM16 at 16 kHz and plays PCM at 24 kHz.
Voice-processing I/O provides echo cancellation. Camera JPEG frames are sent every
1.1 seconds while the camera is enabled. Backgrounding, leaving the camera,
opening settings, changing the backend, disconnecting or reaching the server's
session limit releases microphone, playback, camera streaming and pending tools.
There is no automatic retry loop after provider errors or session expiry.

`/api/live/session` and `/api/live/tool` retain the existing access header. Permanent
provider keys stay on the backend. The tool bridge forwards current GPS and the
spoken request, preserving requested seasons/dates; clearing GPS sends null to
invalidate a previous location. Outfit suggestions render above the transcript.
Saved skills can start in the camera and respond to next, previous, repeat and stop.

The web-specific video fitting renderer and lesson recording/editing are not
ported into this native audio change. Those tool calls return an explicit
unavailable result, so the assistant cannot truthfully claim it has changed clothes
or saved a lesson. Existing wardrobe and lesson screens remain accessible.

Validation: `bash tools/test-native-live.sh` runs the real session coordinator
against fake audio/socket/HTTP boundaries: streaming, partial transcripts,
interruption, turn completion, typed follow-ups, tool execution/deduplication, GPS
revocation, cancellation during connection, safe provider errors and expiry.
These checks do not replace iPhone microphone/speaker/camera testing.

Native sources also typecheck against the generated Shared framework and iOS SDK.
The current automation environment cannot launch Xcode successfully, so no new
signed iPhone build has been installed from this session.

Protocol references: [Gemini Live WebSocket API](https://ai.google.dev/api/live),
[ephemeral tokens](https://ai.google.dev/gemini-api/docs/ephemeral-tokens),
[Apple sample-rate conversion](https://developer.apple.com/documentation/technotes/tn3136-avaudioconverter-performing-sample-rate-conversions).
