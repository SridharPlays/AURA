package com.sridharplays.aura.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {
    @GET("exec")
    suspend fun getJournalEntries(@Query("action") action: String = "getEntries"): Response<JournalResponse>

    @POST("exec")
    suspend fun postMood(@Body moodData: MoodPostData): Response<Unit>

    @POST("exec")
    suspend fun updateNote(@Body noteData: NoteUpdateData): Response<Unit>

    @GET("exec")
    suspend fun getLatestMood(@Query("action") action: String = "getLatestMood"): Response<LatestMoodResponse>

    @GET("exec")
    suspend fun deleteEntry(
        @Query("action") action: String = "deleteEntry",
        @Query("id") id: String
    ): Response<Unit>
}