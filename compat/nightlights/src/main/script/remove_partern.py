import os
import json

def should_remove_parent(value: str) -> bool:
    return (
            isinstance(value, str) and
            (value.startswith("fairy_lights_") or value.startswith("hanging_lights_"))
    )

def clean_parent_fields(directory):
    for root, _, files in os.walk(directory):
        for filename in files:
            if filename.endswith(".json"):
                path = os.path.join(root, filename)
                try:
                    with open(path, "r", encoding="utf-8") as f:
                        data = json.load(f)

                    if "parent" in data and should_remove_parent(data["parent"]):
                        print(f"Removing 'parent' from {path}")
                        del data["parent"]
                        with open(path, "w", encoding="utf-8") as f:
                            json.dump(data, f, indent=4)
                except Exception as e:
                    print(f"Failed to process {path}: {e}")

# 修改为你的路径
target_dir = r"F:\code\mcmod\project\night-lights-fabric\src\main\resources\assets\nightlights\models\item"
clean_parent_fields(target_dir)
