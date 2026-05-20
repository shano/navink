package com.navink.data.remote

import com.navink.data.repository.SettingsRepository
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class SubsonicAuthInterceptor @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val creds = runBlocking { settingsRepository.getCredentials() }
        val serverBase = creds.serverUrl.trimEnd('/')
        val original = chain.request()

        val rewrittenBase = "$serverBase/".toHttpUrlOrNull()
            ?: return chain.proceed(original)

        val newUrl = original.url.newBuilder()
            .scheme(rewrittenBase.scheme)
            .host(rewrittenBase.host)
            .port(rewrittenBase.port)
            .addQueryParameter("u", creds.username)
            .addQueryParameter("p", creds.password)
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "navink")
            .addQueryParameter("f", "json")
            .build()

        return chain.proceed(original.newBuilder().url(newUrl).build())
    }
}
