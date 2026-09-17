package com.lagradost.cloudstream3.metaproviders

import com.lagradost.cloudstream3.MainAPI

abstract class TmdbProvider : MainAPI() {
    open val useMetaLoadResponse: Boolean = false
    open val instantLinkLoading: Boolean = false
    open var useAutoDub: Boolean = false
}

abstract class SyncRepo : MainAPI()
