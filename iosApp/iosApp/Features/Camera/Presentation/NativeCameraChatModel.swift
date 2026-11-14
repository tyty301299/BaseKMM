import SwiftUI
import AVFoundation
import Shared

struct LiveOutfitItem: Identifiable {
    let id: String
    let name: String
    let imageURL: String
    var gender = "unknown"
    var category = ""
}

@MainActor
final class NativeCameraChatModel: ObservableObject {
    @Published private(set) var state: ConversationState
    @Published private(set) var live = LiveSnapshot()
    @Published var text = ""
    @Published private(set) var cameraGranted = false
    @Published private(set) var cameraAvailable = true
    @Published private(set) var cameraEnabled = true
    @Published private(set) var notice: String?
    @Published private(set) var outfitTitle = ""
    @Published private(set) var outfitReason = ""
    @Published private(set) var outfitItems: [LiveOutfitItem] = []
    @Published private(set) var showsOutfit = false
    @Published private(set) var lessonTitle = ""
    @Published private(set) var lessonInstruction = ""
    @Published private(set) var lessonStep = 0
    @Published private(set) var lessonSteps = 0
    @Published private var liveLinks: [ConversationLink] = []
    var getLocation: () -> [String: Double]? = { nil }
    var getCity: () -> String? = { nil }
    @Published private(set) var selectedWardrobeIDs: [String] = []
    @Published var wardrobePresented = false
    @Published private(set) var wardrobeItems: [LiveOutfitItem]?
    @Published private(set) var wardrobeTotal = 0
    private var galleryRevision: Int32 = 0
    let camera = NativeServices.makeCamera()
    private let session = NativeServices.makeLiveSession()
    let teaching = NativeLessonSession()
    private let store: ConversationStore
    private var subscription: StoreSubscription?
    private var active = false
    private var front = false
    private var wantsLive = true
    private var endpoint = ""
    private var headers: [String: String] = [:]
    private var backendConnected = false
    private var epoch = 0
    private var startTask: Task<Void, Never>?
    private var lesson: [String: Any]?
    private var lessonIndex = 0
    private var lessonID: String?

    var listening: Bool { live.connected || live.status == .connecting }
    var canCancelResponse: Bool { text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && (state.busy || live.status == .speaking || live.status == .thinking) }
    var links: [ConversationLink] { live.messages.isEmpty ? state.links : liveLinks }
    var messages: [LiveTranscript] {
        if !live.messages.isEmpty { return live.messages }
        var result = state.turns.map { LiveTranscript(role: $0.role, text: $0.text) }
        if !state.reply.isEmpty && result.last?.text != state.reply { result.append(LiveTranscript(role: "assistant", text: state.reply)) }
        return result
    }

    init(store: ConversationStore) {
        self.store = store; state = store.currentState
        subscription = store.observeState { [weak self] value in
            guard let self else { return }
            let previousLinks = self.state.links
            self.state = value
            if value.galleryRevision != self.galleryRevision {
                self.galleryRevision = value.galleryRevision
                if let items = value.wardrobe {
                    self.wardrobeItems = items.map { LiveOutfitItem(id: $0.id, name: $0.name, imageURL: $0.imageUrl, gender: $0.gender, category: $0.category) }
                    self.wardrobeTotal = Int(value.wardrobeTotal)
                    self.wardrobePresented = true; self.showsOutfit = false
                } else if let outfit = value.outfits.first {
                    self.outfitTitle = outfit.title; self.outfitReason = outfit.reason
                    self.outfitItems = outfit.items.map { LiveOutfitItem(id: $0.id, name: $0.name, imageURL: $0.imageUrl, category: $0.category) }
                    self.wardrobePresented = false; self.showsOutfit = true
                }
            }
            if value.links != previousLinks, value.links.contains(where: { $0.route == "wardrobe" }), !self.wardrobePresented {
                self.openWardrobe()
            }
        }
        session.onChange = { [weak self] snapshot in
            self?.live = snapshot
            self?.teaching.observe(snapshot.messages)
            if snapshot.status == .failed { self?.wantsLive = false }
            if snapshot.status == .idle || snapshot.status == .failed { self?.teaching.suspend() }
        }
        teaching.capture = { [weak self] in
            guard let self, self.active, self.cameraGranted, self.cameraEnabled else { return nil }
            return self.camera.capture(maxSide: 512)
        }
        teaching.onChange = { [weak self] in self?.objectWillChange.send() }
        teaching.notify = { [weak self] in self?.session.notifyTaskResult($0) }
        teaching.request = { [weak self] method, path, body in
            guard let self, self.active, let url = URL(string: path, relativeTo: URL(string: self.endpoint))?.absoluteURL else {
                throw LiveFailure(message: "Camera đã đóng.")
            }
            var request = URLRequest(url: url, timeoutInterval: 180)
            request.httpMethod = method; request.httpBody = try JSONSerialization.data(withJSONObject: body)
            self.headers.forEach { request.setValue($1, forHTTPHeaderField: $0) }
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            let (data, response) = try await URLSession.shared.data(for: request)
            try Task.checkCancellation()
            guard let code = (response as? HTTPURLResponse)?.statusCode, (200..<300).contains(code),
                  let result = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                throw LiveFailure(message: "Chưa xử lý được bài học. Nội dung vẫn còn để thử lại.")
            }
            return result
        }
        session.getFrame = { [weak self] in
            guard let self, self.active, self.cameraGranted, self.cameraEnabled else { return nil }
            return self.camera.capture(maxSide: 640)
        }
        session.getLocation = { [weak self] in self?.getLocation() }
        session.getCity = { [weak self] in self?.getCity() }
        session.getTaskContext = { [weak self] in self?.taskContext() ?? ["current_task": "chat"] }
        session.onEvent = { [weak self] in self?.handleEvent($0) }
        session.executeClientControl = { [weak self] result in
            guard let self, self.active else { return ["status": "cancelled", "error": "Camera đã đóng."] }
            return await self.executeControl(result)
        }
    }

    func configure(endpoint: String, headers: [String: String], connected: Bool) {
        if self.endpoint != endpoint || self.headers != headers {
            let wasActive = active
            stop(); session.clear(); store.clear(); teaching.reset(); liveLinks = []; outfitItems = []; showsOutfit = false; clearLesson()
            self.endpoint = endpoint; self.headers = headers; wantsLive = true
            active = wasActive
            if active && cameraGranted && cameraEnabled { camera.start(front: front) }
        }
        backendConnected = connected
        if active && connected { connectLive() }
    }

    func start() {
        active = true
        cameraAvailable = AVCaptureDevice.default(for: .video) != nil
        if cameraAvailable {
            ShadowCamera.requestAccess { [weak self] granted in
                guard let self else { return }
                self.cameraGranted = granted
                if granted && self.active && self.cameraEnabled { self.camera.start(front: self.front) }
            }
        }
        // Audio Live is usable even if the camera is unavailable or permission was declined.
        connectLive()
    }
    func stop() {
        epoch += 1; active = false
        startTask?.cancel(); startTask = nil
        teaching.suspend(); session.stop(); camera.stop(); store.cancel()
    }
    func toggleCamera() {
        cameraEnabled.toggle()
        if cameraEnabled && active { start() } else { teaching.suspend(); camera.stop() }
    }
    func flip() { front.toggle(); if active && cameraEnabled { camera.flip(front: front) } }
    func toggleMic() {
        if listening || startTask != nil {
            wantsLive = false; epoch += 1; startTask?.cancel(); startTask = nil; teaching.suspend(); session.stop()
        } else { wantsLive = true; notice = nil; connectLive() }
    }
    private func connectLive() {
        guard active, wantsLive, backendConnected, startTask == nil, !listening,
              let origin = URL(string: endpoint), ["https", "http"].contains(origin.scheme), origin.host != nil else { return }
        let generation = epoch
        startTask = Task { [weak self] in
            let permission = await withCheckedContinuation { continuation in
                AVCaptureDevice.requestAccess(for: .audio) { continuation.resume(returning: $0) }
            }
            guard let self, self.active, self.epoch == generation, !Task.isCancelled else { return }
            defer { if self.epoch == generation { self.startTask = nil } }
            guard permission else { self.wantsLive = false; self.notice = "Cho phép micro trong Cài đặt để trò chuyện Live."; return }
            self.notice = nil; self.store.cancel()
            await self.session.start(origin: origin, headers: self.headers)
        }
    }
    func send() {
        let prompt = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !prompt.isEmpty else { return }
        if session.sendText(prompt) { text = ""; notice = nil; return }
        if live.status == .connecting { notice = "Live đang kết nối. Bạn đợi một chút nhé."; return }
        guard !state.busy else { return }
        // Explicitly typed fallback. Voice never goes through on-device dictation + REST + TTS.
        session.clear(); liveLinks = []; text = ""; notice = nil
        let image = cameraEnabled && cameraGranted && active ? camera.capture(maxSide: 640) : nil
        store.send(text: prompt, image: image)
    }
    func cancel() { if live.connected { session.cancelResponse() } else { store.cancel() } }
    func clear() { stop(); session.clear(); store.clear(); teaching.reset(); text = ""; wantsLive = true; clearLesson(); outfitItems = []; showsOutfit = false }

    func syncLocation() { session.syncLocationContext() }
    func openWardrobe() { wardrobeItems = nil; wardrobePresented = true; showsOutfit = false }
    func selectWardrobe(_ ids: [String]) { selectedWardrobeIDs = Array(ids.prefix(3)) }
    func styleSelectedWardrobe(names: [String]) {
        text = String(("Tư vấn phối đồ với các món tôi vừa chọn trong tủ đồ: " + names.joined(separator: ", ")).prefix(500))
        send()
    }
    func saveTeaching() { _ = teaching.control(["command": "save", "revision": teaching.revision], messages: live.messages) }

    func imageHeaders(_ value: String) -> [String: String] {
        guard let url = URL(string: value), let base = URL(string: endpoint), url.scheme == base.scheme, url.host == base.host, url.port == base.port else { return [:] }
        return headers
    }
    private func handleEvent(_ event: [String: Any]) {
        if event["type"] as? String == "wardrobe" {
            wardrobeItems = (event["items"] as? [[String: Any]])?.compactMap { item in
                guard let id = item["id"] as? String else { return nil }
                let raw = item["image_url"] as? String ?? ""
                let url = URL(string: raw, relativeTo: URL(string: endpoint))?.absoluteURL
                let image = ["https", "http"].contains(url?.scheme) ? url?.absoluteString ?? "" : ""
                return LiveOutfitItem(id: id, name: item["name"] as? String ?? "Trang phục", imageURL: image,
                    gender: item["gender"] as? String ?? "unknown", category: item["category"] as? String ?? "")
            }
            wardrobeTotal = event["total"] as? Int ?? wardrobeItems?.count ?? 0
            wardrobePresented = true; showsOutfit = false
        } else if event["type"] as? String == "outfits", let outfit = (event["outfits"] as? [[String: Any]])?.first {
            outfitTitle = outfit["title"] as? String ?? "Trang phục gợi ý"
            outfitReason = outfit["reason"] as? String ?? ""
            outfitItems = (outfit["items"] as? [[String: Any]] ?? []).compactMap { item in
                guard let id = item["id"] as? String else { return nil }
                let raw = item["image_url"] as? String ?? "/wardrobe-media/\(id).jpg"
                let url = URL(string: raw, relativeTo: URL(string: endpoint))?.absoluteURL
                let image = ["https", "http"].contains(url?.scheme) ? url?.absoluteString ?? "" : ""
                return LiveOutfitItem(id: id, name: item["name"] as? String ?? "Trang phục", imageURL: image)
            }
            wardrobePresented = false
            showsOutfit = true
        } else if event["type"] as? String == "action", let route = event["route"] as? String {
            if route == "wardrobe" { if !wardrobePresented { openWardrobe() }; return }
            liveLinks = [ConversationLink(route: route, label: event["label"] as? String ?? "Mở", id: event["id"] as? String ?? "")]
        }
    }
    private func taskContext() -> [String: Any] {
        if teaching.hasWork { return ["current_task": teaching.mode, "task_title": teaching.title, "lesson_state": teaching.snapshot] }
        var value: [String: Any] = ["current_task": lesson == nil ? "chat" : "guiding", "suggested_item_ids": outfitItems.prefix(3).map(\.id), "selected_item_ids": selectedWardrobeIDs]
        if lesson != nil { value["lesson_state"] = lessonSnapshot(); value["task_title"] = lessonTitle }
        return value
    }
    private func executeControl(_ result: [String: Any]) async -> [String: Any] {
        if let control = result["live_control"] as? [String: Any], let view = control["view"] as? String {
            switch view {
            case "dismiss_outfits": showsOutfit = false
            case "show_outfits": showsOutfit = !outfitItems.isEmpty
            case "original": if !cameraEnabled { toggleCamera() }
            default: return ["status": "unavailable", "error": "Chưa có video mặc thử đang chạy trên iPhone. Không báo đã mặc xong."]
            }
            return ["status": "ready", "view": view]
        }
        if let control = result["lesson_control"] as? [String: Any] {
            return await controlLesson(control)
        }
        if let decision = result["camera_decision"] as? [String: Any] {
            if decision["camera_command"] as? String == "cancel_task" { showsOutfit = false; return ["status": "cancelled", "message": "Đã đóng gợi ý trang phục."] }
            // The existing native fitting engine is a different provider from the web renderer.
            // Never acknowledge a live fitting that hasn't actually started on this device.
            return ["status": "unavailable", "error": "Video thay đồ của web chưa có bộ dựng native iOS. Có thể tư vấn và xem bộ đồ tại đây, nhưng chưa thể báo đã mặc thử."]
        }
        return ["status": "unavailable", "error": "Thao tác chưa được hỗ trợ."]
    }
    private func controlLesson(_ control: [String: Any]) async -> [String: Any] {
        guard let command = control["command"] as? String else { return ["error": "Thiếu thao tác bài học."] }
        if command == "stop" { teaching.reset(); clearLesson(); return ["status": "stopped"] }
        if !["start_guidance", "next", "previous", "repeat"].contains(command) {
            let result = teaching.control(control, messages: live.messages)
            if command == "start_teaching", result["error"] == nil { clearLesson() }
            return result
        }
        if command == "start_guidance" {
            guard !teaching.hasWork else { return ["error": "Hãy lưu hoặc hủy bài dạy đang ghi trước khi bắt đầu hướng dẫn."] }
            teaching.reset()
            guard let id = control["skill_id"] as? String, !id.isEmpty,
                  id.range(of: "^[A-Za-z0-9_-]+$", options: .regularExpression) != nil,
                  let url = URL(string: "/api/skills/\(id)", relativeTo: URL(string: endpoint))?.absoluteURL else { return ["error": "Cần chọn bài học đã lưu."] }
            let generation = epoch
            var request = URLRequest(url: url, timeoutInterval: 20)
            headers.forEach { request.setValue($1, forHTTPHeaderField: $0) }
            do {
                let (data, response) = try await URLSession.shared.data(for: request)
                try Task.checkCancellation()
                guard active, generation == epoch, (response as? HTTPURLResponse)?.statusCode == 200,
                      let value = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                      let steps = value["steps"] as? [[String: Any]], !steps.isEmpty else { return ["error": "Chưa mở được bài học."] }
                lesson = value; lessonID = id; lessonIndex = 0
            } catch { return ["error": "Chưa tải được bài học. Bạn thử lại nhé."] }
        }
        guard let lesson, let steps = lesson["steps"] as? [[String: Any]], !steps.isEmpty else { return ["error": "Chưa có bài hướng dẫn đang mở."] }
        if command == "next" { lessonIndex = min(steps.count, lessonIndex + 1) }
        if command == "previous" { lessonIndex = max(0, lessonIndex - 1) }
        lessonTitle = lesson["display_name"] as? String ?? "Bài hướng dẫn"
        lessonSteps = steps.count; lessonStep = min(lessonIndex + 1, steps.count)
        lessonInstruction = lessonIndex == steps.count ? "Bạn đã hoàn thành bài học." : steps[lessonIndex]["instruction"] as? String ?? ""
        return ["status": lessonIndex == steps.count ? "completed" : "guiding", "lesson": lessonSnapshot(), "message": "Hướng dẫn đang hiện trên camera. Đọc bước hiện tại, quan sát hình camera và chờ người dùng thực hành."]
    }
    private func lessonSnapshot() -> [String: Any] {
        let steps = lesson?["steps"] as? [[String: Any]] ?? []
        return ["status": lessonIndex >= steps.count ? "completed" : "guiding", "title": String(lessonTitle.prefix(160)), "revision": 0,
                "step": min(lessonIndex, 30), "skill_id": lessonID ?? "",
                "steps": Array(steps.prefix(30).enumerated()).map { index, step in
                    ["number": index + 1, "instruction": String((step["instruction"] as? String ?? "").prefix(800)), "tip": String((step["tip"] as? String ?? "").prefix(400)), "warning": String((step["warning"] as? String ?? "").prefix(400))] as [String: Any]
                }]
    }
    private func clearLesson() { lesson = nil; lessonID = nil; lessonTitle = ""; lessonInstruction = ""; lessonStep = 0; lessonSteps = 0; lessonIndex = 0 }
    deinit {
        subscription?.cancel(); startTask?.cancel(); camera.stop(); store.close()
        let liveSession = session
        Task { @MainActor in liveSession.stop() }
    }
}
