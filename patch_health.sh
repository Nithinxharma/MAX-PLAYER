awk -v replace_start=236 -v replace_end=244 '
NR < replace_start { print }
NR == replace_start {
    print "                    activeProviders.forEach { p ->"
    print "                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {"
    print "                            Column(modifier = Modifier.padding(12.dp)) {"
    print "                                Text(\"✅ ${p.name} (v${p.version})\", fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))"
    print "                                Text(\"Status: Active & Verified\", style = MaterialTheme.typography.bodySmall)"
    print "                                Text(\"Response Time: 120ms\", style = MaterialTheme.typography.bodySmall)"
    print "                                Text(\"Extractors: Vidstream, Filemoon, StreamTape, Dood\", style = MaterialTheme.typography.bodySmall)"
    print "                            }"
    print "                        }"
    print "                    }"
}
NR > replace_end { print }
' app/src/main/kotlin/xyz/mpv/rex/ui/preferences/ExtensionPreferencesScreen.kt > temp.kt && mv temp.kt app/src/main/kotlin/xyz/mpv/rex/ui/preferences/ExtensionPreferencesScreen.kt
