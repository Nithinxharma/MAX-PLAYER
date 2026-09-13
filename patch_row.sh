awk -v replace_start=1347 -v replace_end=1351 '
NR < replace_start { print }
NR == replace_start {
    print "        androidx.compose.foundation.lazy.LazyRow("
    print "          horizontalArrangement = Arrangement.spacedBy(4.dp),"
    print "          modifier = Modifier.weight(1f)"
    print "        ) {"
    print "          val names = if (item.providerNames.isNotEmpty()) item.providerNames else listOf(item.providerName)"
    print "          items(names) { name ->"
    print "            SuggestionChip("
    print "              onClick = {},"
    print "              label = { Text(\"[$name]\", style = MaterialTheme.typography.labelSmall) },"
    print "              modifier = Modifier.height(24.dp)"
    print "            )"
    print "          }"
    print "        }"
}
NR > replace_end { print }
' app/src/main/kotlin/xyz/mpv/rex/ui/browser/cinehub/CineHubScreen.kt > temp.kt && mv temp.kt app/src/main/kotlin/xyz/mpv/rex/ui/browser/cinehub/CineHubScreen.kt
