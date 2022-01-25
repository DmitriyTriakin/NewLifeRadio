package com.newliferadio;

import android.os.Binder;

public class PlayerBinder extends Binder {
    private final PlayerService playerService;

    public PlayerBinder(PlayerService playerService) {
        this.playerService = playerService;
    }

    public PlayerService getService() {
        return playerService;
    }
}
