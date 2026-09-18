import zipfile
import subprocess
import os

apk_path = ".build-outputs/app-debug.apk"
if not os.path.exists(apk_path):
    # Search for apk
    for root, dirs, files in os.walk("."):
        for f in files:
            if f.endswith(".apk"):
                apk_path = os.path.join(root, f)
                break

print("Checking APK:", apk_path)
out_dir = "build/unpacked_verify_dex"
os.makedirs(out_dir, exist_ok=True)

with zipfile.ZipFile(apk_path, "r") as z:
    dex_names = [n for n in z.namelist() if n.endswith(".dex")]
    print("Found DEX files:", len(dex_names))
    for n in dex_names:
        z.extract(n, out_dir)

found_json_exception = []
found_json_classes = []

for dex in dex_names:
    dex_path = os.path.join(out_dir, dex)
    cmd = ["/opt/android/sdk/build-tools/36.0.0/dexdump", "-f", dex_path]
    res = subprocess.run(cmd, capture_output=True, text=True)
    for line in res.stdout.splitlines():
        if "Class descriptor" in line and "Lorg/json/" in line:
            found_json_classes.append((dex, line.strip()))
            if "JSONException" in line:
                found_json_exception.append((dex, line.strip()))

print("Total org/json class definitions:", len(found_json_classes))
print("Total org/json/JSONException class definitions:", len(found_json_exception))
if found_json_classes:
    for item in found_json_classes:
        print(" ", item)
else:
    print("VERIFICATION SUCCESS: No org/json classes defined in any APK DEX!")
