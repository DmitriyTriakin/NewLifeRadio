package com.newliferadio;

public interface OnPlayerUpdate {
    void onPlayService();

    void onTitleService(String content);

    void onStopService();

    void onErrorService(String error);
}
