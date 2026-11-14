import Foundation

final class LiveHTTPFixture: URLProtocol {
    static let lock = NSLock()
    static var requests: [(String, [String: Any], String?)] = []
    static var delay: TimeInterval = 0
    static var sessionError = false
    static var duration = 300
    private var work: DispatchWorkItem?
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        var bytes = request.httpBody ?? Data()
        if let stream = request.httpBodyStream {
            stream.open(); defer { stream.close() }
            var buffer = [UInt8](repeating: 0, count: 4096)
            while stream.hasBytesAvailable { let count = stream.read(&buffer, maxLength: buffer.count); if count <= 0 { break }; bytes.append(contentsOf: buffer.prefix(count)) }
        }
        let body = (try? JSONSerialization.jsonObject(with: bytes)) as? [String: Any] ?? [:]
        let path = request.url!.path
        Self.lock.lock()
        Self.requests.append((path, body, request.value(forHTTPHeaderField: "X-Shadow-Access")))
        let delay = Self.delay, failure = Self.sessionError, duration = Self.duration
        Self.lock.unlock()
        let value: [String: Any]
        let status: Int
        if path == "/api/live/session" {
            status = failure ? 403 : 200
            value = failure ? ["detail": ["code": "provider_auth_failed", "message": "DO_NOT_DISPLAY_SECRET_TOKEN"]] :
                ["token": "ephemeral-test-only", "socketUrl": "wss://generativelanguage.googleapis.com/ws/test", "model": "test-live", "toolSessionId": "6e15d86b-3311-4cc5-8d0b-d83a63261507", "maxSeconds": duration]
        } else {
            status = 200
            value = ["result": ["available": true, "temperature": 27], "events": [["type": "outfits", "outfits": []]]]
        }
        let work = DispatchWorkItem { [weak self] in
            guard let self, self.work?.isCancelled != true else { return }
            self.client?.urlProtocol(self, didReceive: HTTPURLResponse(url: self.request.url!, statusCode: status, httpVersion: nil, headerFields: ["Content-Type": "application/json"])!, cacheStoragePolicy: .notAllowed)
            self.client?.urlProtocol(self, didLoad: try! JSONSerialization.data(withJSONObject: value))
            self.client?.urlProtocolDidFinishLoading(self)
        }
        self.work = work
        DispatchQueue.global().asyncAfter(deadline: .now() + delay, execute: work)
    }
    override func stopLoading() { work?.cancel() }
    static func captured() -> [(String, [String: Any], String?)] { lock.lock(); defer { lock.unlock() }; return requests }
}

@MainActor final class TestLiveAudio: LiveAudioStreaming {
    var recording = false
    var interrupted = 0
    var played = 0
    var pcm: ((Data) -> Void)?
    var drained: (() -> Void)?
    func start(onPCM: @escaping (Data) -> Void, onDrained: @escaping () -> Void, onFailure: @escaping () -> Void) throws { recording = true; pcm = onPCM; drained = onDrained }
    func play(_ pcm: Data, rate: Double) throws { played += 1 }
    func interrupt() { interrupted += 1 }
    func stop() { recording = false }
}

@MainActor final class TestLiveSocket: LiveSocket {
    var sent: [[String: Any]] = []
    var queue: [Data] = []
    var pending: CheckedContinuation<Data, Error>?
    var closed = false
    func send(_ text: String) async throws {
        if closed { throw CancellationError() }
        sent.append(try JSONSerialization.jsonObject(with: Data(text.utf8)) as! [String: Any])
    }
    func receive() async throws -> Data {
        if closed { throw CancellationError() }
        if !queue.isEmpty { return queue.removeFirst() }
        return try await withCheckedThrowingContinuation { pending = $0 }
    }
    func close() { closed = true; pending?.resume(throwing: CancellationError()); pending = nil }
    func deliver(_ value: [String: Any]) {
        guard !closed else { return }
        let bytes = try! JSONSerialization.data(withJSONObject: value)
        if let waiting = pending { pending = nil; waiting.resume(returning: bytes) } else { queue.append(bytes) }
    }
}

@main struct NativeLiveChecks {
    @MainActor static func wait(_ label: String, _ predicate: () -> Bool) async {
        for _ in 0..<200 { if predicate() { return }; try? await Task.sleep(nanoseconds: 10_000_000) }
        fatalError("Timeout: \(label)")
    }
    static func clientText(_ value: [String: Any]) -> String {
        guard let content = value["clientContent"] as? [String: Any], let turns = content["turns"] as? [[String: Any]] else { return "" }
        return turns.flatMap { $0["parts"] as? [[String: Any]] ?? [] }.compactMap { $0["text"] as? String }.joined()
    }
    static func require(_ value: @autoclosure () -> Bool, _ message: String) { if !value() { fatalError(message) } }
    @MainActor static func main() async {
        for code in ["quota", "chat_quota"] {
            let error = LiveFailure.provider(["code": code, "detail": "DO_NOT_DISPLAY_PROVIDER_DETAILS"], status: 429)
            require(error.message.contains("giới hạn mở phiên"), "Local session quota must be distinct from temporary service load")
            require(!error.message.contains("DO_NOT_DISPLAY"), "Quota errors must not expose upstream details")
        }
        let depleted = "Your prepayment credits are depleted. DO_NOT_DISPLAY_SECRET_TOKEN"
        require(LiveFailure.connectionClosed(Data(depleted.utf8)).message.contains("nạp tín dụng"), "WebSocket billing failure explains why voice cannot start")
        require(LiveFailure.provider(["error": ["code": 429, "message": depleted]]).message.contains("nạp tín dụng"), "In-band billing failures use the same safe message")
        require(!LiveFailure.connectionClosed(Data("DO_NOT_DISPLAY_SECRET_TOKEN".utf8)).message.contains("DO_NOT_DISPLAY"), "Unknown socket reasons stay private")
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [LiveHTTPFixture.self]
        let http = URLSession(configuration: config)
        let audio = TestLiveAudio()
        var sockets: [TestLiveSocket] = []
        let live = NativeLiveSession(audio: audio, http: http) { url in
            require(URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first?.name == "access_token", "Only an ephemeral access token goes to the socket")
            let socket = TestLiveSocket(); sockets.append(socket); return socket
        }
        let origin = URL(string: "https://ski.example.test")!
        var gps: [String: Double]? = ["lat": 10.8, "lon": 106.6]
        live.getLocation = { gps }
        var city: String? = "Hà Nội"
        live.getCity = { city }
        live.getFrame = { "data:image/jpeg;base64,FRAME_FIXTURE" }
        var events = 0
        live.onEvent = { _ in events += 1 }
        await live.start(origin: origin, headers: ["X-Shadow-Access": "demo-access"])
        let socket = sockets.last!
        await wait("setup sent") { socket.sent.first?["setup"] != nil }
        require(!audio.recording, "Mic must not send before setupComplete")
        socket.deliver(["setupComplete": [:]])
        await wait("microphone starts") { audio.recording && live.snapshot.connected }
        require(LiveHTTPFixture.captured().first?.1["configured_city"] as? String == "Hà Nội", "Initial Live setup includes the GPS city")
        await wait("GPS context reaches Live") { socket.sent.contains { clientText($0).contains("Hà Nội") } }
        let contextMessages = socket.sent.filter { $0["clientContent"] != nil }
        require((contextMessages.last?["clientContent"] as? [String: Any])?["turnComplete"] as? Bool == false, "GPS updates must not start a spoken turn")
        require(live.snapshot.messages.isEmpty, "GPS context must not appear as a user transcript")
        live.syncLocationContext()
        try? await Task.sleep(nanoseconds: 30_000_000)
        require(socket.sent.filter { $0["clientContent"] != nil }.count == contextMessages.count, "Unchanged GPS must not flood the conversation")
        city = "Đà Nẵng"; live.syncLocationContext()
        await wait("GPS city update") { socket.sent.contains { clientText($0).contains("Đà Nẵng") } }
        require(LiveHTTPFixture.captured().first?.2 == "demo-access", "BE requests keep the existing access header")
        audio.pcm?(Data(repeating: 0, count: 3200))
        await wait("PCM packet") { socket.sent.contains { ($0["realtimeInput"] as? [String: Any])?["audio"] != nil } }
        socket.deliver(["serverContent": ["inputTranscription": ["text": "Chào "]]])
        socket.deliver(["serverContent": ["inputTranscription": ["text": "bạn"]]])
        socket.deliver(["serverContent": ["outputTranscription": ["text": "Chào bạn!"], "modelTurn": ["parts": [["inlineData": ["mimeType": "audio/pcm;rate=24000", "data": "AAAAAA=="]]]]]])
        await wait("streamed playback") { audio.played == 1 }
        require(audio.recording, "Mic remains active during assistant audio for barge-in")
        require(live.snapshot.messages.first?.text == "Chào bạn", "Partial user transcript must merge")
        let beforeLocationUpdate = socket.sent.filter { $0["clientContent"] != nil }.count
        city = "Huế"; live.syncLocationContext()
        try? await Task.sleep(nanoseconds: 30_000_000)
        require(socket.sent.filter { $0["clientContent"] != nil }.count == beforeLocationUpdate, "GPS must not interrupt a spoken reply")
        socket.deliver(["serverContent": ["turnComplete": true]])
        await wait("turn drained") { live.snapshot.messages.count == 2 }
        try? await Task.sleep(nanoseconds: 30_000_000)
        require(socket.sent.filter { $0["clientContent"] != nil }.count == beforeLocationUpdate, "GPS waits for buffered audio to drain too")
        audio.drained?()
        await wait("deferred GPS update") { socket.sent.contains { clientText($0).contains("Huế") } }
        await wait("listening after turn") { live.snapshot.status == .listening }
        require(audio.recording, "Conversation must not stop after one sentence")
        await wait("camera frame streamed") { socket.sent.contains { ($0["realtimeInput"] as? [String: Any])?["video"] != nil } }
        socket.deliver(["serverContent": ["inputTranscription": ["text": "Ngày mai thì sao?"]]])
        socket.deliver(["serverContent": ["outputTranscription": ["text": "Để mình xem…"], "modelTurn": ["parts": [["inlineData": ["mimeType": "audio/pcm;rate=24000", "data": "AAAAAA=="]]]]]])
        await wait("second voice turn") { audio.played == 2 }
        let interruptions = audio.interrupted
        socket.deliver(["serverContent": ["interrupted": true, "inputTranscription": ["text": "Phối đồ mùa hè nhé"]]])
        await wait("barge-in") { audio.interrupted > interruptions }
        require(live.snapshot.messages.last?.text == "Phối đồ mùa hè nhé", "Interruption starts a separate user turn")
        let call: [String: Any] = ["id": "weather-1", "name": "get_weather", "args": [:]]
        socket.deliver(["toolCall": ["functionCalls": [call]]])
        await wait("weather response") { socket.sent.contains { $0["toolResponse"] != nil } }
        let request = LiveHTTPFixture.captured().last!.1
        require((request["location"] as? [String: Double])?["lat"] == 10.8, "Tool gets current GPS")
        require(request["request_text"] as? String == "Phối đồ mùa hè nhé", "Requested season reaches server instead of forced current weather")
        require(events == 1, "Native UI receives tool result events")
        let requests = LiveHTTPFixture.captured().count
        socket.deliver(["toolCall": ["functionCalls": [call]]])
        await wait("cached response") { socket.sent.filter { $0["toolResponse"] != nil }.count == 2 }
        require(LiveHTTPFixture.captured().count == requests && events == 1, "Duplicate tool must not repeat server or UI side effects")
        gps = nil
        socket.deliver(["serverContent": ["turnComplete": true]])
        await wait("ready to update revoked GPS") { live.snapshot.status == .listening }
        live.syncLocationContext()
        await wait("GPS context revoked") {
            guard let content = socket.sent.last?["clientContent"] as? [String: Any],
                  let turns = content["turns"] as? [[String: Any]],
                  let text = (turns.first?["parts"] as? [[String: Any]])?.first?["text"] as? String else { return false }
            return text.contains("\"gps_available\":false") && !text.contains("Huế")
        }
        socket.deliver(["toolCall": ["functionCalls": [["id": "weather-2", "name": "get_weather", "args": [:]]]]])
        await wait("GPS revoked") { LiveHTTPFixture.captured().count > requests }
        require(LiveHTTPFixture.captured().last!.1["location"] is NSNull, "Revoked GPS clears server context")
        require(live.sendText("Đổi sang trời lạnh"), "Typed follow-up uses the same live socket")
        socket.deliver(["serverContent": ["interrupted": true]])
        socket.deliver(["serverContent": ["outputTranscription": ["text": "Mình sẽ chọn áo ấm."]]])
        await wait("typed follow-up") { live.snapshot.messages.last?.text == "Mình sẽ chọn áo ấm." }
        require(live.snapshot.messages.dropLast().last?.text == "Đổi sang trời lạnh", "Server interruption must preserve typed turn")
        let stalePCM = audio.pcm
        live.stop()
        let before = socket.sent.count
        stalePCM?(Data(repeating: 0, count: 3200))
        require(!audio.recording && socket.closed && live.snapshot.status == .idle, "Stop releases microphone and socket")
        try? await Task.sleep(nanoseconds: 50_000_000)
        require(socket.sent.count == before, "Late capture callback must not send after stop")

        LiveHTTPFixture.delay = 0.25
        let pendingStart = Task { await live.start(origin: origin, headers: [:]) }
        await wait("delayed token request") { live.snapshot.status == .connecting }
        live.stop(); await pendingStart.value
        require(sockets.count == 1 && !audio.recording, "Closing during token fetch must not reopen microphone")
        LiveHTTPFixture.delay = 0; LiveHTTPFixture.sessionError = true
        await live.start(origin: origin, headers: [:])
        require(live.snapshot.status == .failed, "Auth failure is visible and retryable by the user")
        require(!(live.snapshot.error ?? "").contains("DO_NOT_DISPLAY_SECRET_TOKEN"), "Raw provider errors must not leak")
        LiveHTTPFixture.sessionError = false; LiveHTTPFixture.duration = 1
        await live.start(origin: origin, headers: [:])
        let last = sockets.last!; last.deliver(["setupComplete": [:]])
        await wait("reconnect") { live.snapshot.connected }
        await wait("server session time limit") { live.snapshot.status == .idle }
        require(!audio.recording && last.closed, "Session expiry releases resources without a paid reconnect loop")
        print("Native Live checks PASS: duplex audio, video frames, partial transcripts, interruption, typed follow-up, tools/GPS, deduplication, cancellation, safe errors and expiry.")
        http.invalidateAndCancel()
    }
}
