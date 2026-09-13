sed -i 's/candidates.distinctBy { it.url }/candidates.distinctBy { it.url + it.name }/g' app/src/main/kotlin/xyz/mpv/rex/cinehub/stream/CloudStreamLinkManager.kt
