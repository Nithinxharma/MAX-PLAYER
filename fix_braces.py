import re

with open('/tmp/NfoScanner.kt.bak', 'r') as f:
    lines = f.readlines()

out = []
expected_indents = [] # Stack of expected indents for closing braces

for line in lines:
    stripped = line.strip()
    if not stripped:
        out.append(line)
        continue
    
    current_indent = len(line) - len(line.lstrip(' '))
    
    # Check if we need to insert missing closing braces before this line
    while expected_indents and current_indent < expected_indents[-1]:
        # Indent dropped, meaning blocks are closing.
        closing_indent = expected_indents.pop()
        # Only insert if it was an 8-space closing brace since we only deleted those!
        # Actually, let's just insert it and let the formatter/compiler sort it out if it was deleted.
        if closing_indent == 8:
            out.append(" " * closing_indent + "}\n")
    
    # Also if current_indent == 8 and the line is not '}', but expected_indents[-1] == 8 and this line is not a continuation.
    # Wait, if expected_indents[-1] == 8, it means a block opened at 8 spaces is active.
    # If the current line is at 8 spaces, it's a sibling statement, so the block must have closed!
    while expected_indents and current_indent == expected_indents[-1] and expected_indents[-1] == 8:
        # e.g. previous line was indented 12, this line is 8.
        # But wait, what if this line IS the closing brace?
        if stripped.startswith('}'):
            break
        # Otherwise, the block must be closed before this line
        closing_indent = expected_indents.pop()
        out.append(" " * closing_indent + "}\n")

    out.append(line)
    
    # Update expected_indents based on '{' and '}' on this line
    # Simplified: count net '{' - '}'
    net_braces = line.count('{') - line.count('}')
    if net_braces > 0:
        for _ in range(net_braces):
            expected_indents.append(current_indent)
    elif net_braces < 0:
        for _ in range(-net_braces):
            if expected_indents:
                expected_indents.pop()

while expected_indents:
    closing_indent = expected_indents.pop()
    if closing_indent == 8:
        out.append(" " * closing_indent + "}\n")

with open('app/src/main/kotlin/xyz/mpv/rex/cinehub/data/NfoScanner.kt', 'w') as f:
    f.writelines(out)

