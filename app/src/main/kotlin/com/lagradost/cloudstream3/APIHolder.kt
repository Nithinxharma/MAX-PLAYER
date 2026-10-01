package com.lagradost.cloudstream3

import java.util.concurrent.CopyOnWriteArrayList

object APIHolder {
    val apis: CopyOnWriteArrayList<MainAPI> = CopyOnWriteArrayList()
    val allProviders: List<MainAPI> get() = apis.toList()

    fun addProvider(api: MainAPI) {
        apis.removeAll { it.name.equals(api.name, ignoreCase = true) }
        apis.add(api)
    }

    fun removeProvider(name: String) {
        apis.removeAll { it.name.equals(name, ignoreCase = true) }
    }

    fun getApiFromName(name: String): MainAPI? {
        return apis.find { it.name.equals(name, ignoreCase = true) }
    }
}
