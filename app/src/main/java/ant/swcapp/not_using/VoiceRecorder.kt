/*
    현재 이 소스를 사용하고 있진 않습니다만 이 파일은

    구글 샘플 코드에서 구현되있던 VoiceRecorder.java 파일을
    Kotlin 파일로 옮겨놓은 것입니다

    통신을 어떻게 사용하여도
    라이브러리를 사용하지 않는 경우에
    VoiceRecorder 는 많은 부분 활용이 가능해 보입니다
 */

package ant.swcapp.not_using

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

import kotlin.math.absoluteValue

/**
 * VoiceRecorder
 * 음성을 인식하여 [VoiceRecorder.CallBack] 에 통보하는 클래스입니다.
 *
 * 사용할 액티비티에서 CallBack 객체를 적절히 정의하여 사용하면 됩니다.
 *
 * 녹음되는 음성의 포맷은 [AudioFormat.ENCODING_PCM_16BIT], [AudioFormat.CHANNEL_IN_MONO] 입니다.
 * [createAudioRecord] 에서 기기에 맞는 적절한 rate 를 선택합니다.
 * 선택된 rate 를 확인하려면 [sampleRate] 를 사용하세요.
 */
class VoiceRecorder(val mCallBack: CallBack){

    companion object: Any() {

        val SAMPLE_RATE_CANDIDATES = intArrayOf(16000, 11025, 22050, 44100)

        val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        val ENCODING = AudioFormat.ENCODING_PCM_16BIT

        val AMPLITUDE_THRESHOLD = 1500
        /** 몇 밀리초 동안 음성이 없을 시 자동종료 (1초 = 1000밀리초) */
        val SPEECH_TIMEOUT_MILLIS = 2000
        /** 음성인식 최대 밀리초 */
        val MAX_SPEECH_LENGTH_MILLIS = 30 * 1000

        abstract class CallBack {

            /**
             * Recorder 가 음성을 인식했을 때 호출됩니다
             */
            abstract fun onVoiceStart()

            /**
             * Recorder 가 음성을 인식중일 때 호출됩니다 (1회 이상)
             *
             * @param data [AudioFormat.ENCODING_PCM_16BIT] 형식의 오디오 데이터입니다
             * @param size 실제 데이터의 크기입니다
             */
            abstract fun onVoice(data: ByteArray, size: Int)

            /**
             * Recorder 가 음성인식을 종료했을 때 호출됩니다
             */
            abstract fun onVoiceEnd()
        }
    }

    /** 실제 녹음을 담당하는 안드로이드 지원 클래스 */
    private var mAudioRecord: AudioRecord? = null

    private var mThread: Thread? = null

    /** 오디오 데이터를 담는 버퍼 */
    private var mBuffer: ByteArray? = null

    /** 스레드 사용으로 인한 에러를 방지하는 객체 */
    private val mLock = Any()

    /** 마지막 음성을 들었던 시간 */
    private var mLastVoiceHeardMillis: Long = Long.MAX_VALUE

    /** 현재 음성이 시작한 시간; 초기값 신경쓸 필요 없음 */
    private var mVoiceStartMillis: Long = 0

    /**
     * 음성 녹음을 시작하는 함수.
     */
    public fun start(){
        // 현재 녹음중이라면 해당 녹음을 정지합니다.
        stop()
        // 새로운 녹음세션 생성
        mAudioRecord = createAudioRecord()
        if (mAudioRecord == null){
            throw RuntimeException("AudioRecord 객체 생성 실패")
        }
        // 녹음 시작
        mAudioRecord!!.startRecording()
        // 스레드를 할당하고 콜백을 활성화시킵니다.
        mThread = Thread(ProcessVoice())
        mThread!!.start()
    }

    /**
     * 음성 녹음을 중지하는 함수
     */
    public fun stop(){
        synchronized (mLock){
            mLastVoiceHeardMillis = Long.MAX_VALUE
            mCallBack.onVoiceEnd()

            mThread?.interrupt()
            mThread = null

            mAudioRecord?.stop()
            mAudioRecord?.release()
            mAudioRecord = null

            mBuffer = null
        }
    }

    /**
     * 새로운 [AudioRecord] 객체를 생성합니다.
     *
     * @return [AudioRecord] 객체. 오류시 null 반환.
     */
    private fun createAudioRecord(): AudioRecord?{
        for (sampleRate in SAMPLE_RATE_CANDIDATES) {
            val sizeInByte = AudioRecord.getMinBufferSize(sampleRate, CHANNEL, ENCODING)
            if (sizeInByte == AudioRecord.ERROR_BAD_VALUE) {
                continue
            }
            val audioRecord: AudioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate, CHANNEL, ENCODING, sizeInByte
            )
            if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                mBuffer = ByteArray(size = sizeInByte)
                return audioRecord
            } else {
                audioRecord.release()
            }
        }
        return null
    }

    /**
     * 쓰레드에서 오디오 데이터를 계속 확인하여 [mCallBack] 으로 이벤트를 전달합니다
     */
    private inner class ProcessVoice(): Runnable{

        override fun run() {
            while (true){
                synchronized (mLock){
                    if (Thread.currentThread().isInterrupted){
                        return@run
                    }
                    val size: Int = mAudioRecord!!.read(mBuffer, 0, mBuffer!!.size)
                    val now = System.currentTimeMillis()
                    if (isHearingVoice(mBuffer!!, size)){
                        if (mLastVoiceHeardMillis == Long.MAX_VALUE){
                            mVoiceStartMillis = now
                            mCallBack.onVoiceStart()
                        }
                        mCallBack.onVoice(mBuffer!!, size)
                        mLastVoiceHeardMillis = now
                        if (now - mVoiceStartMillis > MAX_SPEECH_LENGTH_MILLIS){
                            stop()
                        }
                    }else if(mLastVoiceHeardMillis != Long.MAX_VALUE){
                        mCallBack.onVoice(mBuffer!!, size)
                        if (now - mLastVoiceHeardMillis > SPEECH_TIMEOUT_MILLIS){
                            stop()
                        }
                    }
                }
            }
        }

        /**
         * 오디오에서 현재 음성이 있는지 확인
         */
        private fun isHearingVoice(buffer: ByteArray, size: Int): Boolean{
            for (i in 0 until (size - 1) step 2){
                var s = buffer[i + 1].toInt()
                if (s < 0 ) s *= -1
                s = s shl 8
                s += buffer[i].toInt().absoluteValue
                if (s > AMPLITUDE_THRESHOLD){
                    return true
                }
            }
            return false
        }
    }

    /**
     * 현재 사용 중인 rate 반환
     *
     * @return 현재 사용 중인 rate, Recorder 없을 시 0 반환
     */
    public val sampleRate: Int
        get() = mAudioRecord?.sampleRate ?: 0
}


