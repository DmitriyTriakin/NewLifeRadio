package com.newliferadio.ui;

import retrofit2.Retrofit;

public class ApiService {

    private Retrofit retrofit;
    private final String API_URL = "http://nlradio.stream/";

    public ApiService() {
        retrofit = new Retrofit.Builder()
                .baseUrl(API_URL)
                .build();
    }

    public Service getService() {
        return retrofit.create(Service.class);
    }

}
