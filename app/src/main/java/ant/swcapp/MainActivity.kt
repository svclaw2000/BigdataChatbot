package ant.swcapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.view.*
import java.lang.Exception
import android.os.Handler
import android.os.Message


private const val PROCESS_LOG = "PROCESS LOG"


class MainActivity : AppCompatActivity() {

    companion object{
        // requestPermission() 에 사용되어 callBack 함수인 onRequestPermission() 에서
        // 어떤 권한에 대한 onRequestPermission 인지 식별하기 위한 식별자인데
        // onRequestPermission 을 사용안해서 큰 의미는 없는 상수입니다
        const val REQUEST_RECORD_AUDIO_PERMISSION = 430

        // 애니메이션 설정에 사용하는 상수입니다
        const val ANIMATION_IDLE: Int = 1
        const val ANIMATION_LISTEN: Int = 2
        const val ANIMATION_ALARM: Int = 3
        const val ANIMATION_BYE: Int = 4
    }

    // SpeechRecognizer 초기화용 인텐트
    lateinit var i:Intent

    // 녹음 버튼
    lateinit var recButton: ImageButton

    // STT 오브젝트 생성
    lateinit var mRecognizer: SpeechRecognizer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // SpeechRecognizer 설정에 필요한 정보를 담는 인텐트
        i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, "ant.swcapp")
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)

        // SpeechRecognizer 초기화
        mRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        mRecognizer.setRecognitionListener(listener)


        // recButton 설정과 리스너 등록
        recButton = findViewById(R.id.recordingButton)
        recButton.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View) {
                Log.d("rec Init", "mRecognizer 이 실행됩니다")
                mRecognizer.startListening(i) // STT 실행
                announcerTextView.text = getString(R.string.announcer_listen)
            }
        })
    }
        /*
        아나운서 클래스입니다
        애니메이션 부분과 발화(STT) 부분으로 나누어 볼 수 있겠습니다.
        생성 과정에서 애니메이션이 실행되게 하였습니다.
         */
        internal class Announcer: ImageView{
            constructor(context: Context): super(context)
            constructor(context: Context, attrs: AttributeSet?): super(context, attrs)
            constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int): super(context, attrs, defStyleAttr)

            init{
                setIdle() // 초기 애니메이션: 기본
                process() // 애니메이션 실행
            }

            // 애니메이션 이미지 변경을 담당하는 핸들러
            private val announcerHandler= object: Handler(){
                override fun handleMessage(msg: Message){
                    setImageResource(frameList[nowFrame])
                }
            }

            // 애니매이션용 변수
            private lateinit var frameList:Array<Int> // 이미지를 저장한 배열
            private lateinit var frameDelay:Array<Long> // 애니메이션에 해당하는 딜레이를 저장하는 배열
            private var isLoop: Boolean = true // 애니메이션을 반복할 것인지, 아닌지
            private var nextAnimation = ANIMATION_IDLE // 다음 애니메이션을 가리키는 값
            private var nowFrame: Int = 0 // 현재 몇 번째 프레임인지?
            private var nowDelay: Long = 0L // 현재 딜레이는 얼마나 되는지?


            // 스레드 생성 후 애니메이션을 실행하는 함수
            private fun process() {
                Thread(Runnable {
                    var preTime: Long = 0 // 과거 이 사진이 언제 변경되었는지? (nowTime 과 비교하여 Delay 만큼 경과했는지 비교하기 위해 사용)
                    var nowTime: Long // 현재 시간
                    Log.i(PROCESS_LOG, "INIT")

                    while (true) {
                        nowTime = System.currentTimeMillis()
                        Log.i(PROCESS_LOG, "preTime: $preTime, nowTime: $nowTime, nowTime - preTime: ${nowTime - preTime}, nowDelay: $nowDelay")
                        // 지정 딜레이만큼 시간이 지났으면 if 문 실행, 아니면 nowTime 만을 갱신하며 아무것도 하지 않는다
                        if (nowTime - preTime  > nowDelay) { // 딜레이만큼 시간 경과
                            Log.i(PROCESS_LOG, "nowTime - preTime > nowDelay")
                            if (nowFrame == frameList.size - 1) { // 지금 이미지가 이 애니메이션의 마지막 이미지라면
                                if (isLoop) { // 만약 이 애니메이션을 반복했다고 설정했다면, Frame 을 0 (처음 값) 으로 되돌려 반복시킨다
                                    nowFrame = 0
                                } else { // 반복이 설정되어 있지 않다면, nextAnimation 값에 따라 다음 애니메이션을 실행시킨다
                                    when (nextAnimation) {
                                        ANIMATION_IDLE -> setIdle()
                                        ANIMATION_LISTEN -> setListen()
                                        ANIMATION_ALARM -> setAlarm()
                                        ANIMATION_BYE -> setBye()
                                        else -> {
                                        }
                                    }
                                }
                            } else { // 이 프레임이 애니메이션의 마지막이 아니라면 ( 수행할 프레임이 더 남아있다면) 프레임 값 증가
                                Log.i(PROCESS_LOG, "BEFORE nowFrame $nowFrame")
                                nowFrame++
                                Log.i(PROCESS_LOG, "AFTER nowFrame $nowFrame")
                            }
                            // 프레임이 변경되었으므로 이후 preTime 재설정, Handler 를 통해 이미지 변경 수행
                            nowDelay = frameDelay[nowFrame]
                            preTime = System.currentTimeMillis()
                            Log.i(PROCESS_LOG, "nowFrame BEFORE setImageResource $nowFrame")
                            val msg:Message = announcerHandler.obtainMessage()
                            announcerHandler.sendMessage(msg)
                        }
                    }
                }).start()
            }

            // Announcer 의 상태를 Idle 로 설정
            public fun setIdle(){
                frameList = arrayOf(R.drawable.announcer_idle1, R.drawable.announcer_idle2,
                                     R.drawable.announcer_idle1, R.drawable.announcer_idle2)
                frameDelay = arrayOf(2000L, 200L, 3000L, 200L)

                nowFrame = 0
                nowDelay = frameDelay[nowFrame]

                isLoop = true
            }

            // Announcer 의 상태를 Listen 으로 설정
            public fun setListen(){
                frameList = arrayOf(R.drawable.announcer_smile1, R.drawable.announcer_idle2,
                                      R.drawable.announcer_smile1, R.drawable.announcer_idle2)
                frameDelay = arrayOf(2000L, 200L, 3000L, 200L)

                nowFrame = 0
                nowDelay = frameDelay[nowFrame]

                isLoop = true

            }

            // Announcer 의 상태를 Alarm 으로 설정
            public fun setAlarm(){
                frameList = arrayOf(R.drawable.announcer_alarm1, R.drawable.announcer_idle2,
                    R.drawable.announcer_alarm1, R.drawable.announcer_idle2)
                frameDelay = arrayOf(2000L, 200L, 3000L, 200L)

                nowFrame = 0
                nowDelay = frameDelay[nowFrame]

                isLoop = false

            }

            // Announcer 의 상태를 Interested 로 설정
            public fun setInterested(){
                frameList = arrayOf(R.drawable.announcer_interested1, R.drawable.announcer_idle2,
                    R.drawable.announcer_interested1, R.drawable.announcer_idle2)
                frameDelay = arrayOf(2000L, 200L, 3000L, 200L)

                nowFrame = 0
                nowDelay = frameDelay[nowFrame]

                isLoop = false
            }

            private fun setBye(){
            }

            // 발화 (TTS) 를 위한 변수
            private var mediaPlayer: MediaPlayer? = null // 플레이어

            // 발화 (TTS) 함수
            // msg:String (in java; String msg) 를 넣어서 실행하면 된다
            public fun talk(msg: String){
                if (mediaPlayer != null){
                    mediaPlayer!!.stop()
                    mediaPlayer = null
                    }
                mediaPlayer = MediaPlayer()
                mediaPlayer!!.apply{
                    setAudioAttributes(
                        AudioAttributes.Builder().
                                setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    setDataSource("http://114.71.220.20:8882/get_text?text=$msg")
                    prepare()
                    start()
                }
            }
        }



    override fun onStart() {
        super.onStart()
        // if(음성 녹음 권한이 없다면)
        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // 권한을 요청합니다
            ActivityCompat.requestPermissions(
                this@MainActivity,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO_PERMISSION
            )
        }
    }

    // SpeechRecognizer 가 음성을 인식하는 과정에서 호출되는 함수들을 가진 객체입니다.
    // SpeechRecognizer 생성 시 (mRecognizer 객체 생성 시) 사용했습니다.
    private var listener:RecognitionListener = object: RecognitionListener{


        // 버튼 눌렀을 때
        override fun onBeginningOfSpeech() {
            announcerTextView.text = getString(R.string.announcer_listen)
            sttTextView.text = null
            val announcer = announcer as MainActivity.Announcer
            announcer.apply{
                setListen() // 아나운서 상태 Listen 으로 변경
            }
        }

        // 중간 결과를 받아왔을 때
        override fun onPartialResults(partialResults: Bundle?) {
            var key: String = SpeechRecognizer.RESULTS_RECOGNITION
            val mResult: ArrayList<String>? = partialResults?.getStringArrayList(key)
            if (mResult != null) {sttTextView.text = "" + mResult[0]} // sttTextView 창에 표현

        }

        // 최종 결과를 받아왔을때
        override fun onResults(results: Bundle?) {
            var key: String = SpeechRecognizer.RESULTS_RECOGNITION
            val mResult: ArrayList<String>? = results?.getStringArrayList(key) ?: throw Exception("No Result Found")
            sttTextView.text = "" + mResult!![0] // sttTextView 창에 표현
            announcerTextView.text = getString(R.string.announcer_really)
            val announcer = announcer as MainActivity.Announcer
            announcer.apply{
                setInterested() // 아나운서 상태 Interested 로 변경
                talk(getString(R.string.announcer_really))
            }

            // 참고했습니다: https//dsnight.tistory.com/15
        }

        override fun onEvent(eventType: Int, params: Bundle?) {

        }

        override fun onEndOfSpeech() {
        }

        // 적절하게 인식되지 않았을 때
        override fun onError(error: Int) {
            announcerTextView.text = getString(R.string.announcer_error)
            val announcer = announcer as MainActivity.Announcer
            announcer.apply{
                setAlarm() // 아나운서 상태 Alarm 으로 변경
            }
        }

        override fun onBufferReceived(buffer: ByteArray?) {
        }

        override fun onReadyForSpeech(params: Bundle?) {
        }

        override fun onRmsChanged(rmsdB: Float) {
        }
    }



}

