awk '
NR < 68 { print }
NR == 68 {
    print "    suspend fun fetchShorts(): List<YoutubeVideo> = withContext(Dispatchers.IO) {"
    print "        val results = mutableListOf<YoutubeVideo>()"
    print "        val endpoints = listOf("
    print "            \"api/v1/popular?region=IN\","
    print "            \"api/v1/trending?region=IN\","
    print "            \"api/v1/search?q=%23shorts+hindi&region=IN\","
    print "            \"api/v1/search?q=yt%3Ashorts&region=IN\""
    print "        )"
    print "        val deferreds = endpoints.map {"
    print "            kotlinx.coroutines.async { failoverClient.executeGet(it) }"
    print "        }"
    print "        val responses = deferreds.mapNotNull { it.await() }"
    print "        for (res in responses) {"
    print "            try {"
    print "                val parsed = jsonParser.decodeFromString<List<YoutubeVideo>>(res)"
    print "                results.addAll(parsed.filter { it.lengthSeconds in 1..90 || it.title.contains(\"#shorts\", ignoreCase = true) || it.title.contains(\"shorts\", ignoreCase = true) || it.isShort })"
    print "            } catch (_: Exception) {}"
    print "        }"
    print "        if (results.isEmpty()) return@withContext fetchSearchVideos(\"%23shorts\")"
    print "        return@withContext results.distinctBy { it.videoId }.shuffled()"
    print "    }"
    skip = 1
}
NR > 68 && skip == 1 {
    if ($0 ~ /suspend fun fetchSearchVideos/) {
        skip = 0
        print ""
        print "    /**"
        print $0
    }
}
NR > 68 && skip == 0 { print }
' app/src/main/kotlin/xyz/mpv/rex/youtube/data/InvidiousClient.kt > temp.kt && mv temp.kt app/src/main/kotlin/xyz/mpv/rex/youtube/data/InvidiousClient.kt
