package xyz.mpv.rex.domain.media.model

import androidx.compose.runtime.Immutable

@Immutable
data class VideoFolder(
  val bucketId: String,
  val name: String,
  val path: String,
  val videoCount: Int,
  val audioCount: Int = 0,
  val totalSize: Long = 0L,
  val totalDuration: Long = 0L, // in milliseconds
  val lastModified: Long = 0L,
  val newCount: Int = 0,
  val unwatchedVideoCount: Int = 0,
  val posterPath: String? = null,
  val backdropPath: String? = null,
  val rating: Double = 0.0,
  val year: String = "",
  val genre: String = "",
  val isTvShow: Boolean = false,
  val isMovie: Boolean = false,
  val mediaTitle: String? = null,
)
