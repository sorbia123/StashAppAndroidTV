package com.github.damontecres.stashapp.mobile.net

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.network.okHttpClient
import okhttp3.Credentials
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit

object StashMobileClient {
    fun createOkHttpClient(
        apiKey: String = "",
        username: String = "",
        password: String = ""
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val requestBuilder = chain.request().newBuilder()
                if (username.isNotBlank()) {
                    requestBuilder.header("Authorization", Credentials.basic(username, password))
                }
                if (apiKey.isNotBlank()) {
                    requestBuilder.header("ApiKey", apiKey)
                    requestBuilder.header("ApiKey-ApiKey", apiKey)
                }
                val response = chain.proceed(requestBuilder.build())
                if (response.code == 401 || response.code == 403) {
                    throw IOException("Invalid username, password, or API key (HTTP ${response.code} Unauthorized)")
                }
                response
            }
            .build()
    }

    fun createApolloClient(
        serverUrl: String,
        apiKey: String = "",
        username: String = "",
        password: String = ""
    ): ApolloClient {
        val cleanUrl = serverUrl.trim().trimEnd('/')
        val graphqlEndpoint = "$cleanUrl/graphql"
        val okHttpClient = createOkHttpClient(apiKey, username, password)
        return ApolloClient.Builder()
            .serverUrl(graphqlEndpoint)
            .okHttpClient(okHttpClient)
            .build()
    }
}
