package com.theupnextapp.network

import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import kotlinx.coroutines.Deferred
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TvMazeService {
    @GET("schedule")
    fun getYesterdayScheduleAsync(
        @Query("country") countryCode: String, @Query("date") date: String?
    ): Deferred<List<YesterdayNetworkSchedule>>

    @GET("schedule")
    fun getTodayScheduleAsync(
        @Query("country") countryCode: String, @Query("date") date: String?
    ): Deferred<List<TodayNetworkSchedule>>

    @GET("schedule")
    fun getTomorrowScheduleAsync(
        @Query("country") countryCode: String, @Query("date") date: String?
    ): Deferred<List<TomorrowNetworkSchedule>>

    @GET("search/shows")
    fun getSuggestionListAsync(@Query("q") name: String): Deferred<List<NetworkShowSearchResponse>>

    @GET("shows/{id}")
    fun getShowSummaryAsync(@Path("id") id: String?): Deferred<NetworkShowInfoResponse>

    @GET("/episodes/{id}")
    fun getNextEpisodeAsync(@Path("id") name: String?): Deferred<NetworkShowNextEpisode>

    @GET("/episodes/{id}")
    fun getPreviousEpisodeAsync(@Path("id") name: String?): Deferred<NetworkShowPreviousEpisode>
}

object TvMazeNetwork {
    private const val BASE_URL = "http://api.tvmaze.com/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient().newBuilder()
        .addInterceptor(loggingInterceptor)
        .build()

    private val retrofit = Retrofit.Builder()
        .client(client)
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .addCallAdapterFactory(CoroutineCallAdapterFactory())
        .build()

    val tvMazeApi: TvMazeService = retrofit.create(TvMazeService::class.java)
}