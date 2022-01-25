package com.newliferadio

import android.os.Binder

class PlayerBinder(val service: PlayerService) : Binder()