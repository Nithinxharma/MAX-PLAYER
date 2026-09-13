import re

with open('app/src/main/kotlin/xyz/mpv/rex/ui/preferences/CineTubeSettingsScreen.kt', 'r') as f:
    content = f.read()

# I need to add import for Preference
if 'import xyz.mpv.rex.ui.preferences.components.Preference' not in content:
    content = content.replace('import xyz.mpv.rex.ui.preferences.components.SwitchPreference', 'import xyz.mpv.rex.ui.preferences.components.SwitchPreference\nimport xyz.mpv.rex.ui.preferences.components.Preference')

def replacer(match):
    title = match.group(1)
    summary = match.group(2)
    return f'''Preference(
                                title = {{ Text(text = "{title}") }},
                                summary = {{ Text(text = "{summary}", color = MaterialTheme.colorScheme.outline) }},
                                onClick = {{}}
                            )'''

content = re.sub(r'Preference\(\s*title = "(.*?)",\s*summary = "(.*?)",\s*onClick = \{\}\s*\)', replacer, content)

with open('app/src/main/kotlin/xyz/mpv/rex/ui/preferences/CineTubeSettingsScreen.kt', 'w') as f:
    f.write(content)
