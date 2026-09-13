sed -i 's/url = safeUrl + \"#bollyflix_1080p\",/url = safeUrl,/g' app/src/main/kotlin/xyz/mpv/rex/cinehub/stream/CloudStreamLinkManager.kt
sed -i 's/url = safeUrl + \"#bollyflix_720p\",/url = safeUrl,/g' app/src/main/kotlin/xyz/mpv/rex/cinehub/stream/CloudStreamLinkManager.kt
sed -i 's/url = safeUrl + \"#superstream_4k\",/url = safeUrl,/g' app/src/main/kotlin/xyz/mpv/rex/cinehub/stream/CloudStreamLinkManager.kt
sed -i 's/url = safeUrl + \"#superstream_1080p\",/url = safeUrl,/g' app/src/main/kotlin/xyz/mpv/rex/cinehub/stream/CloudStreamLinkManager.kt
sed -i 's/url = safeUrl + \"#uhdmovies_1080p\",/url = safeUrl,/g' app/src/main/kotlin/xyz/mpv/rex/cinehub/stream/CloudStreamLinkManager.kt
sed -i 's/url = safeUrl + \"#vidsrc_1080p\",/url = safeUrl,/g' app/src/main/kotlin/xyz/mpv/rex/cinehub/stream/CloudStreamLinkManager.kt
