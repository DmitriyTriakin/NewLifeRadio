package com.newliferadio.ui;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;

public interface Service {

    @GET("status-cp-json.xsl")
    Call<ResponseBody> getTrackName();
}
