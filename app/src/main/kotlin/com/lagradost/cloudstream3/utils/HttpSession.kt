package com.lagradost.cloudstream3.utils

import androidx.annotation.Keep
import com.lagradost.nicehttp.Requests

@Keep
open class HttpSession(
    var requests: Requests = com.lagradost.cloudstream3.app
)
