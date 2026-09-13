awk -v replace_start=123 -v replace_end=142 '
NR < replace_start { print }
NR == replace_start {
    print "    override suspend fun loadStreams(data: String): List<CineHubStreamLink> = withContext(Dispatchers.IO) {"
    print "        val clean = data.removePrefix(\"ext://$id/\").substringBefore(\"?\")"
    print "        val streams = mutableListOf<CineHubStreamLink>()"
    print "        val nf = CineCloudRepoClient.resolveDirectStreamUrl(clean, \"nf\")"
    print "        val pv = CineCloudRepoClient.resolveDirectStreamUrl(clean, \"pv\")"
    print "        val hs = CineCloudRepoClient.resolveDirectStreamUrl(clean, \"hs\")"
    print "        val dp = CineCloudRepoClient.resolveDirectStreamUrl(clean, \"dp\")"
    print "        val best = nf ?: pv ?: hs ?: dp"
    print "        if (!best.isNullOrBlank() && !best.contains(\"/embed/\")) {"
    print "            streams.add(CineHubStreamLink(name = \"[$name] Vidstream\", url = nf ?: best, quality = \"1080p\", isM3u8 = (nf ?: best).contains(\".m3u8\")))"
    print "            streams.add(CineHubStreamLink(name = \"[$name] Filemoon\", url = pv ?: best, quality = \"1080p\", isM3u8 = (pv ?: best).contains(\".m3u8\")))"
    print "            streams.add(CineHubStreamLink(name = \"[$name] StreamTape\", url = hs ?: best, quality = \"720p\", isM3u8 = (hs ?: best).contains(\".m3u8\")))"
    print "            streams.add(CineHubStreamLink(name = \"[$name] Dood\", url = dp ?: best, quality = \"480p\", isM3u8 = (dp ?: best).contains(\".m3u8\")))"
    print "        }"
    print "        streams"
    print "    }"
}
NR > replace_end { print }
' app/src/main/kotlin/xyz/mpv/rex/cinehub/extension/providers/DeclarativeCineHubProvider.kt > temp.kt && mv temp.kt app/src/main/kotlin/xyz/mpv/rex/cinehub/extension/providers/DeclarativeCineHubProvider.kt

awk -v replace_start=76 -v replace_end=102 '
NR < replace_start { print }
NR == replace_start {
    print "    override suspend fun loadStreams(data: String): List<CineHubStreamLink> = withContext(Dispatchers.IO) {"
    print "        val streams = mutableListOf<CineHubStreamLink>()"
    print "        val clean = data.removePrefix(\"tmdb://\")"
    print "        val nf = CineCloudRepoClient.resolveDirectStreamUrl(clean, \"nf\")"
    print "        val pv = CineCloudRepoClient.resolveDirectStreamUrl(clean, \"pv\")"
    print "        val hs = CineCloudRepoClient.resolveDirectStreamUrl(clean, \"hs\")"
    print "        val dp = CineCloudRepoClient.resolveDirectStreamUrl(clean, \"dp\")"
    print "        val best = nf ?: pv ?: hs ?: dp"
    print "        if (!best.isNullOrBlank() && !best.contains(\"/embed/\")) {"
    print "            streams.add(CineHubStreamLink(name = \"[$name] Vidstream\", url = nf ?: best, quality = \"1080p\", isM3u8 = (nf ?: best).contains(\".m3u8\")))"
    print "            streams.add(CineHubStreamLink(name = \"[$name] Filemoon\", url = pv ?: best, quality = \"1080p\", isM3u8 = (pv ?: best).contains(\".m3u8\")))"
    print "            streams.add(CineHubStreamLink(name = \"[$name] StreamTape\", url = hs ?: best, quality = \"720p\", isM3u8 = (hs ?: best).contains(\".m3u8\")))"
    print "            streams.add(CineHubStreamLink(name = \"[$name] Dood\", url = dp ?: best, quality = \"480p\", isM3u8 = (dp ?: best).contains(\".m3u8\")))"
    print "        }"
    print "        streams"
    print "    }"
}
NR > replace_end { print }
' app/src/main/kotlin/xyz/mpv/rex/cinehub/extension/providers/CineOnlineBridgeProvider.kt > temp2.kt && mv temp2.kt app/src/main/kotlin/xyz/mpv/rex/cinehub/extension/providers/CineOnlineBridgeProvider.kt
