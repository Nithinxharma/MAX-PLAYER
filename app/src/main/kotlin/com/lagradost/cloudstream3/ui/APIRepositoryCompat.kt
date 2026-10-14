package com.lagradost.cloudstream3.ui

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.mvvm.Resource

data class SearchResults(val list: List<SearchResponse>)

class APIRepository(val api: MainAPI) {
    suspend fun search(query: String): Resource<SearchResults> {
        return try {
            val list = api.search(query) ?: emptyList()
            Resource.Success(SearchResults(list))
        } catch (t: Throwable) {
            Resource.Failure(false, t.message ?: "Search failed")
        }
    }

    suspend fun load(url: String): Resource<LoadResponse> {
        return try {
            val res = api.load(url)
            if (res != null) {
                Resource.Success(res)
            } else {
                Resource.Failure(false, "Load returned null")
            }
        } catch (t: Throwable) {
            Resource.Failure(false, t.message ?: "Load failed")
        }
    }

    suspend fun loadLinks(
        data: String,
        isCasting: Boolean = false,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Resource<Boolean> {
        return try {
            val res = api.loadLinks(data, isCasting, subtitleCallback, callback)
            Resource.Success(res)
        } catch (t: Throwable) {
            Resource.Failure(false, t.message ?: "LoadLinks failed")
        }
    }
}
