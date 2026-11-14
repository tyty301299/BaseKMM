import Foundation

/// Records the existing Live transcript/camera and uses the same compile/save APIs as the web.
@MainActor
final class NativeLessonSession {
    var capture: () -> String? = { nil }
    var request: (String, String, [String: Any]) async throws -> [String: Any] = { _, _, _ in throw LessonFailure() }
    var onChange: () -> Void = {}
    var notify: ([String: Any]) -> Void = { _ in }
    private(set) var status = "idle"
    private(set) var title = ""
    private(set) var message = ""
    private(set) var revision = 0
    private(set) var draft: [String: Any]?
    private(set) var pending: Task<Void, Never>?
    private var timer: Task<Void, Never>?
    private var epoch = 0
    private var started = Date()
    private var ignored: [UUID] = []
    private var lines: [(UUID, [String: Any])] = []
    private var frames: [[String: Any]] = []
    private var draftID = ""
    private var savedID = ""
    private var saveAfterCompile = false
    private struct LessonFailure: Error {}

    var hasWork: Bool { !["idle", "saved"].contains(status) }
    var mode: String { status == "compiling" ? "compiling" : draft != nil ? "reviewing" : hasWork ? "teaching" : "chat" }
    var steps: [[String: Any]] { draft?["steps"] as? [[String: Any]] ?? [] }
    var snapshot: [String: Any] {
        ["status": status, "title": String(title.prefix(160)), "revision": revision, "step": 0,
         "steps": Array(steps.prefix(30).enumerated()).map { index, step in
            ["number": index + 1, "instruction": String((step["instruction"] as? String ?? "").prefix(800)),
             "tip": String((step["tip"] as? String ?? "").prefix(400)), "warning": String((step["warning"] as? String ?? "").prefix(400))] as [String: Any]
         }]
    }
    var execution: [String: Any] {
        var value: [String: Any] = ["mode": mode, "status": status, "message": message, "lesson_state": snapshot]
        if status == "saved" { value["saved_skill"] = ["id": savedID, "name": title] }
        return value
    }

    func observe(_ messages: [LiveTranscript]) {
        guard status == "recording" else { return }
        for line in messages where line.role == "user" && !ignored.contains(line.id) {
            if let index = lines.firstIndex(where: { $0.0 == line.id }) {
                lines[index].1["text"] = String(line.text.prefix(2000))
                lines[index].1["t1"] = Date().timeIntervalSince(started)
            } else if lines.count < 600 {
                let time = Date().timeIntervalSince(started)
                lines.append((line.id, ["t0": lines.last?.1["t1"] as? Double ?? 0, "t1": time, "text": String(line.text.prefix(2000))]))
                captureFrame()
            }
        }
    }

    func control(_ control: [String: Any], messages: [LiveTranscript]) -> [String: Any] {
        let command = control["command"] as? String ?? ""
        if command == "stop" { reset(); return execution }
        if command == "start_teaching" {
            if status == "recording" { return execution }
            if ["paused", "error"].contains(status), draft == nil {
                guard capture() != nil else { return error("Bật camera để tiếp tục bài dạy nhé.") }
                ignored.append(contentsOf: messages.map { $0.id }); ignored = Array(ignored.suffix(640)); status = "recording"
                message = "Đang ghi tiếp bài dạy."; startTimer(); onChange(); return execution
            }
            guard !hasWork else { return error("Bài dạy vẫn còn. Hãy lưu hoặc hủy trước khi ghi bài mới.") }
            guard capture() != nil else { return error("Bật camera để làm mẫu và ghi bài dạy nhé.") }
            reset(); started = Date(); ignored = messages.map { $0.id }
            title = String((control["title"] as? String ?? "Bài dạy qua camera").prefix(160))
            status = "recording"; message = "Đang ghi bài. Khi xong, nói “Dạy xong rồi, lưu lại đi”."
            captureFrame(); startTimer(); onChange(); return execution
        }
        observe(messages)
        if ["finish_teaching", "finish_and_save", "save"].contains(command) && draft == nil {
            if status == "compiling" {
                if command != "finish_teaching" { saveAfterCompile = true; message = "Đã nhận yêu cầu lưu khi biên soạn xong."; onChange() }
                return execution
            }
            guard ["recording", "paused", "error"].contains(status), !lines.isEmpty else { return error("Chưa ghi được lời dạy. Hãy làm mẫu và giải thích các bước trước.") }
            compile(save: command != "finish_teaching"); return execution
        }
        if status == "saving" { return execution }
        guard draft != nil else { return error("Chưa có bản nháp. Hãy dạy rồi yêu cầu xem lại hoặc lưu.") }
        if command == "review" { message = "Bản nháp đã hiện trên camera. Nói “Lưu đi” để lưu."; onChange(); return execution }
        guard control["revision"] as? Int == revision else { return error("Bản nháp đã đổi. Hãy dùng revision hiện tại trong lesson_state.") }
        if command == "rename", let name = control["title"] as? String, !name.isEmpty {
            title = String(name.prefix(160)); draft?["display_name"] = title; revision += 1; onChange(); return execution
        }
        if ["save", "finish_and_save"].contains(command) {
            status = "saving"; onChange()
            pending = Task { [weak self] in await self?.persist() }
            return execution
        }
        return error("Bản nháp vẫn còn. Bạn có thể xem lại, đổi tên hoặc lưu bài tại đây.")
    }

    private func captureFrame() {
        guard let image = capture(), image.hasPrefix("data:image/jpeg;base64,"), image.utf8.count <= 500000 else { return }
        frames.append(["t": Date().timeIntervalSince(started), "image": image, "reason": "live"])
        if frames.count > 48 { frames = frames.enumerated().filter { $0.offset == 0 || $0.offset == frames.count - 1 || $0.offset % 2 == 0 }.map(\.element) }
    }
    private func startTimer() {
        timer?.cancel()
        timer = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 2_000_000_000)
                guard !Task.isCancelled, let self, self.status == "recording" else { return }
                self.captureFrame()
            }
        }
    }
    private func compile(save: Bool) {
        captureFrame(); timer?.cancel(); timer = nil; saveAfterCompile = save
        status = "compiling"; message = "Đang biên soạn bài dạy…"; onChange()
        let generation = epoch
        let body: [String: Any] = ["title": title, "teacher_name": "", "transcript": lines.map(\.1), "frames": frames, "notes": ""]
        pending = Task { [weak self] in
            guard let self else { return }
            do {
                let result = try await self.request("POST", "/api/teach/compile", body)
                guard self.epoch == generation, !Task.isCancelled else { return }
                guard result["compiled_by"] as? String != "mock", let skill = result["skill"] as? [String: Any],
                      let steps = skill["steps"] as? [[String: Any]], !steps.isEmpty, steps.count <= 30,
                      let id = result["draft_id"] as? String, !id.isEmpty else { throw LessonFailure() }
                self.draft = skill; self.draftID = id; self.savedID = "camera-" + UUID().uuidString.replacingOccurrences(of: "-", with: "").lowercased().prefix(16)
                self.title = String((skill["display_name"] as? String ?? self.title).prefix(160))
                self.revision += 1; self.status = "review"; self.message = "Bản nháp đã sẵn sàng. Nói “Lưu đi” để lưu."; self.onChange()
                if self.saveAfterCompile { self.saveAfterCompile = false; await self.persist() }
                else { self.notify(self.execution) }
            } catch {
                guard self.epoch == generation, !Task.isCancelled else { return }
                self.status = "error"; self.saveAfterCompile = false
                self.message = "Chưa biên soạn được bài. Nội dung đã ghi vẫn còn; nói “Lưu lại” để thử lại."
                self.onChange(); self.notify(self.execution)
            }
        }
    }
    private func persist() async {
        guard var skill = draft else { return }
        let generation = epoch
        skill["id"] = savedID; skill["draft_id"] = draftID
        let payload = rewriteMedia(skill, prefix: "/skills-media/_drafts/\(draftID)/", replacement: "/skills-media/\(savedID)/") as! [String: Any]
        status = "saving"; message = "Đang lưu bài học…"; onChange()
        do {
            _ = try await request("PUT", "/api/skills/\(savedID)", payload)
            guard epoch == generation, !Task.isCancelled else { return }
            draft = nil; frames = []; lines = []; revision += 1; status = "saved"
            message = "Đã lưu bài \(title). Bạn có thể nhờ SKI hướng dẫn ngay."
        } catch {
            guard epoch == generation, !Task.isCancelled else { return }
            status = "review"; message = "Chưa lưu được bài. Bản nháp vẫn còn; nói “Lưu lại” để thử lại."
        }
        onChange(); notify(execution)
    }
    private func rewriteMedia(_ value: Any, prefix: String, replacement: String) -> Any {
        if let text = value as? String, text.hasPrefix(prefix) { return replacement + text.dropFirst(prefix.count) }
        if let list = value as? [Any] { return list.map { rewriteMedia($0, prefix: prefix, replacement: replacement) } }
        if let object = value as? [String: Any] { return object.mapValues { rewriteMedia($0, prefix: prefix, replacement: replacement) } }
        return value
    }
    private func error(_ text: String) -> [String: Any] { var value = execution; value["error"] = text; return value }
    func suspend() {
        epoch += 1; pending?.cancel(); pending = nil; timer?.cancel(); timer = nil; saveAfterCompile = false
        if hasWork {
            status = draft == nil ? "paused" : "review"
            message = "Bài dạy đang tạm dừng. Nội dung vẫn còn; bạn có thể nói lưu hoặc hủy."
            onChange()
        }
    }
    func reset() {
        suspend(); draft = nil; lines = []; frames = []; ignored = []; draftID = ""; savedID = ""
        revision += 1; status = "idle"; title = ""; message = "Đã dừng bài học."; onChange()
    }
}
