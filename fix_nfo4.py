import re

with open('app/src/main/kotlin/xyz/mpv/rex/cinehub/data/NfoScanner.kt', 'r') as f:
    content = f.read()

# find any method body that doesn't have a closing brace
# Since I replaced `}` with `if (...)` earlier, the sed I ran to reverse it was:
# sed -i ':a;N;$!ba;s/        if (allVideoFiles.isEmpty() && isVideoFile(showFolder)) {\n            allVideoFiles.add(showFolder)\n        }/        }/g'
# Wait! This reversed ONLY the exact match with 8 spaces. What if the `}` was at 4 spaces?
# The earlier sed was:
# sed -i 's/        }/        if (allVideoFiles.isEmpty() \&\& isVideoFile(showFolder)) {\n            allVideoFiles.add(showFolder)\n        }/g'
# So it ONLY matched 8 spaces `        }`!
# Let me look for ANY remaining `if (allVideoFiles.isEmpty() && isVideoFile(showFolder)) {` and replace with `}`
content = re.sub(r'        if \(allVideoFiles\.isEmpty\(\) && isVideoFile\(showFolder\)\) \{\n            allVideoFiles\.add\(showFolder\)\n        \}', r'        }', content)
content = re.sub(r'    if \(allVideoFiles\.isEmpty\(\) && isVideoFile\(showFolder\)\) \{\n        allVideoFiles\.add\(showFolder\)\n    \}', r'    }', content)
content = re.sub(r'if \(allVideoFiles\.isEmpty\(\) && isVideoFile\(showFolder\)\) \{\n\s*allVideoFiles\.add\(showFolder\)\n\s*\}', r'}', content)

with open('app/src/main/kotlin/xyz/mpv/rex/cinehub/data/NfoScanner.kt', 'w') as f:
    f.write(content)
