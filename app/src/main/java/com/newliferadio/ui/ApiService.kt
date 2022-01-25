package com.newliferadio.ui

import retrofit2.Retrofit

class ApiService {

    companion object {

        const val API_URL = "http://nlradio.stream/"
    }

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(API_URL)
        .build()

    val service: Service
        get() = retrofit.create(Service::class.java)

}