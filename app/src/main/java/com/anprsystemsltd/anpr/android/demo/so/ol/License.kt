package com.anprsystemsltd.anpr.android.demo.so.ol

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.lifecycle.MutableLiveData
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*

class License() {

    companion object {
        private const val LICENSE_SERVER_URL = "http://anprlicense.eu/android/android_get_licens.php"
    }

    val checkLicenseLiveData = MutableLiveData<Boolean?>()

    private var deviceId = ""

    private lateinit var licenceFilePath: String

    fun getDeviceId() = deviceId

    fun processLicense(context: Context) {
        licenceFilePath = context.filesDir.absolutePath + "/anprlicense.txt"
        deviceId = readDeviceId(context)
        if (checkDownloadedLicense()) {
            checkLicenseLiveData.value = true
        } else {
            downloadLicense()
        }
    }

    private fun checkDownloadedLicense() : Boolean {
        try {
            val file = File(licenceFilePath)
            if (file.exists()) {
                FileInputStream(file).use {
                    val content = it.readBytes().toString(Charset.defaultCharset())
                    val licensInfoStart = content.indexOf("(licens)") + 8
                    val licensInfoEnd = content.indexOf("(/licens)")
                    val licensInfo = content.substring(licensInfoStart, licensInfoEnd)
                    val infoContent = licensInfo.split("#")
                    val devId = infoContent[0]
                    val expiredDate = infoContent[1]
                    val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    return devId == deviceId && expiredDate.compareTo(currentDate) >= 0
                }
            }
        } catch (e: Exception) {}
        return false
    }

    private fun downloadLicense() {
        Thread(Runnable() {
            try {
                val connection = URL(LICENSE_SERVER_URL + "?imei=" + deviceId).openConnection() as HttpURLConnection
                val content = connection.inputStream.bufferedReader().readText()
                val file = File(licenceFilePath)
                FileOutputStream(file).use { fos ->
                    fos.writer().use {
                        val licensStart = content.indexOf("(licens)")
                        val licensEnd = content.indexOf("(/licens)") + 9
                        val licensData = content.substring(licensStart, licensEnd)
                        it.write(licensData)
                    }
                }
            } catch (e: Exception) { }
            checkLicenseLiveData.postValue(checkDownloadedLicense())
        }).start()
    }

    private fun readDeviceId(context: Context) : String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            readAndroidId(context)
        } else {
            readImei(context) ?: readAndroidId(context)
        }

    @SuppressLint("HardwareIds")
    private fun readAndroidId(context: Context) =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

    @SuppressLint("MissingPermission", "HardwareIds")
    private fun readImei(context: Context) : String? = try {
        (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).deviceId
    } catch (e: Exception) {
        null
    }

}