sed -i 's/import xyz.mpv.rex.ui.components.TopBar//g' app/src/main/kotlin/xyz/mpv/rex/ui/preferences/ExtensionPreferencesScreen.kt
awk -v replace_start=55 -v replace_end=58 '
NR < replace_start { print }
NR == replace_start {
    print "            TopAppBar("
    print "                title = { Text(\"Extensions\") },"
    print "                navigationIcon = {"
    print "                    IconButton(onClick = onNavigateBack) {"
    print "                        Icon(Icons.Default.ArrowBack, contentDescription = \"Back\")"
    print "                    }"
    print "                }"
    print "            )"
}
NR > replace_end { print }
' app/src/main/kotlin/xyz/mpv/rex/ui/preferences/ExtensionPreferencesScreen.kt > temp.kt && mv temp.kt app/src/main/kotlin/xyz/mpv/rex/ui/preferences/ExtensionPreferencesScreen.kt
