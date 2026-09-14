sed -i 's/if (selectedTab == 0 || selectedTab == 1) {/if ((selectedTab == 0 || selectedTab == 1) \&\& selectedProviderId == null) {/g' app/src/main/kotlin/xyz/mpv/rex/ui/browser/cinehub/CineHubScreen.kt
sed -i 's/if (selectedTab == 0 || selectedTab == 2) {/if ((selectedTab == 0 || selectedTab == 2) \&\& selectedProviderId == null) {/g' app/src/main/kotlin/xyz/mpv/rex/ui/browser/cinehub/CineHubScreen.kt
sed -i 's/if (selectedTab == 0 \&\& providerHomeRows.isNotEmpty()) {/if (selectedProviderId != null) {/g' app/src/main/kotlin/xyz/mpv/rex/ui/browser/cinehub/CineHubScreen.kt
