import Foundation

@main
struct NativeLessonChecks {
    @MainActor static func main() async {
        let compiled: [String: Any] = ["compiled_by": "ai", "draft_id": "draft1", "skill": ["display_name": "Gấp áo", "steps": [["id": "step1", "instruction": "Gấp tay áo", "reference_image_url": "/skills-media/_drafts/draft1/frames/a.jpg"]]]]
        for saveImmediately in [false, true] {
            let lesson = NativeLessonSession()
            var saved: [[String: Any]] = []
            lesson.capture = { "data:image/jpeg;base64,frame" }
            lesson.request = { method, _, body in
                if method == "POST" {
                    let lines = body["transcript"] as! [[String: Any]]
                    precondition(lines.count == 1 && lines[0]["text"] as? String == "Gấp tay áo vào trong.")
                    precondition(!(body["frames"] as! [[String: Any]]).isEmpty)
                    return compiled
                }
                saved.append(body); return body
            }
            _ = lesson.control(["command": "start_teaching"], messages: [])
            var line = LiveTranscript(role: "user", text: "Gấp tay áo")
            lesson.observe([line]); line.text = "Gấp tay áo vào trong."; lesson.observe([line])
            _ = lesson.control(["command": saveImmediately ? "finish_and_save" : "finish_teaching"], messages: [line])
            await lesson.pending?.value
            if !saveImmediately {
                precondition(saved.isEmpty && lesson.status == "review")
                let stale = lesson.control(["command": "save", "revision": lesson.revision - 1], messages: [])
                precondition(stale["error"] != nil)
                _ = lesson.control(["command": "save", "revision": lesson.revision], messages: [])
                await lesson.pending?.value
            }
            precondition(saved.count == 1 && lesson.status == "saved")
            let id = saved[0]["id"] as! String
            let step = (saved[0]["steps"] as! [[String: Any]])[0]
            precondition(step["reference_image_url"] as? String == "/skills-media/\(id)/frames/a.jpg")
            precondition(saved[0]["draft_id"] as? String == "draft1")
            lesson.reset()
        }
        for cancel in [false, true] {
            let lesson = NativeLessonSession()
            var gate: CheckedContinuation<[String: Any], Error>?
            var saves = 0
            lesson.capture = { "data:image/jpeg;base64,frame" }
            lesson.request = { method, _, _ in
                if method == "POST" { return try await withCheckedThrowingContinuation { gate = $0 } }
                saves += 1; return [:]
            }
            _ = lesson.control(["command": "start_teaching"], messages: [])
            lesson.observe([LiveTranscript(role: "user", text: "Rót nước chậm.")])
            _ = lesson.control(["command": "finish_teaching"], messages: [])
            for _ in 0..<1000 where gate == nil { try? await Task.sleep(nanoseconds: 1_000_000) }
            precondition(gate != nil, "Compilation did not start")
            _ = lesson.control(["command": "save"], messages: [])
            let pending = lesson.pending
            if cancel { lesson.reset() }
            gate?.resume(returning: compiled)
            await pending?.value
            precondition(saves == (cancel ? 0 : 1))
            lesson.reset()
        }
        let lesson = NativeLessonSession()
        var attempts = 0, saves = 0
        lesson.capture = { "data:image/jpeg;base64,frame" }
        lesson.request = { method, _, body in
            if method == "POST" {
                attempts += 1
                precondition((body["transcript"] as! [[String: Any]]).count == 1)
                if attempts == 1 { throw NSError(domain: "offline", code: 1) }
                return compiled
            }
            saves += 1; return [:]
        }
        _ = lesson.control(["command": "start_teaching"], messages: [])
        lesson.observe([LiveTranscript(role: "user", text: "Gấp áo.")])
        _ = lesson.control(["command": "finish_and_save"], messages: []); await lesson.pending?.value
        precondition(lesson.status == "error" && saves == 0)
        _ = lesson.control(["command": "save"], messages: []); await lesson.pending?.value
        precondition(lesson.status == "saved" && saves == 1 && attempts == 2)
        lesson.reset()
        let paused = NativeLessonSession()
        paused.capture = { "data:image/jpeg;base64,frame" }
        paused.request = { method, _, body in
            if method == "POST" {
                precondition((body["transcript"] as! [[String: Any]]).count == 2)
                return compiled
            }
            return [:]
        }
        _ = paused.control(["command": "start_teaching"], messages: [])
        let first = LiveTranscript(role: "user", text: "Trải áo lên bàn.")
        paused.observe([first]); paused.suspend()
        _ = paused.control(["command": "start_teaching"], messages: [first])
        paused.observe([first, LiveTranscript(role: "user", text: "Gấp áo làm đôi.")])
        _ = paused.control(["command": "finish_and_save"], messages: [])
        await paused.pending?.value
        precondition(paused.status == "saved")
        paused.reset()
        print("Native lesson recording, review, single-command saving, media promotion, cancellation and retry: PASS")
    }
}
