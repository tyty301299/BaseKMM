#!/usr/bin/env python3
"""Fast guard for shared dependency direction, package paths and Xcode source membership."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "shared/src/commonMain/kotlin/com/example/kmmbase"
errors = []
for source in BASE.rglob("*.kt"):
    text = source.read_text()
    # Strip comments so documentation can describe forbidden dependencies.
    code = re.sub(r"/\*.*?\*/|//[^\n]*", "", text, flags=re.S)
    relative = source.relative_to(BASE)
    expected = "com.example.kmmbase." + str(relative.parent).replace("/", ".")
    package = re.search(r"^package (\S+)", text, re.M)
    if not package or package[1] != expected:
        errors.append(f"{relative}: package does not match folder")
    if relative.parts[0] in ("domain", "presentation"):
        forbidden = ["com.example.kmmbase.data.", "io.ktor.", "io.realm.", "kotlinx.serialization."]
        if relative.parts[0] == "domain":
            forbidden += ["com.example.kmmbase.presentation.", "com.example.kmmbase.di."]
        for dependency in forbidden:
            if dependency in code:
                errors.append(f"{relative}: forbidden dependency {dependency}")

project = (ROOT / "iosApp/iosApp.xcodeproj/project.pbxproj").read_text()
for source in (ROOT / "iosApp/iosApp").rglob("*.swift"):
    path = str(source.relative_to(ROOT / "iosApp"))
    refs = re.findall(r'([0-9A-F]{24}) = \{ isa = PBXFileReference;[^\n]*path = "' + re.escape(path) + r'";', project)
    if len(refs) != 1:
        errors.append(f"{path}: expected exactly one Xcode file reference")
    elif len(re.findall(r"isa = PBXBuildFile; fileRef = " + refs[0] + ";", project)) != 1:
        errors.append(f"{path}: expected exactly one Xcode build file")

if errors:
    print("\n".join(errors))
    sys.exit(1)
print("Architecture checks passed: dependency direction, Kotlin packages, Swift source membership.")
