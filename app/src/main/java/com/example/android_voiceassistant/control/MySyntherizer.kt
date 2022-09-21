package com.example.android_voiceassistant.control

import android.content.Context
import android.os.Handler
import android.os.Message
import android.util.Log
import android.util.Pair
import com.baidu.tts.client.SpeechSynthesizeBag
import com.baidu.tts.client.SpeechSynthesizer
import com.example.android_voiceassistant.MainHandlerConstant
import com.example.android_voiceassistant.MainHandlerConstant.INIT_SUCCESS
import com.example.android_voiceassistant.MainHandlerConstant.PRINT


/**
 * 该类是对SpeechSynthesizer的封装
 *
 *
 * Created by fujiayi on 2017/5/24.
 */
class MySyntherizer protected constructor(context: Context, mainHandler: Handler?) : MainHandlerConstant {
    protected var mSpeechSynthesizer: SpeechSynthesizer? = null
    protected var context: Context
    protected var mainHandler: Handler?

    constructor(context: Context, initConfig: InitConfig, mainHandler: Handler?) : this(
        context,
        mainHandler
    ) {
        init(initConfig)
    }

    /**
     * 注意该方法需要在新线程中调用。且该线程不能结束。详细请参见NonBlockSyntherizer的实现
     *
     * @param config 配置
     * @return 是否初始化成功
     */
    fun init(config: InitConfig): Boolean {
        sendToUiThread("初始化开始")
        mSpeechSynthesizer = SpeechSynthesizer.getInstance()
        mSpeechSynthesizer?.setContext(context)
        Log.i("MySyntherizer", "包名:" + context.packageName)
        val listener = config.listener

        // listener = new SwitchSpeakerListener(mainHandler,context,this); // 测试播放过程中切换发音人逻辑
        mSpeechSynthesizer?.setSpeechSynthesizerListener(listener)


        // 请替换为语音开发者平台上注册应用得到的App ID ,AppKey ，Secret Key ，填写在SynthActivity的开始位置
        mSpeechSynthesizer?.setAppId(config.appId)
        mSpeechSynthesizer?.setApiKey(config.appKey, config.secretKey)
        setParams(config.params)
        // 初始化tts
        val result = mSpeechSynthesizer?.initTts(config.ttsMode)
        if (result != 0) {
            sendToUiThread("【error】initTts 初始化失败 + errorCode：$result")
            return false
        }

        // 设置播放的音频流类型，具体参数和组合见AudioAttributes,https://source.android.google.cn/devices/audio/attributes
        // mSpeechSynthesizer.setAudioAttributes(AudioAttributes.USAGE_MEDIA,AudioAttributes.CONTENT_TYPE_MUSIC);

        // 此时可以调用 speak和synthesize方法
        sendToUiThread(INIT_SUCCESS, "合成引擎初始化成功")
        return true
    }

    /**
     * 合成并播放
     *
     * @param text 小于1024 GBK字节，即512个汉字或者字母数字
     * @return =0表示成功
     */
    fun speak(text: String): Int {
        if (!isInitied) {
            throw RuntimeException("TTS 还未初始化")
        }
        Log.i(TAG, "speak text:$text")
        return mSpeechSynthesizer!!.speak(text)
    }

    /**
     * 合成并播放
     *
     * @param text        小于1024 GBK字节，即512个汉字或者字母数字
     * @param utteranceId 用于listener的回调，默认"0"
     * @return =0表示成功
     */
    fun speak(text: String?, utteranceId: String?): Int {
        if (!isInitied) {
            throw RuntimeException("TTS 还未初始化")
        }
        return mSpeechSynthesizer!!.speak(text, utteranceId)
    }

    /**
     * 只合成不播放
     *
     * @param text 合成的文本
     * @return =0表示成功
     */
    fun synthesize(text: String?): Int {
        if (!isInitied) {
            // SpeechSynthesizer.getInstance() 不要连续调用
            throw RuntimeException("TTS 还未初始化")
        }
        return mSpeechSynthesizer!!.synthesize(text)
    }

    fun synthesize(text: String?, utteranceId: String?): Int {
        if (!isInitied) {
            // SpeechSynthesizer.getInstance() 不要连续调用
            throw RuntimeException("TTS 还未初始化")
        }
        return mSpeechSynthesizer!!.synthesize(text, utteranceId)
    }

    fun batchSpeak(texts: List<Pair<String?, String?>>): Int {
        if (!isInitied) {
            throw RuntimeException("TTS 还未初始化")
        }
        val bags: MutableList<SpeechSynthesizeBag> = ArrayList()
        for (pair in texts) {
            val speechSynthesizeBag = SpeechSynthesizeBag()
            speechSynthesizeBag.text = pair.first
            if (pair.second != null) {
                speechSynthesizeBag.utteranceId = pair.second
            }
            bags.add(speechSynthesizeBag)
        }
        return mSpeechSynthesizer!!.batchSpeak(bags)
    }

    fun setParams(params: Map<String, String>) {
        if (params != null) {
            for ((key, value) in params) {
                mSpeechSynthesizer!!.setParam(key, value)
            }
        }
    }

    fun pause(): Int {
        return mSpeechSynthesizer!!.pause()
    }

    fun resume(): Int {
        return mSpeechSynthesizer!!.resume()
    }

    fun stop(): Int {
        return mSpeechSynthesizer!!.stop()
    }

    /**
     * 引擎在合成时该方法不能调用！！！
     * 注意 只有 TtsMode.MIX 才可以切换离线发音
     *
     * @return
     */
    fun loadModel(modelFilename: String?, textFilename: String?): Int {
        val res = mSpeechSynthesizer!!.loadModel(modelFilename, textFilename)
        sendToUiThread("切换离线发音人成功。")
        return res
    }

    /**
     * 设置播放音量，默认已经是最大声音
     * 0.0f为最小音量，1.0f为最大音量
     *
     * @param leftVolume  [0-1] 默认1.0f
     * @param rightVolume [0-1] 默认1.0f
     */
    fun setStereoVolume(leftVolume: Float, rightVolume: Float) {
        mSpeechSynthesizer!!.setStereoVolume(leftVolume, rightVolume)
    }

    fun release() {
        Log.i("MySyntherizer", "MySyntherizer release called")
        if (!isInitied) {
            // 这里报错是因为连续两次 new MySyntherizer。
            // 必须第一次new 之后，调用release方法
            throw RuntimeException("TTS 还未初始化")
        }
        mSpeechSynthesizer!!.stop()
        mSpeechSynthesizer!!.release()
        mSpeechSynthesizer = null
        isInitied = false
    }

    protected fun sendToUiThread(message: String) {
        sendToUiThread(PRINT, message)
    }

    protected fun sendToUiThread(action: Int, message: String) {
        Log.i(TAG, message)
        if (mainHandler == null) { // 可以不依赖mainHandler
            return
        }
        val msg = Message.obtain()
        msg.what = action
        msg.obj = """
            $message
            
            """.trimIndent()
        mainHandler!!.sendMessage(msg)
    }

    companion object {
        private const val TAG = "MySyntherizer"

        @Volatile
        protected var isInitied = false
    }

    init {
        if (isInitied) {
            // SpeechSynthesizer.getInstance() 不要连续调用
            throw RuntimeException(
                "MySynthesizer 对象里面 SpeechSynthesizer还未释放，请勿新建一个新对象。" +
                        "如果需要新建，请先调用之前MySynthesizer对象的release()方法。"
            )
        }
        Log.i("MySyntherizer", "MySyntherizer new called")
        this.context = context
        this.mainHandler = mainHandler
        isInitied = true
    }
}
