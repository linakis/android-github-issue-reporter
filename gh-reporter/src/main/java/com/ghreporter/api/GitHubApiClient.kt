package com.ghreporter.api

import com.ghreporter.auth.SecureTokenStorage
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Factory for creating GitHub API service instances.
 */
object GitHubApiClient {

    private const val BASE_URL = "https://api.github.com/"
    private const val API_VERSION = "2022-11-28"

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    /**
     * Create a GitHubApiService instance with the provided token.
     *
     * @param tokenStorage Token storage to get the access token
     * @return Configured GitHubApiService
     */
    fun create(tokenStorage: SecureTokenStorage): GitHubApiService {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenStorage))
            .addInterceptor(GitHubHeadersInterceptor())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return retrofit.create(GitHubApiService::class.java)
    }

    /**
     * Create a GitHubApiService instance with a direct token.
     *
     * @param token GitHub access token
     * @return Configured GitHubApiService
     */
    fun create(token: String): GitHubApiService {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(DirectAuthInterceptor(token))
            .addInterceptor(GitHubHeadersInterceptor())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return retrofit.create(GitHubApiService::class.java)
    }

    /**
     * Interceptor that adds the OAuth token from storage.
     */
    private class AuthInterceptor(
        private val tokenStorage: SecureTokenStorage
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()
            
            val token = tokenStorage.getGitHubToken()
            if (token.isNullOrBlank()) {
                return chain.proceed(originalRequest)
            }

            val authenticatedRequest = originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()

            return chain.proceed(authenticatedRequest)
        }
    }

    /**
     * Interceptor that adds a direct token.
     */
    private class DirectAuthInterceptor(
        private val token: String
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val authenticatedRequest = chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()

            return chain.proceed(authenticatedRequest)
        }
    }

    /**
     * Interceptor that adds required GitHub API headers.
     */
    private class GitHubHeadersInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request().newBuilder()
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", API_VERSION)
                .header("User-Agent", "GHReporter-Android-SDK")
                .build()

            return chain.proceed(request)
        }
    }
}
