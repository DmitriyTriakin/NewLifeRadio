package com.newliferadio.ui;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.http.GET;

public class ApiService {

    private Retrofit adapter;

    public ApiService() {
        Retrofit.Builder builder = new Retrofit.Builder();
        adapter = builder.baseUrl("https://nlradio.stream/").build();
    }

    public Service getService() {
        return adapter.create(Service.class);
    }

    public interface Service {

        @GET("status-cp-json.xsl")
        Call<ResponseBody> getTrackName();
    }
}
