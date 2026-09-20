package com.lagradost.cloudstream3.syncproviders

abstract class BackupAPI : AuthAPI() {
    override val requiresLogin: Boolean = true

    open suspend fun getBackups(): List<String>? = null
    open suspend fun downloadBackup(id: String): ByteArray? = null
    open suspend fun uploadBackup(data: ByteArray, name: String): Boolean = false
}
