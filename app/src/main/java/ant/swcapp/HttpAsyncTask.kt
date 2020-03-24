package ant.swcapp

import android.content.Context
import android.os.AsyncTask
import java.io.*
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import javax.net.ssl.*

class HttpAsyncTask(val context: Context): AsyncTask<String, Void, String>() {
    companion object {
        const val RESULT_ERROR = "Did not work!"

        fun trustAllHosts() {
            val trustAllCerts = arrayOf( object : X509TrustManager {
                override fun checkClientTrusted(
                    chain: Array<out java.security.cert.X509Certificate>?,
                    authType: String?
                ) { }

                override fun checkServerTrusted(
                    chain: Array<out java.security.cert.X509Certificate>?,
                    authType: String?
                ) { }

                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> {
                    return emptyArray()
                }
            })

            val NullHostNameVerifier = HostnameVerifier {_, _ ->
                true
            }

            try {
                HttpsURLConnection.setDefaultHostnameVerifier(NullHostNameVerifier)
                val sc = SSLContext.getInstance("TLS")
                sc.init(null, trustAllCerts, SecureRandom())
                HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val URL_DIALOG = "https://114.71.220.20:8001/bytecelldialog"

    override fun doInBackground(vararg params: String?): String {
        if (params[0].equals(Utils.POST_DIALOG))
            return POST_DIALOG(params[1] ?: "")
        else
            return RESULT_ERROR
    }

    fun POST_DIALOG(sMsg: String): String {
        var ret = RESULT_ERROR
        val httpsCon: HttpsURLConnection
        var httpCon: HttpURLConnection? = null

        try {
            trustAllHosts()

            val inputStream: InputStream
            val urlCon = URL(URL_DIALOG)
            httpsCon = urlCon.openConnection() as HttpsURLConnection
            httpCon = httpsCon

            httpCon.requestMethod = "POST"
            httpCon.setRequestProperty("Content-type", "application/json")
            httpCon.setRequestProperty("User-Agent", "Mozilla/5.0 ( compatible ) ")
            httpCon.setRequestProperty("Accept", "*/*")
            httpCon.doInput = true
            httpCon.doOutput = true

            val os = httpCon.outputStream
            os.write(sMsg.toByteArray())
            os.flush()

            val status = httpCon.responseCode
            try {
                if (status != HttpURLConnection.HTTP_OK) inputStream = httpCon.errorStream
                else inputStream = httpCon.inputStream

                if (inputStream != null)
                    ret = convertInputStreamToString(inputStream)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            httpCon?.disconnect()
        }

        return ret
    }

    @Throws(IOException::class)
    fun convertInputStreamToString(inputStream: InputStream): String {
        val bufferedReader = BufferedReader(InputStreamReader(inputStream))
        var ret = ""

        var line = bufferedReader.readLine()
        while (line != null) {
            ret += line
            line = bufferedReader.readLine()
        }
        inputStream.close()
        return ret
    }
}