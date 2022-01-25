package com.newliferadio.ui

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET

interface Service {

    @get:GET("status-cp-json.xsl")
    val trackName: Call<ResponseBody?>?
}