awk -v replace_start=673 -v replace_end=674 '
NR < replace_start { print }
NR == replace_start {
    print "              if (selectedProviderId != \"local\") {"
    print "                val filteredRows = if (selectedProviderId == null) providerHomeRows else providerHomeRows.filter { it.items.firstOrNull()?.providerId == selectedProviderId }"
    print "                filteredRows.forEach { homeRow ->"
}
NR > replace_end { print }
' app/src/main/kotlin/xyz/mpv/rex/ui/browser/cinehub/CineHubScreen.kt > temp.kt && mv temp.kt app/src/main/kotlin/xyz/mpv/rex/ui/browser/cinehub/CineHubScreen.kt
