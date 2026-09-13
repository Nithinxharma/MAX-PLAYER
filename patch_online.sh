sed -i 's/if (onlineMovies.isNotEmpty()) {/if (onlineMovies.isNotEmpty() \&\& selectedProviderId != \"local\") {/g' app/src/main/kotlin/xyz/mpv/rex/ui/browser/cinehub/CineHubScreen.kt
sed -i 's/if (onlineTvShows.isNotEmpty()) {/if (onlineTvShows.isNotEmpty() \&\& selectedProviderId != \"local\") {/g' app/src/main/kotlin/xyz/mpv/rex/ui/browser/cinehub/CineHubScreen.kt
