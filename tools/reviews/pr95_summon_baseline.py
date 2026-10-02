"""Read-only, builder-balanced catalog baseline for PR #95 summon pricing."""
import argparse
from collections import defaultdict
import hashlib
import json
import math
from pathlib import Path
from statistics import median
import subprocess
import sys

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--version", required=True)
parser.add_argument("--active-config", type=Path, required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[2]
collector = root / ".agents/skills/semiontd-live-balance-analysis/scripts/fetch_live_metrics.py"
catalog = json.loads(subprocess.run(
    ["rtk", "proxy", sys.executable, str(collector), "/api/v1/catalog", f"version={args.version}"],
    check=True, capture_output=True, text=True).stdout)
assert catalog["versionHash"] == args.version
config_bytes = args.active_config.read_bytes()
config = json.loads(config_bytes)
towers = {tower["id"]: tower for tower in catalog["towers"]}
incoming = defaultdict(list)
for edge in catalog["upgrades"]:
    incoming[edge["toTowerId"]].append(edge)


def total_cost(tower_id, seen=()):
    if tower_id in seen or tower_id not in towers:
        return None
    tower = towers[tower_id]
    if tower["tier"] == 1:
        return tower["mineralCost"] if tower["mineralCost"] > 0 else None
    costs = []
    for edge in incoming[tower_id]:
        previous = total_cost(edge["fromTowerId"], seen + (tower_id,))
        if previous is not None:
            costs.append(previous + edge["mineralCost"])
    return min(costs) if costs else None


result = []
for builder in catalog["builders"]:
    tiers = []
    for tier in (1, 2, 3):
        rows = []
        for tower in towers.values():
            if (tower["builderId"] != builder["id"] or tower["tier"] != tier
                    or tower["availability"] != "JOB" or tower["category"] != "DIRECT"
                    or any(tower[key] <= 0 for key in ("damage", "maxHealth", "range", "attackIntervalTicks"))):
                continue
            if config["abilities"].get(tower["id"], {}).get("towerSlotCost", 1) != 1:
                continue
            cost = total_cost(tower["id"])
            if cost is None or cost <= 0:
                continue
            dps = tower["damage"] * 20 / tower["attackIntervalTicks"]
            power = dps / 8 * (tower["range"] / 6) ** 0.8 + tower["maxHealth"] / 88
            rows.append({"id": tower["id"], "cost": cost, "health": tower["maxHealth"], "dps": dps,
                         "powerWeight": (cost / 16.5) ** (1 / 1.6) / power})
        if rows:
            tiers.append({"tier": tier, "count": len(rows), "towerIds": [row["id"] for row in rows],
                          **{key: median(row[key] for row in rows) for key in ("cost", "health", "dps", "powerWeight")}})
    result.append({"id": builder["id"], "name": builder["displayName"], "origin": builder["builderOrigin"],
                   "enabled": builder["builderEnabled"], "tiers": tiers})

summary = []
for origin in ("OFFICIAL", "CREATIVE", "ALL"):
    for tier in (1, 2, 3):
        rows = [row for builder in result if builder["enabled"]
                and (origin == "ALL" or builder["origin"] == origin)
                for row in builder["tiers"] if row["tier"] == tier]
        summary.append({"origin": origin, "tier": tier, "builders": len(rows),
                        "towers": sum(row["count"] for row in rows),
                        **{key: median(row[key] for row in rows) for key in ("cost", "health", "dps", "powerWeight")}})

# Equal builder weight, then equal weight for each available tier within a builder.
weight = median(median(row["powerWeight"] for row in builder["tiers"])
                for builder in result if builder["enabled"] and builder["tiers"])
t1_cost = next(row["cost"] for row in summary if row["origin"] == "ALL" and row["tier"] == 1)
print(json.dumps({"version": args.version, "activeConfigSha256": hashlib.sha256(config_bytes).hexdigest(),
                  "builders": result, "summary": summary, "rawPowerWeightMedian": weight,
                  "suggestedPowerWeight": math.floor(weight * 10 + 0.5) / 10,
                  # Policy floor: 10% of the T1 cost median, rounded to the existing 5-diamond step.
                  "suggestedMinimumPricePerLevel": max(5, math.floor(t1_cost * 0.1 / 5 + 0.5) * 5)},
                 ensure_ascii=False, indent=2))
