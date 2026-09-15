sed -i 's/provider.search(query).collect { results ->/val results = provider.search(query)/g' app/src/main/kotlin/xyz/mpv/rex/cinehub/stream/CloudStreamSearchManager.kt
sed -i 's/                        emit(results)/emit(results)/g' app/src/main/kotlin/xyz/mpv/rex/cinehub/stream/CloudStreamSearchManager.kt
sed -i '18d' app/src/main/kotlin/xyz/mpv/rex/cinehub/stream/CloudStreamSearchManager.kt
