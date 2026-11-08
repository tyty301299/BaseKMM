import json

from google.genai import types

# Acceptance criteria stay on the server so they can be tuned without shipping a new app.
CRITERIA = {
    "cups": "Tất cả 3 cái cốc đã nằm ở nửa bên phải mặt bàn. Không còn cốc rời ở bên trái hoặc giữa bàn.",
    "bowls": "3 cái bát đã xếp chồng lên nhau thành một chồng duy nhất.",
    "spoons": "Tất cả thìa đã nằm trong khay. Không còn thìa rời trên mặt bàn.",
}

UNCLEAR_MESSAGE = "Em chưa nhìn rõ, anh cho em xem lại nhé."
STATUSES = ("DONE", "NOT_DONE", "UNCLEAR")

# What the guide box marks on the photo for each step.
TARGETS = {
    "cups": "vùng trống ở nửa bên phải mặt bàn, đủ chỗ đặt 3 cái cốc",
    "bowls": "vị trí của cái bát thấp nhất để xếp chồng, nếu chưa có thì vùng trống giữa bàn",
    "spoons": "cái khay đựng thìa",
}

RESULT_SCHEMA = {
    "type": "object",
    "properties": {
        "status": {"type": "string", "enum": list(STATUSES)},
        "message": {"type": "string"},
        "misplaced": {"type": "array", "items": {"type": "array", "items": {"type": "integer"}}},
    },
    "required": ["status", "message"],
}

OBJECT_LABELS = ("cốc", "bát", "thìa", "khay")

SCAN_SCHEMA = {
    "type": "object",
    "properties": {
        "objects": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "label": {"type": "string", "enum": list(OBJECT_LABELS)},
                    "box_2d": {"type": "array", "items": {"type": "integer"}},
                },
                "required": ["label", "box_2d"],
            },
        },
        "target_found": {"type": "boolean"},
        "target_box_2d": {"type": "array", "items": {"type": "integer"}},
    },
    "required": ["objects", "target_found"],
}

VERIFY_CONFIG = {
    "response_mime_type": "application/json",
    "response_json_schema": RESULT_SCHEMA,
}

SCAN_CONFIG = {
    "response_mime_type": "application/json",
    "response_json_schema": SCAN_SCHEMA,
}


def build_prompt(criterion):
    return "\n".join([
        "Bạn kiểm tra ảnh chụp mặt bàn bếp cho một trợ lý hướng dẫn dọn dẹp.",
        f"Điều kiện hoàn thành: {criterion}",
        "status = DONE chỉ khi nhìn thấy rõ điều kiện đã đạt.",
        "status = NOT_DONE khi thấy rõ điều kiện chưa đạt. message phải nói cụ thể món đồ nào còn sai, đang ở đâu và cần làm gì.",
        "status = UNCLEAR khi ảnh mờ, tối, bị che hoặc không thấy mặt bàn. Không đoán.",
        'message là một câu tiếng Việt ngắn, xưng "em", gọi người dùng là "anh".',
        "misplaced: khi NOT_DONE, liệt kê khung của từng món đang để sai, mỗi khung là [ymin, xmin, ymax, xmax] theo thang 0-1000. Trường hợp khác để mảng rỗng.",
    ])


def build_scan_prompt(target):
    return "\n".join([
        "Đây là ảnh mặt bàn bếp. Mọi khung là [ymin, xmin, ymax, xmax] theo thang 0-1000.",
        f"objects: liệt kê từng món riêng lẻ nhìn thấy rõ thuộc các loại {', '.join(OBJECT_LABELS)}, mỗi món một khung. Không đoán món không thấy.",
        f"target: tìm {target}. Nếu tìm được thì target_found = true và target_box_2d là khung của nó, nếu không thì target_found = false.",
    ])


def to_zone(box):
    """Converts Gemini's [ymin, xmin, ymax, xmax] (0-1000) to [left, top, right, bottom] (0-1)."""
    if not isinstance(box, list) or len(box) != 4:
        return None
    if not all(isinstance(value, (int, float)) and not isinstance(value, bool) for value in box):
        return None
    ymin, xmin, ymax, xmax = (min(max(value, 0), 1000) / 1000 for value in box)
    if xmin >= xmax or ymin >= ymax:
        return None
    return [xmin, ymin, xmax, ymax]


def parse_result(text):
    """Malformed model output is treated the same as an unclear photo."""
    try:
        data = json.loads(text)
    except (TypeError, ValueError):
        data = None
    if isinstance(data, dict):
        status, message = data.get("status"), data.get("message")
        if status in STATUSES and isinstance(message, str) and message:
            boxes = data.get("misplaced") if status == "NOT_DONE" else None
            zones = [to_zone(box) for box in boxes] if isinstance(boxes, list) else []
            return {"status": status, "message": message, "misplaced": [zone for zone in zones if zone]}
    return {"status": "UNCLEAR", "message": UNCLEAR_MESSAGE, "misplaced": []}


def parse_scan(text):
    """Bad model output means nothing was detected, so the app keeps its current boxes."""
    try:
        data = json.loads(text)
    except (TypeError, ValueError):
        data = None
    if not isinstance(data, dict):
        return {"target": None, "objects": []}
    objects = []
    items = data.get("objects")
    for item in items if isinstance(items, list) else []:
        if isinstance(item, dict) and item.get("label") in OBJECT_LABELS:
            box = to_zone(item.get("box_2d"))
            if box:
                objects.append({"label": item["label"], "box": box})
    target = to_zone(data.get("target_box_2d")) if data.get("target_found") is True else None
    return {"target": target, "objects": objects}


async def verify_step(ai, model, criterion, image):
    response = await ai.aio.models.generate_content(
        model=model,
        contents=[types.Part.from_bytes(data=image, mime_type="image/jpeg"), build_prompt(criterion)],
        config=VERIFY_CONFIG,
    )
    return parse_result(response.text)


async def scan_scene(ai, model, target, image):
    response = await ai.aio.models.generate_content(
        model=model,
        contents=[types.Part.from_bytes(data=image, mime_type="image/jpeg"), build_scan_prompt(target)],
        config=SCAN_CONFIG,
    )
    return parse_scan(response.text)


MOCK_SCAN = {
    "target": [0.6, 0.25, 0.9, 0.7],
    "objects": [
        {"label": "cốc (giả lập)", "box": [0.1, 0.3, 0.25, 0.42]},
        {"label": "bát (giả lập)", "box": [0.3, 0.45, 0.5, 0.58]},
        {"label": "thìa (giả lập)", "box": [0.15, 0.65, 0.4, 0.72]},
    ],
}


class MockVerifier:
    """Keyless stand-in: each step fails its first check, so the correction path can be demoed."""

    def __init__(self):
        self._checked = set()

    def __call__(self, client, step_id):
        key = (client, step_id)
        if key in self._checked:
            self._checked.discard(key)
            return {"status": "DONE", "message": "Bước này xong rồi, anh làm tốt lắm.", "misplaced": []}
        self._checked.add(key)
        return {
            "status": "NOT_DONE",
            "message": "Vẫn còn một món ở giữa bàn, anh chuyển giúp em nhé.",
            "misplaced": [[0.35, 0.4, 0.55, 0.55]],
        }
