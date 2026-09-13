sed -i 's/val extDeferreds = activeProviders.map { provider ->/val extDeferreds = emptyList<kotlinx.coroutines.Deferred<List<xyz.mpv.rex.cinehub.extension.api.CineHubSearchItem>>>()/g' app/src/main/kotlin/xyz/mpv/rex/ui/browser/cinehub/CineHubScreen.kt
sed -i 's/async {/ /g' app/src/main/kotlin/xyz/mpv/rex/ui/browser/cinehub/CineHubScreen.kt
