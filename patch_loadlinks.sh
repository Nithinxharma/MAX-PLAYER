sed -i 's/{ extractor ->/{ subtitle ->/g' app/src/main/kotlin/xyz/mpv/rex/cinehub/extension/api/ProviderAPI.kt
sed -i 's/links.add(/ \/\/ do nothing/g' app/src/main/kotlin/xyz/mpv/rex/cinehub/extension/api/ProviderAPI.kt
sed -i 's/CineHubStreamLink(/ /g' app/src/main/kotlin/xyz/mpv/rex/cinehub/extension/api/ProviderAPI.kt
