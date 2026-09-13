awk -v replace_start=368 -v replace_end=368 '
NR < replace_start { print }
NR == replace_start {
    print "            // Provider Capsules"
    print "            item {"
    print "              androidx.compose.foundation.lazy.LazyRow("
    print "                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),"
    print "                horizontalArrangement = Arrangement.spacedBy(8.dp)"
    print "              ) {"
    print "                item {"
    print "                  FilterChip("
    print "                    selected = selectedProviderId == null,"
    print "                    onClick = { selectedProviderId = null },"
    print "                    label = { Text(\"All Providers\") }"
    print "                  )"
    print "                }"
    print "                item {"
    print "                  FilterChip("
    print "                    selected = selectedProviderId == \"local\","
    print "                    onClick = { selectedProviderId = \"local\" },"
    print "                    label = { Text(\"Local\") }"
    print "                  )"
    print "                }"
    print "                items(providerRegistry.getEnabledProviders()) { provider ->"
    print "                  FilterChip("
    print "                    selected = selectedProviderId == provider.id,"
    print "                    onClick = { selectedProviderId = provider.id },"
    print "                    label = { Text(provider.name) }"
    print "                  )"
    print "                }"
    print "              }"
    print "            }"
    print ""
    print $0
}
NR > replace_end { print }
' app/src/main/kotlin/xyz/mpv/rex/ui/browser/cinehub/CineHubScreen.kt > temp.kt && mv temp.kt app/src/main/kotlin/xyz/mpv/rex/ui/browser/cinehub/CineHubScreen.kt
