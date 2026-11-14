import Foundation

struct LiveTranscript: Identifiable, Equatable {
    let id: UUID
    let role: String
    var text: String
    init(role: String, text: String) { id = UUID(); self.role = role; self.text = text }
}

enum LiveConnectionStatus { case idle, connecting, listening, thinking, speaking, failed }

struct LiveSnapshot {
    var status: LiveConnectionStatus = .idle
    var messages: [LiveTranscript] = []
    var detail = ""
    var error: String?
    var connected: Bool { [.listening, .thinking, .speaking].contains(status) }
}

struct LiveFailure: LocalizedError {
    let message: String
    var errorDescription: String? { message }
    static func provider(_ value: [String: Any], status: Int = 0) -> LiveFailure {
        let detail = value["detail"] as? [String: Any] ?? value["error"] as? [String: Any] ?? value
        let code = detail["code"] as? String ?? ""
        let message: String
        switch code {
        case "insufficient_credits", "billing_required": message = "Dịch vụ trò chuyện chưa đủ tín dụng. Vui lòng nạp tín dụng rồi mở lại Live."
        case "provider_auth_failed", "missing_api_key": message = "Chưa xác thực được dịch vụ trò chuyện. Kiểm tra cấu hình máy chủ."
        case "rate_limited": message = "Trò chuyện đang đạt hạn mức. Bạn thử lại sau ít phút nhé."
        case "provider_timeout": message = "Kết nối trò chuyện quá lâu. Bạn thử mở lại micro nhé."
        default:
            if status == 401 || status == 403 { message = "Chưa xác thực được kết nối. Kiểm tra mã truy cập trong Cài đặt." }
            else if status == 410 { message = "Phiên trò chuyện đã hết hạn. Bấm micro để mở lại." }
            else if status == 429 { message = "Trò chuyện đang bận. Bạn thử lại sau ít phút nhé." }
            else { message = "Chưa kết nối được trò chuyện Live. Bạn có thể nhập câu hỏi trong lúc chờ." }
        }
        // Never surface raw provider messages, close reasons, token URLs or NSError descriptions.
        return LiveFailure(message: message)
    }
}

@MainActor
protocol LiveAudioStreaming: AnyObject {
    func start(onPCM: @escaping (Data) -> Void, onDrained: @escaping () -> Void, onFailure: @escaping () -> Void) throws
    func play(_ pcm: Data, rate: Double) throws
    func interrupt()
    func stop()
}

@MainActor
protocol LiveSocket: AnyObject {
    func send(_ text: String) async throws
    func receive() async throws -> Data
    func close()
}

@MainActor
private final class URLSessionLiveSocket: LiveSocket {
    let task: URLSessionWebSocketTask
    init(url: URL, session: URLSession) {
        task = session.webSocketTask(with: url)
        task.maximumMessageSize = 2_000_000
        task.resume()
    }
    func send(_ text: String) async throws { try await task.send(.string(text)) }
    func receive() async throws -> Data {
        switch try await task.receive() {
        case .data(let data): return data
        case .string(let text): return Data(text.utf8)
        @unknown default: throw LiveFailure(message: "Dữ liệu hội thoại chưa hợp lệ.")
        }
    }
    func close() { task.cancel(with: .normalClosure, reason: nil) }
}

/// Same ephemeral-token/WebSocket/tool protocol as the website. Permanent keys stay on the BE.
/// All state transitions and callbacks run on the main actor; audio capture itself does not.
@MainActor
final class NativeLiveSession {
    private(set) var snapshot = LiveSnapshot()
    var onChange: (LiveSnapshot) -> Void = { _ in }
    var onEvent: ([String: Any]) -> Void = { _ in }
    var executeClientControl: (([String: Any]) async -> [String: Any])?
    var getFrame: () -> String? = { nil }
    var getLocation: () -> [String: Double]? = { nil }
    var getCity: () -> String? = { nil }
    private var lastLocationContext: String?
    var getTaskContext: () -> [String: Any] = { ["current_task": "chat"] }

    private let audio: LiveAudioStreaming
    private let http: URLSession
    private let socketFactory: (URL) -> LiveSocket
    private var socket: LiveSocket?
    private var origin: URL?
    private var headers: [String: String] = [:]
    private var generation = 0
    private var receiveTask: Task<Void, Never>?
    private var sendTask: Task<Void, Never>?
    private var setupTimeout: Task<Void, Never>?
    private var limitTask: Task<Void, Never>?
    private var videoTask: Task<Void, Never>?
    private var tools: [String: Task<Void, Never>] = [:]
    private var completed: [String: (String, [String: Any])] = [:]
    private var outbound: [String] = []
    private var pendingBytes = 0
    private var toolSessionID = ""
    private var toolLabels: [String: [String]] = [:]
    private var currentInput = ""
    private var userMessageID: UUID?
    private var assistantMessageID: UUID?
    private var turn = 0
    private var toolCount = 0
    private var awaitingTurn = true
    private var typedTurnPending = false
    private var discardOutput = false
    private var audioPlaying = false
    private var maxSeconds = 300
    private var initialHistory: [[String: String]] = []
    private var notices: [[String: Any]] = []

    init(audio: LiveAudioStreaming, http: URLSession = .shared, socketFactory: ((URL) -> LiveSocket)? = nil) {
        self.audio = audio
        self.http = http
        self.socketFactory = socketFactory ?? { URLSessionLiveSocket(url: $0, session: http) }
    }

    func start(origin: URL, headers: [String: String]) async {
        guard snapshot.status == .idle || snapshot.status == .failed else { return }
        stop(notify: false)
        let epoch = generation
        self.origin = origin; self.headers = headers
        initialHistory = history()
        snapshot.error = nil
        setStatus(.connecting, "Đang mở trò chuyện Live…")
        do {
            let descriptor = try await request("/api/live/session", body: ["history": initialHistory, "configured_city": getLocation() == nil ? "" : (getCity() ?? "")], timeout: 20)
            guard epoch == generation, !Task.isCancelled else { return }
            guard let token = descriptor["token"] as? String, !token.isEmpty,
                  let rawURL = descriptor["socketUrl"] as? String,
                  var components = URLComponents(string: rawURL), components.scheme == "wss",
                  components.host == "generativelanguage.googleapis.com", components.user == nil,
                  components.password == nil, components.query == nil,
                  let model = descriptor["model"] as? String, !model.isEmpty,
                  let sessionID = descriptor["toolSessionId"] as? String, UUID(uuidString: sessionID) != nil else {
                throw LiveFailure(message: "Máy chủ chưa trả về phiên trò chuyện hợp lệ.")
            }
            components.queryItems = [URLQueryItem(name: "access_token", value: token)]
            guard let url = components.url else { throw LiveFailure(message: "Chưa tạo được kết nối trò chuyện.") }
            toolSessionID = sessionID
            toolLabels = descriptor["toolLabels"] as? [String: [String]] ?? [:]
            maxSeconds = max(1, min(300, descriptor["maxSeconds"] as? Int ?? 300))
            let connection = socketFactory(url)
            socket = connection
            receiveTask = Task { [weak self] in
                do {
                    while !Task.isCancelled {
                        let data = try await connection.receive()
                        guard let self, epoch == self.generation, !Task.isCancelled else { return }
                        guard let value = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                            throw LiveFailure(message: "Dữ liệu hội thoại chưa hợp lệ.")
                        }
                        try self.receive(value, epoch: epoch)
                    }
                } catch {
                    guard let self, epoch == self.generation, !Task.isCancelled else { return }
                    self.fail(error)
                }
            }
            enqueue(["setup": ["model": "models/" + model]])
            setupTimeout = Task { [weak self] in
                try? await Task.sleep(nanoseconds: 15_000_000_000)
                guard !Task.isCancelled, let self, epoch == self.generation, self.snapshot.status == .connecting else { return }
                self.fail(LiveFailure(message: "Mở Live quá lâu. Kiểm tra mạng rồi thử lại nhé."))
            }
        } catch {
            if epoch == generation && !Task.isCancelled { fail(error) }
        }
    }

    @discardableResult func sendText(_ text: String) -> Bool {
        let text = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard snapshot.connected, !text.isEmpty, text.count <= 500 else { return false }
        audio.interrupt(); audioPlaying = false; beginTurn(); typedTurnPending = true
        currentInput = text
        appendTranscript(role: "user", text: text)
        enqueue(["realtimeInput": ["text": text]])
        setStatus(.thinking, "Đang phân tích yêu cầu…")
        return true
    }

    func cancelResponse() {
        guard snapshot.connected else { return }
        audio.interrupt(); audioPlaying = false; cancelTools(); discardOutput = true; awaitingTurn = true
        setStatus(.listening, "Đã dừng phản hồi. Bạn cứ nói tiếp nhé.")
    }

    func notifyTaskResult(_ execution: [String: Any]) {
        guard snapshot.connected else { return }
        notices.append(execution); notices = Array(notices.suffix(3)); flushNotice()
    }
    private func flushNotice() {
        guard awaitingTurn, !audioPlaying, snapshot.status == .listening, !notices.isEmpty,
              let data = try? JSONSerialization.data(withJSONObject: notices.removeFirst()),
              let text = String(data: data, encoding: .utf8) else { return }
        beginTurn()
        enqueue(["realtimeInput": ["text": "[SHADOW_TASK_RESULT] " + text]])
        setStatus(.thinking, "Đã nhận kết quả bài học.")
    }

    func stop(notify: Bool = true) {
        generation += 1
        receiveTask?.cancel(); sendTask?.cancel(); setupTimeout?.cancel(); limitTask?.cancel(); videoTask?.cancel()
        receiveTask = nil; sendTask = nil; setupTimeout = nil; limitTask = nil; videoTask = nil
        cancelTools(); completed.removeAll(); outbound.removeAll(); notices.removeAll(); pendingBytes = 0
        socket?.close(); socket = nil; audio.stop(); lastLocationContext = nil
        toolSessionID = ""; headers = [:]; origin = nil
        awaitingTurn = true; typedTurnPending = false; discardOutput = false; audioPlaying = false
        if notify { snapshot.error = nil; setStatus(.idle, "Live đã tắt. Bấm micro để trò chuyện tiếp.") }
    }

    func clear() { stop(); snapshot = LiveSnapshot(); onChange(snapshot) }

    private func setStatus(_ status: LiveConnectionStatus, _ detail: String = "") {
        snapshot.status = status; snapshot.detail = detail; onChange(snapshot)
        if status == .listening { flushNotice() }
    }
    private func fail(_ error: Error) {
        let message = (error as? LiveFailure)?.message ?? "Kết nối Live đã ngắt. Bấm micro để thử lại; bạn vẫn có thể nhập câu hỏi."
        stop(notify: false)
        snapshot.error = message
        setStatus(.failed, message)
    }
    private func beginTurn() {
        cancelTools(); turn += 1; toolCount = 0
        currentInput = ""; userMessageID = nil; assistantMessageID = nil
        awaitingTurn = false; typedTurnPending = false; discardOutput = false
    }
    private func cancelTools() { tools.values.forEach { $0.cancel() }; tools.removeAll() }
    private func appendTranscript(role: String, text: String) {
        guard !text.isEmpty else { return }
        let id = role == "user" ? userMessageID : assistantMessageID
        if let id, let index = snapshot.messages.firstIndex(where: { $0.id == id }) {
            snapshot.messages[index].text = String((snapshot.messages[index].text + text).prefix(12000))
        } else {
            let message = LiveTranscript(role: role, text: String(text.prefix(12000)))
            if role == "user" { userMessageID = message.id } else { assistantMessageID = message.id }
            snapshot.messages.append(message)
            snapshot.messages = Array(snapshot.messages.suffix(40))
        }
        onChange(snapshot)
    }
    private func history() -> [[String: String]] {
        snapshot.messages.filter { !$0.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
            .suffix(6).map { ["role": $0.role, "text": String($0.text.prefix(500))] }
    }

    private func activate(epoch: Int) throws {
        guard snapshot.status == .connecting else { return }
        setupTimeout?.cancel(); setupTimeout = nil
        if !initialHistory.isEmpty {
            enqueue(["clientContent": ["turns": initialHistory.map { ["role": $0["role"] == "assistant" ? "model" : "user", "parts": [["text": $0["text"] ?? ""]]] }, "turnComplete": false]])
        }
        try audio.start(onPCM: { [weak self] pcm in
            guard let self, self.generation == epoch, self.snapshot.connected else { return }
            self.enqueue(["realtimeInput": ["audio": ["data": pcm.base64EncodedString(), "mimeType": "audio/pcm;rate=16000"]]])
        }, onDrained: { [weak self] in
            guard let self, epoch == self.generation else { return }
            self.audioPlaying = false
            if self.awaitingTurn && self.snapshot.status == .speaking {
                self.setStatus(.listening, "Đang nghe. Bạn có thể nói tiếp hoặc nói chen để ngắt lời.")
            }
            self.syncLocationContext()
        }, onFailure: { [weak self] in
            guard let self, epoch == self.generation else { return }
            self.fail(LiveFailure(message: "Âm thanh bị gián đoạn. Bấm micro để kết nối lại."))
        })
        setStatus(.listening, "Đang nghe. Bạn cứ nói tự nhiên nhé.")
        syncLocationContext()
        videoTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 1_100_000_000)
                guard !Task.isCancelled, let self, epoch == self.generation else { return }
                self.syncLocationContext()
                if let frame = self.getFrame(), frame.hasPrefix("data:image/jpeg;base64,"), frame.utf8.count <= 500000 {
                    self.enqueue(["realtimeInput": ["video": ["data": String(frame.dropFirst(23)), "mimeType": "image/jpeg"]]], droppable: true)
                }
            }
        }
        limitTask = Task { [weak self] in
            guard let seconds = self?.maxSeconds else { return }
            try? await Task.sleep(nanoseconds: UInt64(seconds) * 1_000_000_000)
            guard !Task.isCancelled, let self, epoch == self.generation else { return }
            self.stop(); self.setStatus(.idle, "Đã hết phiên Live. Bấm micro để tiếp tục trò chuyện.")
        }
    }

    private func receive(_ value: [String: Any], epoch: Int) throws {
        if value["error"] != nil { throw LiveFailure.provider(value) }
        if value["setupComplete"] != nil { try activate(epoch: epoch) }
        if let cancelled = value["toolCallCancellation"] as? [String: Any], let ids = cancelled["ids"] as? [String] {
            for id in ids { tools.removeValue(forKey: id)?.cancel() }
        }
        if let content = value["serverContent"] as? [String: Any] {
            let interrupted = content["interrupted"] as? Bool == true
            if interrupted {
                audio.interrupt(); audioPlaying = false; cancelTools(); discardOutput = false
                awaitingTurn = !typedTurnPending
                setStatus(.listening, "Đang nghe bạn nói…")
            }
            if let transcription = content["inputTranscription"] as? [String: Any], let text = transcription["text"] as? String, !text.isEmpty {
                if awaitingTurn { beginTurn() }
                typedTurnPending = false; discardOutput = false
                currentInput = String((currentInput + text).prefix(12000))
                appendTranscript(role: "user", text: text)
            }
            if !discardOutput && !interrupted {
                if let transcription = content["outputTranscription"] as? [String: Any], let text = transcription["text"] as? String {
                    if awaitingTurn { beginTurn() }
                    appendTranscript(role: "assistant", text: text)
                }
                if let model = content["modelTurn"] as? [String: Any], let parts = model["parts"] as? [[String: Any]] {
                    for part in parts {
                        guard let inline = part["inlineData"] as? [String: Any], let mime = inline["mimeType"] as? String,
                              mime.hasPrefix("audio/pcm"), let base64 = inline["data"] as? String,
                              let data = Data(base64Encoded: base64), !data.isEmpty else { continue }
                        let rate = mime.components(separatedBy: "rate=").last.flatMap(Double.init) ?? 24000
                        guard rate == 24000 else { throw LiveFailure(message: "Định dạng âm thanh chưa được hỗ trợ.") }
                        try audio.play(data, rate: rate)
                        audioPlaying = true
                        setStatus(.speaking, "SKI đang nói. Bạn có thể nói chen để hỏi tiếp.")
                    }
                }
            }
            if content["turnComplete"] as? Bool == true && !interrupted && content["interactionStatus"] as? String != "IN_PROGRESS" {
                awaitingTurn = true; typedTurnPending = false; discardOutput = false
                if !audioPlaying { setStatus(.listening, "Đang nghe. Bạn cứ nói tiếp nhé.") }
                syncLocationContext()
            }
        }
        if let calls = value["toolCall"] as? [String: Any], let functions = calls["functionCalls"] as? [[String: Any]] {
            for call in functions { try runTool(call, epoch: epoch) }
        }
        if value["goAway"] != nil { snapshot.detail = "Phiên Live sắp hết hạn. Bạn có thể tắt rồi mở lại micro."; onChange(snapshot) }
    }

    private func runTool(_ call: [String: Any], epoch: Int) throws {
        guard let id = call["id"] as? String, !id.isEmpty, id.count <= 160,
              let name = call["name"] as? String, !name.isEmpty, name.count <= 80,
              let arguments = call["args"] as? [String: Any], !toolSessionID.isEmpty else {
            throw LiveFailure(message: "Yêu cầu xử lý chưa hợp lệ. Bạn thử nói lại nhé.")
        }
        let fingerprint = String(data: try JSONSerialization.data(withJSONObject: ["name": name, "args": arguments], options: .sortedKeys), encoding: .utf8)!
        if tools[id] != nil { return }
        if let (old, response) = completed[id] {
            guard old == fingerprint else { throw LiveFailure(message: "Yêu cầu xử lý bị trùng. Bấm micro để mở lại phiên.") }
            enqueue(["toolResponse": ["functionResponses": [response]]]); return
        }
        if awaitingTurn { beginTurn() }
        toolCount += 1
        guard toolCount <= 12 else { throw LiveFailure(message: "Yêu cầu có quá nhiều thao tác. Bạn thử nói ngắn hơn nhé.") }
        let currentTurn = turn
        setStatus(.thinking, toolLabels[name]?.first ?? "Đang phân tích yêu cầu…")
        var body = getTaskContext()
        body.merge(["session_id": toolSessionID, "call_id": id, "name": name, "arguments": arguments,
                    "history": history(), "request_text": currentInput]) { _, new in new }
        body["location"] = getLocation().map { $0 as Any } ?? NSNull()
        tools[id] = Task { [weak self] in
            guard let self else { return }
            defer { if epoch == self.generation && currentTurn == self.turn { self.tools.removeValue(forKey: id) } }
            do {
                let data = try await self.request("/api/live/tool", body: body, timeout: 95)
                guard epoch == self.generation, currentTurn == self.turn, !Task.isCancelled else { return }
                guard var result = data["result"] as? [String: Any] else { throw LiveFailure(message: "Chưa nhận được kết quả xử lý.") }
                if result["camera_decision"] != nil || result["lesson_control"] != nil || result["live_control"] != nil {
                    let execution = await self.executeClientControl?(result) ?? ["status": "unavailable", "error": "Thao tác này chưa hỗ trợ trong màn hình hiện tại. Không báo đã thực hiện."]
                    guard epoch == self.generation, currentTurn == self.turn, !Task.isCancelled else { return }
                    if var decision = result["camera_decision"] as? [String: Any] {
                        var context = decision["context"] as? [String: Any] ?? [:]
                        context["execution"] = execution; decision["context"] = context
                        result["camera_decision"] = decision
                    } else { result["execution"] = execution; if let error = execution["error"] { result["error"] = error } }
                }
                if name == "list_wardrobe", result["items"] != nil, result["error"] == nil {
                    self.onEvent(["type": "wardrobe"])
                }
                for event in data["events"] as? [[String: Any]] ?? [] { self.onEvent(event) }
                let response: [String: Any] = ["id": id, "name": name, "response": result["camera_decision"] ?? result]
                self.completed[id] = (fingerprint, response)
                if self.completed.count > 60 { self.completed.removeValue(forKey: self.completed.keys.first!) }
                self.enqueue(["toolResponse": ["functionResponses": [response]]])
                self.setStatus(.thinking, "SKI đang chuẩn bị trả lời…")
            } catch {
                guard epoch == self.generation, currentTurn == self.turn, !Task.isCancelled else { return }
                let message = (error as? LiveFailure)?.message ?? "Chưa thực hiện được thao tác. Bạn thử lại nhé."
                self.enqueue(["toolResponse": ["functionResponses": [["id": id, "name": name, "response": ["error": message]]]]])
                self.setStatus(.thinking, message)
            }
        }
    }

    /// Update application context without starting an assistant turn or showing a fake user message.
    func syncLocationContext() {
        // clientContent updates can interrupt generation; wait until the current reply drains.
        guard snapshot.connected, awaitingTurn, !audioPlaying, snapshot.status == .listening else { return }
        let location = getLocation()
        var context: [String: Any] = ["gps_available": location != nil]
        if let location {
            context["coordinates"] = location
            if let city = getCity(), !city.isEmpty { context["city"] = city }
        }
        guard let data = try? JSONSerialization.data(withJSONObject: context, options: .sortedKeys),
              let value = String(data: data, encoding: .utf8), value != lastLocationContext else { return }
        lastLocationContext = value
        let text = "[SKI_APP_CONTEXT] Vị trí hiện tại từ ứng dụng: " + value
            + ". Khi gps_available=true, đã có vị trí GPS: không hỏi lại người dùng đang ở đâu; dùng get_weather để lấy thời tiết thật nếu cần. Nếu chưa có tên thành phố, không tự đoán tên từ tọa độ. Địa điểm, ngày hoặc mùa người dùng yêu cầu luôn được ưu tiên hơn vị trí và thời tiết hiện tại. Đây là cập nhật ngữ cảnh, không cần nói lời xác nhận."
        enqueue(["clientContent": ["turns": [["role": "user", "parts": [["text": text]]]], "turnComplete": false]])
    }

    private func request(_ path: String, body: [String: Any], timeout: TimeInterval) async throws -> [String: Any] {
        guard let origin, let url = URL(string: path, relativeTo: origin)?.absoluteURL else {
            throw LiveFailure(message: "Chưa cấu hình địa chỉ máy chủ.")
        }
        var request = URLRequest(url: url, timeoutInterval: timeout)
        request.httpMethod = "POST"; request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        for (key, value) in headers { request.setValue(value, forHTTPHeaderField: key) }
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (data, response) = try await http.data(for: request)
        guard let response = response as? HTTPURLResponse, data.count <= 2_000_000 else { throw LiveFailure.provider([:]) }
        let value = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] ?? [:]
        guard (200..<300).contains(response.statusCode) else { throw LiveFailure.provider(value, status: response.statusCode) }
        return value
    }

    private func enqueue(_ value: [String: Any], droppable: Bool = false) {
        guard let connection = socket,
              let data = try? JSONSerialization.data(withJSONObject: value), let text = String(data: data, encoding: .utf8) else { return }
        if pendingBytes + data.count > 512000 {
            if !droppable { fail(LiveFailure(message: "Mạng đang chậm. Live đã dừng; bạn thử kết nối lại nhé.")) }
            return
        }
        outbound.append(text); pendingBytes += data.count
        guard sendTask == nil else { return }
        let epoch = generation
        sendTask = Task { [weak self] in
            do {
                while let self, epoch == self.generation, !Task.isCancelled, !self.outbound.isEmpty {
                    let next = self.outbound.removeFirst()
                    try await connection.send(next)
                    guard epoch == self.generation else { return }
                    self.pendingBytes -= next.utf8.count
                }
                if let self, epoch == self.generation { self.sendTask = nil }
            } catch {
                if let self, epoch == self.generation, !Task.isCancelled { self.fail(error) }
            }
        }
    }
}
