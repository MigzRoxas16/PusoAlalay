"""Dependency-free source checks. This does NOT compile Kotlin or execute Android."""
from pathlib import Path
from collections import Counter
import hashlib
import json
import re
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
checks = []
def check(name, condition, detail=""):
    checks.append({"check": name, "passed": bool(condition), "detail": detail})

xmls = list(ROOT.rglob("*.xml"))
for p in xmls:
    ET.parse(p)
check("XML well-formedness", True, f"{len(xmls)} files")

def resources(folder):
    elements = ET.parse(ROOT / f"app/src/main/res/{folder}/strings.xml").getroot()
    names = [s.attrib["name"] for s in elements]
    check(f"No duplicate strings: {folder}", len(names) == len(set(names)))
    return {s.attrib["name"]: "".join(s.itertext()) for s in elements}
en, fil = resources("values"), resources("values-b+fil")
check("English / Filipino key parity", set(en) == set(fil), f"{len(en)} keys in each language")
placeholders = lambda s: re.findall(r"%\d+\$[dsf]", s)
check("Translation format placeholders", all(placeholders(en[k]) == placeholders(fil[k]) for k in en))

kotlin_files = list(ROOT.rglob("*.kt"))
sources = "\n".join(p.read_text(encoding="utf-8") for p in kotlin_files)
refs = set(re.findall(r"R\.string\.(\w+)", sources))
check("String references resolve", not refs - set(en), f"{len(refs)} distinct references; missing={sorted(refs-set(en))}")

# Lexical delimiters only; not a grammar/type/compiler check.
def balanced_kotlin(text):
    # Remove block comments, line comments, raw strings, regular strings, chars.
    pattern = r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|/\*[\s\S]*?\*/|//[^\n]*'
    clean = re.sub(pattern, "", text)
    stack, pairs = [], {")": "(", "]": "[", "}": "{"}
    for char in clean:
        if char in "([{": stack.append(char)
        elif char in ")]}":
            if not stack or stack.pop() != pairs[char]: return False
    return not stack
check("Kotlin lexical delimiter balance", all(balanced_kotlin(p.read_text(encoding="utf-8")) for p in kotlin_files), f"{len(kotlin_files)} Kotlin files; not a compiler test")

ns = "{http://schemas.android.com/apk/res/android}"
manifest = ET.parse(ROOT/"app/src/main/AndroidManifest.xml").getroot()
permissions = {p.attrib[ns+"name"] for p in manifest.findall("uses-permission")}
check("Minimal declared permissions", permissions == {"android.permission.POST_NOTIFICATIONS", "android.permission.RECEIVE_BOOT_COMPLETED"}, ", ".join(sorted(permissions)))
application = manifest.find("application")
check("Automatic backup disabled", application.attrib[ns+"allowBackup"] == "false")
receivers = application.findall("receiver")
check("Receivers are not externally exported", all(r.attrib[ns+"exported"] == "false" for r in receivers))
extraction = ET.parse(ROOT/"app/src/main/res/xml/data_extraction_rules.xml").getroot()
check("Cloud and device transfer exclusions", all(
    {x.attrib["domain"] for x in extraction.find(name).findall("exclude")} >= {"root","database","sharedpref","file","external"}
    for name in ("cloud-backup","device-transfer")))
classes = set(re.findall(r"\bclass\s+(\w+)", sources))
components = [application.attrib[ns+"name"]] + [n.attrib[ns+"name"] for n in application if n.tag in ("activity","receiver")]
check("Manifest component names exist", all(c.split(".")[-1] in classes for c in components))

jar = ROOT/"gradle/wrapper/gradle-wrapper.jar"
expected_jar = "2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046"
check("Gradle wrapper SHA-256", hashlib.sha256(jar.read_bytes()).hexdigest() == expected_jar,
    "Compared with https://gradle.org/release-checksums/ (Gradle 8.11.1)")
with zipfile.ZipFile(jar) as z:
    check("Wrapper JAR archive integrity", z.testzip() is None and "org/gradle/wrapper/GradleWrapperMain.class" in z.namelist())
props = (ROOT/"gradle/wrapper/gradle-wrapper.properties").read_text()
check("Gradle distribution checksum pinned", "distributionSha256Sum=f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6" in props)

def luminance(color):
    channels = [int(color[i:i+2],16)/255 for i in (0,2,4)]
    linear = [x/12.92 if x <= .04045 else ((x+.055)/1.055)**2.4 for x in channels]
    return sum(a*b for a,b in zip(linear,(.2126,.7152,.0722)))
def contrast(a,b):
    x,y=sorted((luminance(a),luminance(b)), reverse=True)
    return (x+.05)/(y+.05)
ratios = {"body/panel":contrast("F1EEE5","1C2B2A"), "secondary/panel":contrast("BACAC3","1C2B2A"),
          "primary/on-primary":contrast("96D4C3","10372E")}
check("Specified text color pairs >= 4.5:1", all(x>=4.5 for x in ratios.values()), json.dumps({k:round(v,2) for k,v in ratios.items()}))
tests = len(re.findall(r"@Test\b", sources))
check("Test sources included", tests >= 18, f"{tests} declared tests; NOT EXECUTED")
result = {"kind":"source-only audit", "android_compiled":False, "unit_tests_executed":False,
          "device_tests_executed":False, "checks":checks}
output = ROOT/"docs/SOURCE_AUDIT.json"
output.parent.mkdir(exist_ok=True)
output.write_text(json.dumps(result,indent=2)+"\n",encoding="utf-8")
for entry in checks:
    print(("PASS" if entry["passed"] else "FAIL")+": "+entry["check"]+(" | "+entry["detail"] if entry["detail"] else ""))
print("NOT RUN: Kotlin compilation, JVM tests, Android instrumentation, on-device UI/PDF/reminder checks.")
raise SystemExit(0 if all(x["passed"] for x in checks) else 1)
