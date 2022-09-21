package com.example.android_voiceassistant

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.*
import org.json.JSONObject
import org.json.JSONException
import java.io.IOException
import com.baidu.speech.EventListener
import com.baidu.speech.EventManager
import com.baidu.speech.EventManagerFactory
import com.baidu.speech.asr.SpeechConstant
//import java.util.jar.Manifest
import android.Manifest
import android.os.Handler
import android.os.Message
import android.widget.TextView
import com.baidu.aip.asrwakeup3.core.mini.ActivityMiniRecog
import com.baidu.aip.asrwakeup3.core.util.AuthUtil
import com.baidu.tts.chainofresponsibility.logger.LoggerProxy
import com.baidu.tts.client.SpeechSynthesizer
import com.baidu.tts.client.SpeechSynthesizerListener
import com.baidu.tts.client.TtsMode
import com.example.android_voiceassistant.control.MySyntherizer
import com.example.android_voiceassistant.control.InitConfig
import com.example.android_voiceassistant.listener.UiMessageListener
import com.example.android_voiceassistant.util.Auth
import com.example.android_voiceassistant.util.AutoCheck
import com.example.android_voiceassistant.util.IOfflineResourceConst
import kotlin.concurrent.thread


class MainActivity : ActivityMiniRecog(), EventListener, View.OnClickListener,
    IOfflineResourceConst {
    //--------------------------------
    protected var appId: String? = null
    protected var appKey: String? = null
    protected var secretKey: String? = null
    protected var sn // 纯离线合成SDK授权码；离在线合成SDK没有此参数
            : String? = null
    //TtsMode.ONLINE 纯在线
    private val ttsMode = TtsMode.ONLINE
    private val isOnlineSDK = TtsMode.ONLINE == IOfflineResourceConst.DEFAULT_SDK_TTS_MODE

    // ===============初始化参数设置完毕，更多合成参数请至getParams()方法中设置 =================
    protected var mSpeechSynthesizer: SpeechSynthesizer? = null
    // =========== 以下为UI部分 ==================================================
    private var mShowText: TextView? = null
    protected var mainHandler: Handler? = null
    private var desc // 说明文件
            : String? = null
    //--------------------------------

    private val msgList = ArrayList<Msg>()
    private var adapter: MsgAdapter? = null
    private var asr: EventManager? = null
    private val KEY = "ab507bf7ad4c4b54961a09deb3ee8057"
    private val API = "http://openapi.turingapi.com/openapi/api/v2?key="+KEY+"&info="
    private var mySyntherizer : MySyntherizer? = null
    private val config : InitConfig? = null
    private var params: Map<String, String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 以下是语音识别相关
        asr = EventManagerFactory.create(this, "asr")
        asr?.registerListener(this)

        //以下是语音合成相关
        appId = Auth.getInstance(this).appId
        appKey = Auth.getInstance(this).appKey
        secretKey = Auth.getInstance(this).secretKey
        sn = Auth.getInstance(this).sn // 纯离线合成必须有此参数；离在线合成SDK没有此参数

        //val listener = UiMessageListener(mainHandler)
        //var config = InitConfig(appId, appKey, secretKey, TtsMode.ONLINE, this.params, listener)

        // 以下是recyclerView布局适配器相关
        val layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager
        adapter = MsgAdapter(msgList)
        recyclerView.adapter = adapter
        send.setOnClickListener(this)
        voice.setOnClickListener(this)

        initMsg()
        initPermission()
        initTTs()

    }
    //----------------------------------------------------------------------------
    private fun speak(text: String) {
        /* 以下参数每次合成时都可以修改
         *  mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_SPEAKER, "0");
         *  设置在线发声音人： 0 普通女声（默认） 1 普通男声  3 情感男声<度逍遥> 4 情感儿童声<度丫丫>
         *  mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_VOLUME, "5"); 设置合成的音量，0-15 ，默认 5
         *  mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_SPEED, "5"); 设置合成的语速，0-15 ，默认 5
         *  mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_PITCH, "5"); 设置合成的语调，0-15 ，默认 5
         *
         */
        if (mSpeechSynthesizer == null) {
            print("[ERROR], 初始化失败")
            return
        }
        val result : Int?= mSpeechSynthesizer?.speak(text)
        if (result != null) {
            checkResult(result, "speak")
        }
    }

    /**
     * 注意此处为了说明流程，故意在UI线程中调用。
     * 实际集成中，该方法一定在新线程中调用，并且该线程不能结束。具体可以参考NonBlockSyntherizer的写法
     */
    private fun initTTs() {
        LoggerProxy.printable(true) // 日志打印在logcat中
        val isSuccess: Boolean
        /*
        if (!isOnlineSDK) {
            // 检查2个离线资源是否可读
            isSuccess = checkOfflineResources()
            if (!isSuccess) {
                return
            } else {
                print("离线资源存在并且可读, 目录：" + OnlineActivity.TEMP_DIR)
            }
        }

         */
        // 日志更新在UI中，可以换成MessageListener，在logcat中查看日志
        val listener: SpeechSynthesizerListener = UiMessageListener(mainHandler)

        // 1. 获取实例
        mSpeechSynthesizer = SpeechSynthesizer.getInstance()
        mSpeechSynthesizer?.setContext(this)

        // 2. 设置listener
        mSpeechSynthesizer?.setSpeechSynthesizerListener(listener)

        // 3. 设置appId，appKey.secretKey
        var result = mSpeechSynthesizer?.setAppId(appId)
        if (result != null) {
            checkResult(result, "setAppId")
        }
        result = mSpeechSynthesizer?.setApiKey(appKey, secretKey)
        if (result != null) {
            checkResult(result, "setApiKey")
        }

        // 4. 如果是纯离线SDK需要离线功能的话
        /*
        if (!isOnlineSDK) {
            // 文本模型文件路径 (离线引擎使用)， 注意TEXT_FILENAME必须存在并且可读
            mSpeechSynthesizer?.setParam(SpeechSynthesizer.PARAM_TTS_TEXT_MODEL_FILE,
                OnlineActivity.TEXT_FILENAME
            )
            // 声学模型文件路径 (离线引擎使用)， 注意TEXT_FILENAME必须存在并且可读
            mSpeechSynthesizer?.setParam(
                SpeechSynthesizer.PARAM_TTS_SPEECH_MODEL_FILE,
                OnlineActivity.MODEL_FILENAME
            )
            mSpeechSynthesizer?.setParam(
                SpeechSynthesizer.PARAM_MIX_MODE,
                SpeechSynthesizer.MIX_MODE_DEFAULT
            )
            // 该参数设置为TtsMode.MIX生效。
            // MIX_MODE_DEFAULT 默认 ，wifi状态下使用在线，非wifi离线。在线状态下，请求超时6s自动转离线
            // MIX_MODE_HIGH_SPEED_SYNTHESIZE_WIFI wifi状态下使用在线，非wifi离线。在线状态下， 请求超时1.2s自动转离线
            // MIX_MODE_HIGH_SPEED_NETWORK ， 3G 4G wifi状态下使用在线，其它状态离线。在线状态下，请求超时1.2s自动转离线
            // MIX_MODE_HIGH_SPEED_SYNTHESIZE, 2G 3G 4G wifi状态下使用在线，其它状态离线。在线状态下，请求超时1.2s自动转离线
        }

         */

        // 5. 以下setParam 参数选填。不填写则默认值生效
        // 设置在线发声音人： 0 普通女声（默认） 1 普通男声  3 情感男声<度逍遥> 4 情感儿童声<度丫丫>
        mSpeechSynthesizer?.setParam(SpeechSynthesizer.PARAM_SPEAKER, "0")
        // 设置合成的音量，0-15 ，默认 5
        mSpeechSynthesizer?.setParam(SpeechSynthesizer.PARAM_VOLUME, "9")
        // 设置合成的语速，0-15 ，默认 5
        mSpeechSynthesizer?.setParam(SpeechSynthesizer.PARAM_SPEED, "5")
        // 设置合成的语调，0-15 ，默认 5
        mSpeechSynthesizer?.setParam(SpeechSynthesizer.PARAM_PITCH, "5")

        // mSpeechSynthesizer.setAudioStreamType(AudioManager.MODE_IN_CALL); // 调整音频输出
        if (sn != null) {
            // 纯离线sdk这个参数必填；离在线sdk没有此参数
            mSpeechSynthesizer?.setParam(IOfflineResourceConst.PARAM_SN_NAME, sn)
        }

        // x. 额外 ： 自动so文件是否复制正确及上面设置的参数
        val params: MutableMap<String, String> = HashMap()
        // 复制下上面的 mSpeechSynthesizer.setParam参数
        // 上线时请删除AutoCheck的调用
        /*
        if (!isOnlineSDK) {
            params[SpeechSynthesizer.PARAM_TTS_TEXT_MODEL_FILE] = OnlineActivity.TEXT_FILENAME
            params[SpeechSynthesizer.PARAM_TTS_SPEECH_MODEL_FILE] = OnlineActivity.MODEL_FILENAME
        }

         */

        // 检测参数，通过一次后可以去除，出问题再打开debug
        val initConfig = InitConfig(appId, appKey, secretKey, ttsMode, params, listener)
        AutoCheck.getInstance(applicationContext).check(initConfig, object : Handler() {
            /**
             * 开新线程检查，成功后回调
             */
            override fun handleMessage(msg: Message) {
                if (msg.what == 100) {
                    val autoCheck: AutoCheck = msg.obj as AutoCheck
                    synchronized(autoCheck) {
                        val message: String = autoCheck.obtainDebugMessage()
                        print(message) // 可以用下面一行替代，在logcat中查看代码
                    }
                }
            }
        })

        // 6. 初始化
        result = mSpeechSynthesizer?.initTts(ttsMode)
        if (result != null) {
            checkResult(result, "initTts")
        }
    }

    private fun checkResult(result: Int, method: String) {
        if (result != 0) {
            print("error code :$result method:$method")
        }
    }


    //----------------------------------------------------------------------------

    override fun onDestroy() {
        if (mSpeechSynthesizer != null) {
            mSpeechSynthesizer!!.stop()
            mSpeechSynthesizer!!.release()
            mSpeechSynthesizer = null
            print("释放资源成功")
        }
        super.onDestroy()
    }

    /**
     * 基于SDK集成2.2 发送开始事件
     * 点击开始按钮
     */
    private fun start(){
        val params = AuthUtil.getParam()
        params[SpeechConstant.ACCEPT_AUDIO_VOLUME] = false
        // 基于SDK集成2.1 设置识别参数
        var json: String? = null // 可以替换成自己的json
        json = JSONObject(params).toString(); // 这里可以替换成你需要测试的json
        // 基于SDK集成2.2 发送start开始事件
        asr?.send(SpeechConstant.ASR_START, json, null, 0, 0);

    }

    /*
     *基于sdk集成1.2 自定义输出事件类
     *EventListener 回调方法
     */
    override fun onEvent(name: String?, params: String?, data: ByteArray?, offset: Int, length: Int) {
        if (SpeechConstant.CALLBACK_EVENT_ASR_PARTIAL == name){
            // 识别相关的结果都在这里CALLBACK_EVENT_ASR_PARTIAL
            try {
                // 结果类型result_type(临时结果partial_result，最终结果final_result)
                // best_result最佳结果参数
                //在2.2熟悉源码的2.2.1设置识别/唤醒输入参数中可查看原始Json
                val jsonObject = JSONObject(params)
                val result = jsonObject.getString("best_result");
                val resultType = jsonObject.getString("result_type");
                // 判断识别是否结束
                if ("final_result" == resultType){
                    inputText.setText(result)
                    Toast.makeText(applicationContext,"识别已结束",Toast.LENGTH_SHORT).show()
                }
            } catch (e : JSONException) {
                e.printStackTrace()
            }
        }
    }


    override fun onClick(v: View?) {
        when (v) {
            send -> {
                val content = inputText.text.toString()
                if (content.isNotEmpty()) {
                    val msg = Msg(content, Msg.TYPE_SENT)
                    msgList.add(msg)
                    //msgList.add(Msg("哈哈哈", 0))
                    val ask = getJson(content)
                    HttpUtil.sendOkHttpRequest(API+content, ask, object : Callback {
                        override fun onResponse(call: Call, response: Response){
                            val responseData = response.body?.string()
                            if(responseData != null){
                                val text = parseJSON(responseData)
                                // 此处为根据返回text，进行语音合成逻辑
                                speak(text)

                                msgList.add(Msg(text, 0))
                                runOnUiThread {
                                    adapter?.notifyItemInserted(msgList.size - 1) // 当有新消息时， 刷新RecyclerView中的显示
                                    recyclerView.scrollToPosition(msgList.size - 1) // 将RecyclerView 定位到最后一行
                                }

                            }
                        }
                        override fun onFailure(call: Call, e: IOException){
                            Log.d("longpo", "请求失败")
                        }
                    })
                    adapter?.notifyItemInserted(msgList.size - 1) // 当有新消息时， 刷新RecyclerView中的显示
                    recyclerView.scrollToPosition(msgList.size - 1) // 将RecyclerView 定位到最后一行
                    inputText.setText("") // 清空输入框中的内容
                }
            }
            voice -> {
                Toast.makeText(applicationContext,"开始识别",Toast.LENGTH_LONG).show()
                start()
            }
        }
    }

    // 解析传回来的JSON字符串
    private fun parseJSON(responseData: String) : String{
        val jsonData = JSONObject(responseData)
        val result = jsonData.getJSONArray("results")
        if(result != null) {
            return result.getJSONObject(result.length() - 1).getJSONObject("values").getString("text")
        }
        return ""

    }
    // 发送字符串
    private fun getJson(content: String) : String{
        return "{\n"+
                "\"reqType\":0,\n"+
                "\"perception\": {\n"+
                "\"inputText\": {"+
                "   \"text\":\" "+content+"\"\n" +
                "},\n"+
                "},\n"+
                "\"userInfo\": {"+
                "\"apiKey\": \"ab507bf7ad4c4b54961a09deb3ee8057\",\n"+
                "\"userId\": \"779500\"\n"+
                "   }\n"+
                "}"

    }




    private fun initMsg() {
        val msg1 = Msg("晚上去哪里吃？", Msg.TYPE_RECEIVED)
        msgList.add(msg1)
        val msg2 = Msg("你说吧！", Msg.TYPE_SENT)
        msgList.add(msg2)
    }




    // 以下3个函数作用：点击editText之外的会让软键盘收起来
    override fun dispatchTouchEvent(motionEvent: MotionEvent): Boolean {
        if (motionEvent.action == MotionEvent.ACTION_DOWN) {
            // 获得当前得到焦点的View，一般情况下就是EditText（特殊情况就是轨迹求或者实体案件会移动焦点）
            val v = currentFocus
            if (isShouldHideInput(v, motionEvent)) {
                hideSoftInput(v!!.windowToken)
            }
        }
        return super.dispatchTouchEvent(motionEvent)
    }
    private fun isShouldHideInput(v: View?, event: MotionEvent): Boolean {
        if (v != null && v is EditText) {
            val l = intArrayOf(0, 0)
            v.getLocationInWindow(l)
            val left = l[0]
            val top = l[1]
            val bottom = top + v.getHeight()
            val right = (left
                    + v.getWidth())
            return !(event.x > left && event.x < right && event.y > top && event.y < bottom)
        }
        /**如果焦点不是EditText就忽略掉,
         * 因为这个发生在视图刚绘制完,
         * 第一个焦点不在EditView上，和用户用轨迹球选择其他的焦点
         */
        return false
    }
    private fun hideSoftInput(token: IBinder?) {
        if (token != null) {
            val im: InputMethodManager =
                getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            im.hideSoftInputFromWindow(
                token,
                InputMethodManager.HIDE_NOT_ALWAYS
            )
        }
    }


    /**
     * android 6.0 以上需要动态申请权限
     * (以下可直接从ActivityMiniRecog类中复制)
     */
    private fun initPermission() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.INTERNET,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.WRITE_SETTINGS,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        val toApplyList = ArrayList<String>();

        for (perm in permissions) {
            if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(this, perm)) {
                toApplyList.add(perm);
                // 进入到这里代表没有权限.

            }
        }
        val tmpList: Array<String?> = arrayOfNulls<String>(toApplyList.size)
        if (toApplyList.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, toApplyList.toArray(tmpList), 123);
        }
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // 此处为android 6.0以上动态授权的回调，用户自行实现。
    }



}

