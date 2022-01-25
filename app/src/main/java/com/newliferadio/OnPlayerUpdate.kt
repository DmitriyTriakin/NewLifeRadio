package com.newliferadio

interface OnPlayerUpdate {
    fun onPlayService()
    fun onTitleService(content: String?)
    fun onStopService()
    fun onErrorService(error: String?)
}