package com.example.android_voiceassistant

import android.Manifest.permission.RECORD_AUDIO
import android.content.Context
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
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
import com.baidu.aip.asrwakeup3.core.mini.ActivityMiniRecog
import com.baidu.aip.asrwakeup3.core.util.AuthUtil


class MainActivity : ActivityMiniRecog(), EventListener, View.OnClickListener {
    private val msgList = ArrayList<Msg>()
    private var adapter: MsgAdapter? = null
    private var asr: EventManager? = null
    private val KEY = "ab507bf7ad4c4b54961a09deb3ee8057"
    private val API = "http://openapi.turingapi.com/openapi/api/v2?key="+KEY+"&info="

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initMsg()
        initPermission()
        asr = EventManagerFactory.create(this, "asr")
        asr?.registerListener(this)

        val layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager
        adapter = MsgAdapter(msgList)
        recyclerView.adapter = adapter
        send.setOnClickListener(this)
        voice.setOnClickListener(this)
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

    private fun parseJSON(responseData: String) : String{
        val jsonData = JSONObject(responseData)
        val result = jsonData.getJSONArray("results")
        if(result != null) {
            return result.getJSONObject(result.length() - 1).getJSONObject("values").getString("text")
        }
        return ""

    }

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

