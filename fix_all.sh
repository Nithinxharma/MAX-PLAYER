echo "package xyz.mpv.rex.youtube.data" > temp1.kt
echo "import kotlinx.coroutines.async" >> temp1.kt
grep -v "package xyz.mpv.rex.youtube.data" app/src/main/kotlin/xyz/mpv/rex/youtube/data/InvidiousClient.kt | grep -v "import kotlinx.coroutines.async" >> temp1.kt
mv temp1.kt app/src/main/kotlin/xyz/mpv/rex/youtube/data/InvidiousClient.kt

echo "package xyz.mpv.rex.ui.browser.shorts" > temp2.kt
echo "import androidx.compose.ui.unit.dp" >> temp2.kt
grep -v "package xyz.mpv.rex.ui.browser.shorts" app/src/main/kotlin/xyz/mpv/rex/ui/browser/shorts/RexShortsScreen.kt | grep -v "import androidx.compose.ui.unit.dp" >> temp2.kt
mv temp2.kt app/src/main/kotlin/xyz/mpv/rex/ui/browser/shorts/RexShortsScreen.kt

sed -i 's/?: stream.resolution//g' app/src/main/kotlin/xyz/mpv/rex/youtube/data/InvidiousClient.kt
