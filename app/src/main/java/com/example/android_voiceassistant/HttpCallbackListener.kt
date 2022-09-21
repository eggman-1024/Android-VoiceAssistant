package com.example.android_voiceassistant

interface HttpCallbackListener {
    fun onFinish(response: String)
    fun onError(e: Exception)
}