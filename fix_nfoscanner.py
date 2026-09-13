import re

with open('app/src/main/kotlin/xyz/mpv/rex/cinehub/data/NfoScanner.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
for i, line in enumerate(lines):
    if "android.util.Log.d(\"Series\"" in line:
        if i > 0 and "if (!showFolder.exists())" in lines[i-1]:
            new_lines[-1] = new_lines[-1].replace('\n', '') + " return episodes\n"
            continue
        if "showFolder.parentFile ?:" in line:
            new_lines[-1] = new_lines[-1].replace('\n', '') + " return episodes\n"
            continue
    elif "return episodes" in line and "android.util.Log.d" not in line and "showFolder.parentFile ?:" in lines[i-1]:
        continue
    new_lines.append(line)

with open('app/src/main/kotlin/xyz/mpv/rex/cinehub/data/NfoScanner.kt', 'w') as f:
    f.writelines(new_lines)
