package com.sridharplays.aura.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {
    private const val BASE_URL = "https://script.google.com/macros/s/AKfycbx69QhYRHxjolik12WDEsMP4qWKuDAyOlKh9iCgqKBx4JDpc-o9k3vRHR9x4le1E-Fl/"

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}