package com.quizmemo.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.quizmemo.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object ApiClient {
    private val json = AppJson

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        })
        .addInterceptor(AuthInterceptor)
        .build()

    val api: QuizApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(http)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(QuizApi::class.java)
}

object TokenStore {
    @Volatile private var current: String? = null

    fun set(token: String) { current = token }
    fun clear() { current = null }
    fun get(): String? = current
}

private object AuthInterceptor : okhttp3.Interceptor {
    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val req = chain.request().newBuilder().apply {
            TokenStore.get()?.let { addHeader("Authorization", "Bearer $it") }
        }.build()
        return chain.proceed(req)
    }
}
