awk -v replace_start=76 -v replace_end=93 '
NR < replace_start { print }
NR == replace_start {
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
    print "        results.distinctBy { it.videoId }.shuffled()"
    print "    }"
}
NR > replace_end { print }
' app/src/main/kotlin/xyz/mpv/rex/youtube/data/InvidiousClient.kt > temp.kt && mv temp.kt app/src/main/kotlin/xyz/mpv/rex/youtube/data/InvidiousClient.kt
