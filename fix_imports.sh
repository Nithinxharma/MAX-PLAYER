sed -i '1d' app/src/main/kotlin/xyz/mpv/rex/ui/browser/shorts/RexShortsScreen.kt
sed -i '/package/a import androidx.compose.ui.unit.dp' app/src/main/kotlin/xyz/mpv/rex/ui/browser/shorts/RexShortsScreen.kt

sed -i '1d' app/src/main/kotlin/xyz/mpv/rex/youtube/data/InvidiousClient.kt
sed -i '/package/a import kotlinx.coroutines.async' app/src/main/kotlin/xyz/mpv/rex/youtube/data/InvidiousClient.kt
