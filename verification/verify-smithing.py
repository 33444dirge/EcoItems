"""Verify the actual artifacts and completed isolated-server evidence; no server mocks."""
import hashlib
import json
import re
import zipfile
from pathlib import Path
import yaml

root = Path(__file__).resolve().parents[2]
evidence = root / "evidence"
jar = root / "EcoItems/build/libs/EcoItems-2026.27-2-all.jar"
old = evidence / "original-EcoItems-2026.27-1.jar"
def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()
assert sha(old) == "3e797df9ed9892ea29eecfa89d67cef37ee522521410fe5b56a34fb96fcc0435"
with zipfile.ZipFile(old) as a, zipfile.ZipFile(jar) as b:
    assert b.testzip() is None
    changed = [n for n in a.namelist() if n.endswith(".class") and a.read(n) != b.read(n)]
    assert set(changed) == {"com/willfp/ecoitems/EcoItemsPlugin.class", "com/willfp/ecoitems/EcoItemsPluginKt.class"}, changed
    for name in [n for n in a.namelist() if n.endswith(".yml")]:
        x, y = [z.read(name).decode("utf-8").replace("\r\n", "\n") for z in (a, b)]
        if name == "plugin.yml":
            y = re.sub(r"(?m)^version: 2026\.27-2$", "version: 2026.27-1", y)
        assert x == y, name
    assert "smithing.yml" in b.namelist()
    assert all(not n.startswith("probe/") for n in b.namelist())
    plugin = yaml.safe_load(b.read("plugin.yml"))
    assert plugin["name"] == "EcoItems" and plugin["version"] == "2026.27-2" and plugin["folia-supported"] is True

runs = {}
for label in ["baseline3", "baseline7", "protected4", "registry-green"]:
    log = (evidence / (label + ".log")).read_text(encoding="utf-8")
    assert "SMITH_FAIL" not in log and "SMITH_DONE" in log, label
    doc = yaml.safe_load((evidence / (label + ".yml")).read_text(encoding="utf-8"))
    runs[label] = dict(checks=doc["checks"], prepareEvents=doc["prepares"], takeEvents=doc["takes"],
                       snapshots=len(re.findall(r"SMITH_SNAPSHOT source=", log)))

baseline = yaml.safe_load((evidence / "baseline3.yml").read_text(encoding="utf-8"))
assert baseline["eco"]["recipe"] == "enderite:dz_enderite_helmet"
assert baseline["eco"]["expected"] == "taken" and baseline["eco"]["identity"] == "none"
assert '"ecoitems:item"' not in baseline["eco"]["output"]["components"]["minecraft:custom_data"]
assert baseline["ce"]["recipe"] == "none" and baseline["ce"].get("output") is None
assert baseline["combat"]["original"]["cancelled"] is True
assert baseline["combat"]["upgraded"]["cancelled"] is False
repeat = yaml.safe_load((evidence / "baseline7.yml").read_text(encoding="utf-8"))
assert repeat["eco"]["identity"] == "none" and repeat["ce"]["recipe"] == "none"
assert repeat["combat"]["original"]["cancelled"] is True and repeat["combat"]["original"]["debuff"] is True
assert repeat["combat"]["upgraded"]["cancelled"] is False and repeat["combat"]["upgraded"]["debuff"] is False

final = yaml.safe_load((evidence / "protected4.yml").read_text(encoding="utf-8"))
for source in ["eco", "eco-renamed", "eco-legacy", "ce", "ce-renamed", "ce-trim", "sword"]:
    assert final[source]["expected"] == "blocked" and final[source].get("output") is None, source
for source in ["plain", "fake-name", "trim", "plain-sword"]:
    assert final[source]["expected"] == "taken" and final[source]["output"], source
assert final["trim"]["identity"] == "pumpkin_king_crown"
assert "SMITH_TAKE click=LEFT cancelled=true" in (evidence / "registry-green.log").read_text(encoding="utf-8")
assert "blocked inputs unchanged eco slot=0" in (evidence / "registry-red.log").read_text(encoding="utf-8")

inputs = json.loads((evidence / "inputs.json").read_text(encoding="utf-8-sig"))
result = dict(version="2026.27-2", sha256=sha(jar), bytes=jar.stat().st_size, changedOriginalClasses=changed,
              rulesAssertions=11, junitTests=0, runs=runs, originalInputs=inputs,
              coreSha256=sha(root / "lab/versions/26.2/shiroha-26.2.jar"), deployedToProduction=False)
(evidence / "verification-results.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps(result, ensure_ascii=False, indent=2))
