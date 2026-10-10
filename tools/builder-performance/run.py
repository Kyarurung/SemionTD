import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import statistics
import subprocess
import tempfile
import time
import urllib.parse
import urllib.request
import zipfile
import io
import re
import threading
import sys
import math
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[2]
sys.stdout.reconfigure(encoding="utf-8")
EVIDENCE = ROOT / "build" / "builder-performance"
FILTER = "semion-td-gametest:game_builder_performance_test_measures_builder_lane_ticks"
HELPER = "src/main/java/kim/biryeong/semiontd/tower/area/AreaTargetSelection.java"
RUNNER_SHA256 = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
BASELINE_GAME_TESTS = ["src/gametest/java/kim/biryeong/semiontd/tower/" + path for path in
    ["demonlord/DemonLordGameTest.java", "futureagency/FutureAgencyGameTest.java",
     "mage/MageGameTest.java", "thunder/ThunderGameTest.java"]]
DEFAULT_BASELINE = "2b431d1c6c85fca14c045a3c6bce492b1940b665"


def digest(data):
    return hashlib.sha256(data).hexdigest()


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT)


def fetch_spark():
    query = urllib.parse.urlencode({"loaders": json.dumps(["fabric"]), "game_versions": json.dumps(["26.3"])})
    url = "https://api.modrinth.com/v2/project/spark/version?" + query
    request = urllib.request.Request(url, headers={"User-Agent": "SemionTD-builder-performance/1.0"})
    with urllib.request.urlopen(request, timeout=30) as response:
        versions = json.load(response)
    if not versions:
        raise RuntimeError("No published Spark Fabric artifact for Minecraft 26.3")
    version = next(value for value in versions if "fabric" in value["loaders"] and "26.3" in value["game_versions"])
    artifact = next(value for value in version["files"] if value["primary"])
    with urllib.request.urlopen(urllib.request.Request(artifact["url"], headers={"User-Agent": "SemionTD-builder-performance/1.0"}), timeout=30) as response:
        data = response.read()
    if hashlib.sha512(data).hexdigest() != artifact["hashes"]["sha512"] or hashlib.sha1(data).hexdigest() != artifact["hashes"]["sha1"]:
        raise RuntimeError("Spark artifact hash mismatch")
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        if archive.testzip() is not None:
            raise RuntimeError("Invalid Spark ZIP")
        for name in archive.namelist():
            if name.startswith(("/", "\\")) or ".." in Path(name.replace("\\", "/")).parts:
                raise RuntimeError("Unsafe archive path")
        metadata = json.loads(archive.read("fabric.mod.json"))
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    path = EVIDENCE / "spark.jar"
    if path.exists():
        raise RuntimeError("Existing Spark artifact is preserved")
    path.write_bytes(data)
    write_json(EVIDENCE / "spark-provenance.json", {"api": url, "versionId": version["id"],
        "version": version["version_number"], "published": version["date_published"], "file": artifact,
        "sha256": digest(data), "fabricMetadata": metadata, "scope": "ISOLATED_PROFILE_RUNTIME_ONLY"})
    print(json.dumps({"version": version["version_number"], "sha256": digest(data), "dependencies": metadata.get("depends")}))


def prepare(args):
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    if (EVIDENCE / "checkouts.json").exists():
        raise RuntimeError("Existing evidence is preserved; choose a new evidence directory")
    baseline = git("rev-parse", "--verify", "--end-of-options", args.baseline + "^{commit}").decode().strip()
    checkout_root = Path(tempfile.mkdtemp(prefix="semion-builder-performance-"))
    paths = sorted(set(git("ls-files", "--cached", "--others", "--exclude-standard", "-z").decode().split("\0")) - {""})
    paths = [p for p in paths if (ROOT / p).is_file() and not p.startswith("src/main/resources/assets/semion-td/")]
    changed_main = git("diff", "--name-only", "--diff-filter=M", baseline, "--", "src/main/java").decode().splitlines()
    manifests = {}
    reference_helper = """package kim.biryeong.semiontd.tower.area;

import java.util.Comparator;
import java.util.List;

public final class AreaTargetSelection {
    private AreaTargetSelection() {
    }

    public static <T> List<T> sortedFirst(List<T> candidates, Comparator<? super T> order, int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("Negative target limit: " + limit);
        }
        return candidates.stream().sorted(order).limit(limit).toList();
    }
}
"""
    for variant in ["original", "separated", "optimized"]:
        checkout = checkout_root / variant
        for relative in paths:
            destination = checkout / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / relative, destination)
        if variant != "optimized":
            for relative in changed_main:
                (checkout / relative).write_bytes(git("show", baseline + ":" + relative))
            (checkout / HELPER).write_text(reference_helper, encoding="utf-8")
            for relative in BASELINE_GAME_TESTS:
                (checkout / relative).write_bytes(git("show", baseline + ":" + relative))
        if variant == "separated":
            relative = "src/main/java/kim/biryeong/semiontd/tower/area/AreaEffectService.java"
            (checkout / relative).write_bytes((ROOT / relative).read_bytes())
            relative = "src/main/java/kim/biryeong/semiontd/tower/blueprint/BlueprintTower.java"
            (checkout / relative).write_bytes((ROOT / relative).read_bytes())
            relative = "src/main/java/kim/biryeong/semiontd/game/PlayerLane.java"
            source = (checkout / relative).read_text(encoding="utf-8")
            start = source.index("    void tickTowers() {")
            source = source[:start] + source[start:].replace("List.copyOf(towers)", "towerSnapshot()", 1)
            source = source.replace("    public void clearTowers() {",
                    "    private List<Tower> towerSnapshot() {\n        return List.copyOf(towers);\n    }\n\n    public void clearTowers() {", 1)
            (checkout / relative).write_text(source, encoding="utf-8")
        manifest = {relative: digest((checkout / relative).read_bytes()) for relative in paths}
        manifest_path = EVIDENCE / (variant + "-source-manifest.json")
        write_json(manifest_path, manifest)
        manifests[variant] = {"path": str(checkout), "manifest": str(manifest_path), "sha256": digest(manifest_path.read_bytes())}
    init = EVIDENCE / "limits.gradle"
    init.write_text("""allprojects {
    tasks.withType(Test).configureEach {
        maxParallelForks = 1
        maxHeapSize = '1g'
        jvmArgs '-XX:ActiveProcessorCount=4'
    }
}
gradle.projectsEvaluated {
    rootProject.tasks.named('runGameTest').configure {
        minHeapSize = '2g'
        maxHeapSize = '2g'
        jvmArgs '-XX:+UseG1GC', '-XX:ActiveProcessorCount=4'
        if (System.getProperty('builderProfile') == 'true') {
            systemProperty 'semiontd.builderProfile', 'true'
            systemProperty 'fabric-api.gametest.filter', '""" + FILTER + """'
            systemProperty 'semiontd.profileVariant', System.getProperty('builderVariant')
            systemProperty 'semiontd.builderWarmup', System.getProperty('builderWarmup', '32')
            systemProperty 'semiontd.builderSamples', System.getProperty('builderSamples', '32')
            systemProperty 'semiontd.builderBatch', System.getProperty('builderBatch', '16')
            if (System.getProperty('builderSpark') == 'true') {
                classpath = classpath + rootProject.files(System.getProperty('builderSparkJar'))
                systemProperty 'semiontd.builderSpark', 'true'
            }
            if (System.getProperty('builderJfr') == 'true') {
                systemProperty 'semiontd.builderJfr', 'true'
            }
        }
    }
}
""", encoding="utf-8")
    write_json(EVIDENCE / "checkouts.json", {"createdUtc": datetime.now(timezone.utc).isoformat(),
        "head": baseline, "paths": paths, "variants": manifests,
        "limits": str(init), "restoredOriginalProduction": changed_main,
        "fixtureSha256": digest((ROOT / "src/gametest/java/kim/biryeong/semiontd/game/GameBuilderPerformanceTest.java").read_bytes())})
    print(json.dumps({"checkouts": str(checkout_root), "files": len(paths), "restored": len(changed_main)}))


def sync(args):
    state = json.loads((EVIDENCE / "checkouts.json").read_text(encoding="utf-8"))
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%f")
    write_json(EVIDENCE / ("checkouts-before-" + stamp + ".json"), state)
    paths = sorted(set(git("ls-files", "--cached", "--others", "--exclude-standard", "-z").decode().split("\0")) - {""})
    state["paths"] = [p for p in paths if (ROOT / p).is_file() and not p.startswith("src/main/resources/assets/semion-td/")]
    for variant, info in state["variants"].items():
        if args.only_variant is not None and variant != args.only_variant:
            continue
        checkout = Path(info["path"])
        for relative in state["paths"]:
            if variant != "optimized" and relative.startswith("src/main/java/"):
                if variant != "separated" or not relative.endswith(("AreaEffectService.java", "BlueprintTower.java")):
                    continue
            (checkout / relative).parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / relative, checkout / relative)
        if variant != "optimized":
            for relative in BASELINE_GAME_TESTS:
                (checkout / relative).write_bytes(git("show", state["head"] + ":" + relative))
        manifest = {relative: digest((checkout / relative).read_bytes()) for relative in state["paths"]}
        path = EVIDENCE / (variant + "-source-manifest-" + stamp + ".json")
        write_json(path, manifest)
        info.update(manifest=str(path), sha256=digest(path.read_bytes()))
    state["fixtureSha256"] = digest((ROOT / "src/gametest/java/kim/biryeong/semiontd/game/GameBuilderPerformanceTest.java").read_bytes())
    init_path = Path(state["limits"])
    init_source = init_path.read_text(encoding="utf-8")
    if "builderJfr" not in init_source:
        init_source = init_source.replace("            if (System.getProperty('builderSpark') == 'true') {",
                "            if (System.getProperty('builderJfr') == 'true') {\n"
                "                systemProperty 'semiontd.builderJfr', 'true'\n            }\n"
                "            if (System.getProperty('builderSpark') == 'true') {")
        init_path.write_text(init_source, encoding="utf-8")
    write_json(EVIDENCE / "checkouts.json", state)
    print(json.dumps({"synced": stamp, "fixtureSha256": state["fixtureSha256"]}))


def archive(args):
    if not re.fullmatch(r"candidate-v[1-9][0-9]*", args.archive_name):
        raise RuntimeError("Archive names must use candidate-v followed by a positive integer")
    destination = EVIDENCE / args.archive_name
    if destination.exists():
        raise RuntimeError("Existing candidate archive is preserved")
    for variant in ["original", "separated", "optimized"]:
        for fork in [1, 2, 3]:
            descriptor = json.loads((EVIDENCE / (variant + "-f" + str(fork) + "-run.json")).read_text(encoding="utf-8"))
            if descriptor.get("exitCode") != 0:
                raise RuntimeError("Cannot archive an incomplete suite")
    destination.mkdir()
    for path in EVIDENCE.iterdir():
        if path.is_file() and (re.match(r"(?:builder-profile-)?(?:original|separated|optimized)-f[1-3](?:[.-]|$)", path.name)
                              or path.name in {"comparison.json", "comparison.md"}):
            target = destination / path.name
            if not path.resolve().is_relative_to(EVIDENCE.resolve()) or not target.resolve().is_relative_to(EVIDENCE.resolve()):
                raise RuntimeError("Unsafe archive path")
            path.rename(target)
    print(json.dumps({"archived": str(destination)}))


def show_log(args):
    path = EVIDENCE / ((args.tag or args.variant) + ".log")
    data = path.read_text(encoding="utf-8", errors="replace").splitlines()
    lines = [line for line in data if any(token in line.lower() for token in
        ["error", "exception", "failed", "failure", "spark", "caused by", "assert", "no tests", "profile", "build "]) ]
    print("\n".join(lines[-70:]))


def run(args):
    state = json.loads((EVIDENCE / "checkouts.json").read_text(encoding="utf-8"))
    if args.variant == "workspace" and args.mode != "gate":
        raise RuntimeError("The workspace is reserved for the release gate")
    checkout = ROOT if args.variant == "workspace" else Path(state["variants"][args.variant]["path"])
    tag = args.tag or (args.variant + "-" + args.mode)
    if not re.fullmatch(r"[A-Za-z0-9._-]{1,80}", tag):
        raise RuntimeError("Invalid execution tag")
    log = EVIDENCE / (tag + ".log")
    if log.exists():
        raise RuntimeError("Refusing to overwrite an existing execution log: " + str(log))
    tasks = ["test", "runGameTest", "remapJar"] if args.mode == "gate" else ["runGameTest"]
    command = ["cmd.exe", "/d", "/c", str(checkout / "gradlew.bat"), *tasks, "--offline", "--max-workers=1",
               "-Dorg.gradle.jvmargs=-Xmx1G -XX:ActiveProcessorCount=2", "--init-script", state["limits"],
               "--console=plain", "--no-daemon"]
    if args.mode == "profile":
        command += ["-DbuilderProfile=true", "-DbuilderVariant=" + tag,
                    "-DbuilderWarmup=" + str(args.warmup), "-DbuilderSamples=" + str(args.samples),
                    "-DbuilderBatch=" + str(args.batch)]
        if args.spark:
            command += ["-DbuilderSpark=true", "-DbuilderSparkJar=" + str(EVIDENCE / "spark.jar")]
        if args.jfr:
            command += ["-DbuilderJfr=true"]
    if args.variant == "workspace":
        manifest = {relative: digest((ROOT / relative).read_bytes()) for relative in state["paths"]}
        manifest_path = EVIDENCE / (tag + "-source-manifest.json")
        write_json(manifest_path, manifest)
        source_hash = digest(manifest_path.read_bytes())
    else:
        source_hash = state["variants"][args.variant]["sha256"]
        manifest_path = Path(state["variants"][args.variant]["manifest"])
        if digest(manifest_path.read_bytes()) != source_hash:
            raise RuntimeError("Checkout manifest hash differs")
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        for relative, expected in manifest.items():
            if digest((checkout / relative).read_bytes()) != expected:
                raise RuntimeError("Checkout changed without sync: " + relative)
    descriptor = {"command": command, "cwd": str(checkout), "startUtc": datetime.now(timezone.utc).isoformat(),
                  "variant": args.variant, "mode": args.mode, "sourceManifestSha256": source_hash,
                  "runnerSha256": RUNNER_SHA256}
    started = time.monotonic()
    monitor_stop = threading.Event()
    monitor_path = EVIDENCE / (tag + "-load.jsonl")
    monitor_command = """$builderCpu = Get-CimInstance Win32_PerfFormattedData_PerfOS_Processor -Filter "Name='_Total'";
$builderProcesses = Get-CimInstance Win32_Process | Where-Object { $_.Name -match '^(java|javaw|hoi4|python|node|powershell|pwsh)\\.exe$' } | Select-Object ProcessId,ParentProcessId,Name,ExecutablePath;
$builderProcessRates = Get-CimInstance Win32_PerfFormattedData_PerfProc_Process | Where-Object { $_.Name -match '^(java|javaw|hoi4|python|node|powershell|pwsh)' } | Select-Object IDProcess,Name,PercentProcessorTime,IODataBytesPersec,WorkingSetPrivate;
$builderMemory = Get-CimInstance Win32_OperatingSystem | Select-Object TotalVisibleMemorySize,FreePhysicalMemory;
[pscustomobject]@{ utc=[DateTime]::UtcNow.ToString('o'); cpu=$builderCpu.PercentProcessorTime; memory=$builderMemory; processes=@($builderProcesses); processRates=@($builderProcessRates) } | ConvertTo-Json -Depth 5 -Compress
"""
    def monitor():
        with monitor_path.open("w", encoding="utf-8") as stream:
            while not monitor_stop.is_set():
                try:
                    result = subprocess.run(["powershell.exe", "-NoProfile", "-Command", monitor_command],
                        capture_output=True, timeout=15, creationflags=subprocess.CREATE_NO_WINDOW)
                    value = json.loads(result.stdout.decode("utf-8-sig", errors="replace"))
                    value["queryExit"] = result.returncode
                except Exception as failure:
                    value = {"utc": datetime.now(timezone.utc).isoformat(), "error": type(failure).__name__}
                stream.write(json.dumps(value, ensure_ascii=False) + "\n")
                stream.flush()
                monitor_stop.wait(2)
    monitor_thread = threading.Thread(target=monitor, daemon=True)
    monitor_thread.start()
    with log.open("wb") as stream:
        process = subprocess.Popen(command, cwd=checkout, stdout=stream, stderr=subprocess.STDOUT)
        descriptor["pid"] = process.pid
        write_json(EVIDENCE / (tag + "-run.json"), descriptor)
        code = process.wait()
    monitor_stop.set()
    monitor_thread.join(timeout=20)
    descriptor.update(exitCode=code, wallSeconds=time.monotonic() - started,
                      endUtc=datetime.now(timezone.utc).isoformat(), logSha256=digest(log.read_bytes()))
    descriptor["loadSha256"] = digest(monitor_path.read_bytes())
    marker = checkout / "build/26.3-gametest-run-dir.txt"
    if marker.exists():
        run_dir = Path(marker.read_text(encoding="utf-8").strip())
        descriptor["isolatedRunDirectory"] = str(run_dir)
        for extension in ["json", "jfr"]:
            artifact = run_dir / ("builder-profile-" + tag + "." + extension)
            if artifact.exists():
                destination = EVIDENCE / artifact.name
                shutil.copyfile(artifact, destination)
                descriptor[extension + "Sha256"] = digest(destination.read_bytes())
        spark_outputs = []
        for artifact in run_dir.glob("config/spark/*.sparkprofile"):
            destination = EVIDENCE / (tag + "-" + artifact.name)
            shutil.copyfile(artifact, destination)
            spark_outputs.append({"path": str(destination), "sha256": digest(destination.read_bytes())})
        descriptor["sparkOutputs"] = spark_outputs
    write_json(EVIDENCE / (tag + "-run.json"), descriptor)
    text = log.read_text(encoding="utf-8", errors="replace")
    lines = [line for line in text.splitlines() if any(token in line for token in
             ["BUILD ", "required tests", "FAILED", "error:", "Exception", "No tests found", "Profile"])]
    print(json.dumps(descriptor, ensure_ascii=False))
    print("\n".join(lines[-22:]))
    return code


def suite(args):
    order = [("original", 1), ("separated", 1), ("optimized", 1),
             ("optimized", 2), ("separated", 2), ("original", 2),
             ("original", 3), ("separated", 3), ("optimized", 3)]
    for variant, fork in order:
        args.variant = variant
        args.mode = "profile"
        args.tag = variant + "-f" + str(fork)
        descriptor_path = EVIDENCE / (args.tag + "-run.json")
        if descriptor_path.exists():
            previous = json.loads(descriptor_path.read_text(encoding="utf-8"))
            if previous.get("exitCode") == 0:
                state = json.loads((EVIDENCE / "checkouts.json").read_text(encoding="utf-8"))
                if previous["sourceManifestSha256"] != state["variants"][variant]["sha256"]:
                    raise RuntimeError("Archive the completed suite before comparing changed sources")
                continue
            stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%f")
            for suffix in [".log", "-run.json", "-load.jsonl"]:
                existing = EVIDENCE / (args.tag + suffix)
                if existing.exists():
                    existing.rename(EVIDENCE / (args.tag + "-failed-" + stamp + suffix))
        code = run(args)
        if code:
            return code
    compare()
    return 0


def validate(args):
    validation_tag = args.tag or "final"
    args.mode = "profile"
    args.warmup = 32
    args.samples = 10
    args.batch = 1
    args.spark = True
    args.jfr = True
    for variant in ["original", "optimized"]:
        args.variant = variant
        args.tag = variant + "-" + validation_tag + "-diag"
        code = run(args)
        if code:
            return code
    args.variant = "workspace"
    args.mode = "gate"
    args.tag = "workspace-" + validation_tag + "-gate"
    return run(args)


def compare():
    files = sorted(EVIDENCE.glob("builder-profile-*.json"))
    profiles = [(path, json.loads(path.read_text(encoding="utf-8"))) for path in files
                if re.fullmatch(r"builder-profile-(original|separated|optimized)-f[1-3]", path.stem)]
    if not profiles:
        raise RuntimeError("No profile outputs")
    grouped = {}
    settings = set()
    for path, profile in profiles:
        variant = profile["variant"].split("-", 1)[0]
        if variant not in {"original", "separated", "optimized"}:
            continue
        grouped.setdefault(variant, []).append(profile)
        settings.add((profile["java"], profile["heap"], profile["worldSeed"], tuple(profile["vmArguments"]),
                      profile.get("jfr", True), profile.get("spark", False), profile["seed"],
                      tuple(profile["origin"]), profile["clockStart"], profile.get("preconditioningSweeps", 0),
                      profile["limits"], tuple(profile["builders"])))
        descriptor = json.loads((EVIDENCE / (profile["variant"] + "-run.json")).read_text(encoding="utf-8"))
        if descriptor.get("exitCode") != 0 or descriptor.get("jsonSha256") != digest(path.read_bytes()):
            raise RuntimeError("Missing or inconsistent successful execution provenance")
    if len(settings) != 1:
        raise RuntimeError("Java, heap or world seed differs between stages")
    if any(len(grouped.get(v, [])) != 3 for v in ["original", "separated", "optimized"]):
        raise RuntimeError("At least three fresh forks per stage are required")
    keys = {(cell["builder"], cell["size"]) for cell in profiles[0][1]["cells"]}
    if any({(cell["builder"], cell["size"]) for cell in profile["cells"]} != keys for path, profile in profiles):
        raise RuntimeError("Measurement cells differ between stages")
    for variant, runs in grouped.items():
        descriptors = [json.loads((EVIDENCE / (profile["variant"] + "-run.json")).read_text(encoding="utf-8")) for profile in runs]
        if len({entry["sourceManifestSha256"] for entry in descriptors}) != 1:
            raise RuntimeError("Source versions differ between forks")
        state = json.loads((EVIDENCE / "checkouts.json").read_text(encoding="utf-8"))
        if descriptors[0]["sourceManifestSha256"] != state["variants"][variant]["sha256"]:
            raise RuntimeError("Comparison sources differ from the recorded checkout")
    comparisons = []
    for key in sorted(keys):
        stages = {}
        hashes = set()
        input_hashes = set()
        parameters = set()
        for variant, runs in grouped.items():
            cells = [next(cell for cell in profile["cells"] if (cell["builder"], cell["size"]) == key) for profile in runs]
            hashes.update(cell["traceSha256"] for cell in cells)
            input_hashes.update(cell["inputSha256"] for cell in cells)
            parameters.update((cell['batch'], cell['warmup'], cell['samples']) for cell in cells)
            if len({(cell['batch'], cell['warmup'], cell['samples']) for cell in cells}) != 1:
                raise RuntimeError("Measurement settings differ between forks")
            stages[variant] = {"forkMedianNs": [statistics.median(cell["nanos"]) / cell["batch"] for cell in cells],
                               "forkMeanNs": [statistics.mean(cell["nanos"]) / cell["batch"] for cell in cells],
                               "forkP95Ns": [sorted(cell["nanos"])[math.ceil(len(cell["nanos"]) * .95) - 1] / cell["batch"] for cell in cells],
                               "forkMaxNs": [max(cell["nanos"]) / cell["batch"] for cell in cells],
                               "forkBytes": [statistics.mean(cell["allocatedBytes"]) / cell["batch"] for cell in cells]}
            stages[variant]["medianNs"] = statistics.median(stages[variant]["forkMedianNs"])
            stages[variant]["bytes"] = statistics.median(stages[variant]["forkBytes"])
            if "cpuNanos" in cells[0]:
                stages[variant]["forkCpuMeanNs"] = [statistics.mean(cell["cpuNanos"]) / cell["batch"] for cell in cells]
                stages[variant]["setupNanos"] = [cell["setupNanos"] for cell in cells]
                stages[variant]["setupAllocatedBytes"] = [cell["setupAllocatedBytes"] for cell in cells]
                if "firstTickNanos" in cells[0]:
                    stages[variant]["firstTickNanos"] = [cell["firstTickNanos"] for cell in cells]
                    stages[variant]["firstTickAllocatedBytes"] = [cell["firstTickAllocatedBytes"] for cell in cells]
                stages[variant]["gcCountIncludingWarmup"] = [cell["gcCountIncludingWarmup"] for cell in cells]
                stages[variant]["gcMillisIncludingWarmup"] = [cell["gcMillisIncludingWarmup"] for cell in cells]
        if len(parameters) != 1:
            raise RuntimeError("Measurement settings differ between stages")
        equivalent = len(hashes) == 1 and len(input_hashes) == 1
        before = stages["original"]["medianNs"]
        after = stages["optimized"]["medianNs"]
        comparisons.append({"builder": key[0], "size": key[1], "equivalentObservedTrace": equivalent,
                            "inputHashes": sorted(input_hashes), "traceHashes": sorted(hashes), "stages": stages,
                            "medianImprovementPercent": 100 * (before - after) / before if equivalent else None})
    write_json(EVIDENCE / "comparison.json", {"profiles": [{"path": str(p), "sha256": digest(p.read_bytes())} for p, profile in profiles],
        "comparisons": comparisons, "limits": profiles[0][1]["limits"]})
    lines = ["| Builder / kernel | N | Observed trace | Original ns/op | Separation ns/op | Optimized ns/op | Reduction | Original B/op | Optimized B/op |",
             "|---|---:|---|---:|---:|---:|---:|---:|---:|"]
    for cell in comparisons:
        stages = cell["stages"]
        value = cell["medianImprovementPercent"]
        percent = "BLOCKED" if value is None else f"{value:+.2f}%"
        lines.append(f"| {cell['builder']} | {cell['size']} | {'equal' if cell['equivalentObservedTrace'] else 'DIVERGED'} | "
                     f"{stages['original']['medianNs']:.2f} | {stages['separated']['medianNs']:.2f} | "
                     f"{stages['optimized']['medianNs']:.2f} | {percent} | {stages['original']['bytes']:.2f} | {stages['optimized']['bytes']:.2f} |")
    (EVIDENCE / "comparison.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines))


def verify(args):
    tag = args.tag or "workspace-final-gate"
    descriptor = json.loads((EVIDENCE / (tag + "-run.json")).read_text(encoding="utf-8"))
    if descriptor.get("exitCode") != 0 or descriptor.get("variant") != "workspace" or descriptor.get("mode") != "gate":
        raise RuntimeError("A successful workspace release gate is required")
    manifest_path = EVIDENCE / (tag + "-source-manifest.json")
    log_path = EVIDENCE / (tag + ".log")
    if descriptor.get("sourceManifestSha256") != digest(manifest_path.read_bytes()) or descriptor.get("logSha256") != digest(log_path.read_bytes()):
        raise RuntimeError("Release gate provenance differs")
    passed = re.findall(r"All (\d+) required tests passed", log_path.read_text(encoding="utf-8"))
    if len(passed) != 1:
        raise RuntimeError("Missing complete GameTest result")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    state = json.loads((EVIDENCE / "checkouts.json").read_text(encoding="utf-8"))
    sources = git("diff", "--name-only", "--diff-filter=M", state["head"], "--", "src/main/java").decode().splitlines() + [HELPER]
    for source in sources:
        if manifest.get(source) != digest((ROOT / source).read_bytes()):
            raise RuntimeError("Production source changed after the release gate: " + source)
    measured_path = Path(state["variants"]["optimized"]["manifest"])
    if digest(measured_path.read_bytes()) != state["variants"]["optimized"]["sha256"]:
        raise RuntimeError("Measured source manifest differs")
    measured = json.loads(measured_path.read_text(encoding="utf-8"))
    for fork in [1, 2, 3]:
        tag_path = EVIDENCE / ("optimized-f" + str(fork) + "-run.json")
        recorded = json.loads(tag_path.read_text(encoding="utf-8"))
        if recorded.get("exitCode") != 0 or recorded["sourceManifestSha256"] != state["variants"]["optimized"]["sha256"]:
            raise RuntimeError("Measured forks do not match the checked production source")
    fixture = "src/gametest/java/kim/biryeong/semiontd/game/GameBuilderPerformanceTest.java"
    for source in state["paths"]:
        if source.startswith("src/main/java/") or source == fixture:
            if measured[source] != digest((ROOT / source).read_bytes()):
                raise RuntimeError("Measured production/fixture differs from the workspace: " + source)
    suites = [ET.parse(path).getroot() for path in (ROOT / "build/test-results/test").glob("TEST-*.xml")]
    totals = {key: sum(int(suite.get(key, 0)) for suite in suites)
              for key in ["tests", "failures", "errors", "skipped"]}
    if not suites or totals["failures"] or totals["errors"]:
        raise RuntimeError("Missing or failing JUnit results")
    artifacts = [path for path in (ROOT / "build/libs").glob("semion-td-*.jar")
                 if not path.name.endswith(("-sources.jar", "-javadoc.jar"))]
    if len(artifacts) != 1:
        raise RuntimeError("Expected one distributable JAR")
    jar = artifacts[0]
    checked = {}
    test_classes = {path.relative_to(ROOT / source_set).with_suffix(".class").as_posix()
                    for source_set in ["src/test/java", "src/gametest/java"]
                    for path in (ROOT / source_set).rglob("*.java")}
    with zipfile.ZipFile(jar) as archive:
        if archive.testzip() is not None:
            raise RuntimeError("Invalid distributable ZIP")
        names = archive.namelist()
        for name in names:
            if name.startswith(("/", "\\")) or ".." in Path(name.replace("\\", "/")).parts:
                raise RuntimeError("Unsafe distributable entry")
        private_assets = {}
        for entry in archive.infolist():
            if entry.filename.startswith("assets/semion-td/") and not entry.is_dir():
                source = ROOT / "src/main/resources" / entry.filename
                data = archive.read(entry)
                if not source.is_file() or source.read_bytes() != data:
                    raise RuntimeError("Local private asset differs from its source: " + entry.filename)
                private_assets[entry.filename] = digest(data)
        if any((name.split("$", 1)[0] + ".class" if "$" in name else name) in test_classes
               or name == "fabric-gametest.json" for name in names):
            raise RuntimeError("Test runtime entered the distributable")
        metadata = json.loads(archive.read("fabric.mod.json"))
        if metadata["depends"]["minecraft"] != "26.3" or metadata["depends"]["java"] != ">=25":
            raise RuntimeError("Unexpected runtime metadata")
        for source in sources:
            relative = Path(source).relative_to("src/main/java").with_suffix(".class")
            compiled = ROOT / "build/classes/java/main" / relative
            for path in [compiled, *compiled.parent.glob(compiled.stem + "$*.class")]:
                entry = path.relative_to(ROOT / "build/classes/java/main").as_posix()
                data = path.read_bytes()
                if archive.read(entry) != data or int.from_bytes(data[6:8], "big") != 69:
                    raise RuntimeError("Distributable class differs from Java 25 compilation: " + entry)
                checked[entry] = digest(data)
    result = {"gate": tag, "junit": totals, "requiredGameTests": int(passed[0]), "jar": str(jar), "jarSha256": digest(jar.read_bytes()),
              "metadata": metadata, "checkedClasses": checked, "zipCrc": "PASS", "safeEntryPaths": "PASS",
              "privateAssetFiles": len(private_assets), "privateAssetSha256": private_assets,
              "publicReleaseSafe": not private_assets, "artifactScope": "LOCAL_VALIDATION_ONLY",
              "testClassEntries": 0, "measuredProductionMatchesWorkspace": True}
    write_json(EVIDENCE / "workspace-artifact-verification.json", result)
    print(json.dumps({key: value for key, value in result.items() if key not in {"metadata", "checkedClasses", "privateAssetSha256"}}))


parser = argparse.ArgumentParser()
parser.add_argument("action", choices=["prepare", "run", "suite", "validate", "compare", "verify", "fetch-spark", "sync", "show-log", "archive"])
parser.add_argument("--variant", choices=["original", "separated", "optimized", "workspace"], default="optimized")
parser.add_argument("--mode", choices=["gate", "profile"], default="gate")
parser.add_argument("--tag")
parser.add_argument("--warmup", type=int, default=32)
parser.add_argument("--samples", type=int, default=32)
parser.add_argument("--batch", type=int, default=16)
parser.add_argument("--spark", action="store_true")
parser.add_argument("--jfr", action="store_true")
parser.add_argument("--archive-name", default="candidate-v1")
parser.add_argument("--only-variant", choices=["original", "separated", "optimized"])
parser.add_argument("--baseline", default=DEFAULT_BASELINE)
args = parser.parse_args()
if args.action == "show-log":
    show_log(args)
elif args.action == "archive":
    archive(args)
elif args.action == "sync":
    sync(args)
elif args.action == "fetch-spark":
    fetch_spark()
elif args.action == "prepare":
    prepare(args)
elif args.action == "run":
    raise SystemExit(run(args))
elif args.action == "suite":
    raise SystemExit(suite(args))
elif args.action == "validate":
    raise SystemExit(validate(args))
elif args.action == "verify":
    verify(args)
else:
    compare()
