# SHADOW native UI: sample screens

> **Các màn dưới đây là bản mẫu giao diện, không gọi máy chủ.** Luồng thật — camera, WebSocket, AI — nằm ở
> [SHADOW_MOBILE.md](SHADOW_MOBILE.md) và là thứ app mở ra khi khởi động. Các màn mẫu này giữ lại làm phương án
> dự phòng khi mất mạng và để tham chiếu thiết kế; bấm "Đóng" ở màn máy chủ để quay về.

Both platforms have the complete **sample flow** from the screen-flow PDF: wardrobe, suggestions, full-screen try-on, local history, teaching, reviewing, guiding, question/answer and settings. SwiftUI / Jetpack Compose render the screens directly, with no embedded HTML.

This is an implementation of the UI prototype, not a camera/AI engine. The app labels sample content. Hardware capture, speech transcription, real garment photos and live AI assessment live in the server-backed flow instead, not in these screens.

## Flow coverage

| Screen/branch | iOS source | Android source |
|---|---|---|
| Four-tab home | `LookHomeView` | `LookHomeScreen` |
| Wardrobe + empty/loading/retry | `StudioWardrobeView` | `StudioWardrobeContent` |
| Add sample garment | `StudioAddItemView` | `StudioAddItemScreen` |
| Camera introduction / permission concept | `StudioCameraIntroView` | `StudioCameraIntroScreen` |
| Item detail, edit, delete | `StudioItemView` | `StudioItemScreen` |
| Occasion + notes | `StudioOccasionView` | `StudioOccasionScreen` |
| Generating / results | `StudioGeneratingView`, `StudioSuggestionsView` | `StudioGeneratingScreen`, `StudioSuggestionsScreen` |
| Outfit detail, save | `LookOutfitView` | `LookOutfitContent` |
| Full-screen camera fixture | `LookCameraView` | `LookCameraScreen` |
| Capture, save/retry, retake | `LookCaptureView` | `LookCaptureScreen` |
| History: all / try-ons / outfits / skills | `LookHistoryView` | `LookHistoryScreen` |
| Saved look detail, retry/delete | `LookDetailView` | `LookDetailScreen` |
| Learning hub + saved skills | `StudioLearningView` | `StudioLearningContent` |
| Prepare / record sample | `StudioTeachView`, `StudioRecordingView` | `StudioTeachScreen`, `StudioRecordingScreen` |
| Prepare sample steps / review/edit | `StudioCompileView`, `StudioReviewView` | `StudioCompileScreen`, `StudioReviewScreen` |
| Save confirmation | `StudioSavedSkillView` | `StudioSavedSkillScreen` |
| Skill detail, start/delete | `StudioSkillDetailView` | `StudioSkillDetailScreen` |
| Guide, next/previous, completion | `StudioGuideView` | `StudioGuideScreen` |
| Step-specific demo Q&A | `StudioAskView` | `StudioAskScreen` |
| Appearance / clear local data | `StudioSettingsView` | `StudioSettingsScreen` |
| UI components | `StudioComponentsView` | `StudioComponentsScreen` |
| Storage errors | Inline recovery state + error toast in the affected screen; snapshot/edit content retained |
| Network loading/errors | Existing `HomeView` / `HomeScreen` server flow, plus shared success/error feedback |

Permission screens explain the intended flow and continue with sample content. They do not falsely request or claim device access. “Recording” has explicit start/stop demo actions; compilation prepares a fixed lesson fixture and does not analyze typed text or video. Q&A shows a step-specific illustrative hint, never a simulated live-AI answer.

## Clean Architecture

```
iosApp/iosApp/
  Features/Looks/{LookModel,Components,Screens}
  Features/Studio/{StudioModel,Components,Screens}
  Navigation/{RootView,StudioRoute,StudioDestination}
androidApp/.../android/
  features/looks/{components,screens}
  features/studio/{components,screens}
  navigation/{AppNavHost,StudioRoute,StudioDestination}
shared/src/commonMain/.../
  presentation/looks/{LookState,LookAction,LookStore}
  presentation/studio/{StudioState,StudioAction,StudioEffect,StudioStore}
  presentation/common/UiMessage
  domain/model/{SavedLook,Studio}
  domain/repository/{LookRepository,StudioRepository}
  domain/usecase/{BuildDemoSkillUseCase,SuggestDemoOutfitsUseCase}
  data/local/{SavedLookEntity,StudioItemEntity,StudioSkillEntity,StudioSettingsEntity}
  data/repository/{RealmLookRepository,RealmStudioRepository}
  di/{AppContainer,LocalDataContainer}
```

Views render state and send actions. Repository interfaces and sample-generation rules live in Domain. Realm objects never escape Data. DI injects repository factories, dispatcher, clock and ID generator. Native navigation belongs to the navigation layer; routes carry IDs, and detail screens handle missing records. File-local screen components keep UI files separate.

Android uses **distinct ViewModel keys** for the two root stores, avoiding generic type-erasure collisions in `rememberStore`. iOS bridges StateFlow with ObservableObject and cancels subscriptions when released. Stores cancel their coroutine scopes on disposal.

## Loading, toast and error contracts

- `loading`: database observation is starting; show a progress indicator and avoid premature empty/not-found states.
- `busy` / `saving`: an operation is pending; show progress and disable repeat submission. Back/dismiss is guarded while a write is active.
- `UiMessage`: buffered one-shot channel for success/error feedback. iOS displays an accessible glass toast; Android displays a queued Snackbar above the bottom controls.
- Save success is emitted only after the repository transaction returns. Failure emits an error and retains the capture, review or item draft. Retry uses stable IDs, preventing duplicate records.
- Navigation effects are distinct from feedback. A successful item addition opens item detail; a saved lesson opens confirmation. No navigation or success toast is triggered by a failed save.
- Validation errors are visible inline and via feedback. Reads can retry. Network errors remain in the existing service screen as well as toast feedback.

The stores do not add fake delays to fast local operations. Loading lasts for the actual asynchronous operation; pending-write tests use a gated fake repository.

## Local data

Realm schema **3** adds sample wardrobe, local skills and appearance settings, preserving existing notes and looks. No destructive migration or cloud-sync integration is enabled. Existing operating-system backup policy is unchanged.

- A capture is an immutable preset/occasion/before/mirror snapshot, not an actual photo.
- Sample wardrobe entries support rename and deletion. Demo suggestions reference only items still present in that local wardrobe.
- Reviewed skills persist the edited title and ordered steps. History, learning hub and guide observe the same saved records.
- Clear-data confirmation deletes wardrobe samples, saved looks and skills in one Realm transaction; notes and appearance preferences are retained.
- Dark/light and glass preferences survive restart. Unsaved drafts survive normal navigation and Android configuration changes, but are not persisted across process death.

## Glass and accessibility

- iOS: `glassEffect` on iOS 26+, Material on older supported versions; Reduce Transparency or disabling glass selects an opaque surface.
- Android: native Material layout with translucent gradients, edge highlights and rounded floating controls. It is an optical counterpart, not Apple's renderer.
- Both use glass for controls/navigation, readable content panels, safe areas, native text inputs and labelled controls. Camera chrome stays dark for contrast over the illustration.
- Toast text is announced on iOS; Android Snackbar supplies accessibility semantics. No looping animation is used.

## Verification and limits

Performed here:

- Swift frontend parsing of the feature and navigation sources.
- Xcode project plist and source membership checks.
- Style/material and native illustration component type checking against the installed SDK.
- A limited additional type check of non-State SwiftUI screens with temporary Shared interface signatures. This is not compilation against the generated Kotlin framework.
- Diff/whitespace checks.

Added shared tests for full wardrobe/skill flows, edited-step persistence, stable-ID retries, duplicate-write guards, loading, success/error feedback ordering, clear-data behavior and read recovery. Added Realm integration tests for schema 2 → 3, reopen and retention of notes/settings. **These Kotlin/Realm tests have not run here.**

Gradle/Android and the Xcode Kotlin build phase require JDK 17, which is absent. CoreSimulator and the SwiftUI State macro plugin are also restricted by this sandbox. A full native build, simulator/device interaction, small-screen/large-text visual QA and real AI validation are still unverified. Do not treat the original template's historical build results as verification of this change.

Run on a configured machine:

```sh
./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug
./gradlew :shared:iosSimulatorArm64Test
# Intel Mac: use :shared:iosX64Test instead.
```

Then build scheme `iosApp` in Xcode. Exercise add/edit/delete wardrobe → suggestions → capture/save → history/retry; teach → stop → compile → edit/save → history → guide/Q&A; and dark/light/glass, failed save/retry, repeated taps and relaunch. Configure the existing service endpoint separately to validate real server-backed AI behavior.
