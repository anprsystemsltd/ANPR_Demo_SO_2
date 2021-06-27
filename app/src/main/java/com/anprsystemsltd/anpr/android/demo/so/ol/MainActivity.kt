package com.anprsystemsltd.anpr.android.demo.so.ol

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.Camera
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.SurfaceHolder
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.anprsystemsltd.anpr.android.demo.so.ol.decoder.AnprLibraryOutputDecoderBase
import com.anprsystemsltd.anpr.android.demo.so.ol.decoder.AnprLibraryOutputDecoder_32
import com.anprsystemsltd.anpr.android.demo.so.ol.decoder.AnprLibraryOutputDecoder_64
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.FileOutputStream
import java.lang.StringBuilder
import kotlin.system.exitProcess

@SuppressWarnings("deprecation")
class MainActivity : AppCompatActivity(), SurfaceHolder.Callback, Camera.PreviewCallback {

    companion object {
        private const val PERMISSION_REQUEST = 1
        private const val CLASS_PATH = "com/anprsystemsltd/anpr/android/demo/so/ol/MainActivity;;"   // your activity package
        private const val LIBRARY_NAME = "_NOR_Norway" // if the ANPR so library file name is '_NOR_Norway.so'; lib prefix is necessary!

        private val permissions = listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CAMERA
        )

    }

    private lateinit var anprOutputDecoder: AnprLibraryOutputDecoderBase

    private val license = License()

    private var camera: Camera? = null
    private var cameraPreviewWidth: Int = 0
    private var cameraPreviewHeight: Int = 0
    private var cameraPreviewFormat: Int = 0

    private var libraryInitialized = false
    private var libraryBusy = false

    private var lastRecognizedString = ""
    private var recognizingConunter = 0
    private val results = mutableListOf<Pair<String, Long>>()


    private fun initializeActivity() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (!checkPermissions()) {
            askPermissions()
            return
        }

        if ("armeabi-v7a".equals(Build.CPU_ABI)) {
            anprOutputDecoder = AnprLibraryOutputDecoder_32()
        } else if ("arm64-v8a".equals(Build.CPU_ABI)) {
            anprOutputDecoder = AnprLibraryOutputDecoder_64()
        } else {
            error("Unknown CPU architect!")
            return
        }

        if (!writeClassPathFile()) {
            error("Failed to write class info to SD card!")
            return
        }

        license.checkLicenseLiveData.observe(this, Observer {
            title = "Device ID:" + license.getDeviceId()
            if (it == false) {
                error("Failed to download license from server!", false)
            }
            loadAnprSoLibrary()
        })

        license.processLicense(this);

        setContentView(R.layout.activity_main)
        surfaceCamera.holder.also {
            it.addCallback(this)
            it.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
        }
    }

    private fun loadAnprSoLibrary() {
        System.loadLibrary(LIBRARY_NAME)
        InitCaller(2)
        libraryInitialized = true
    }

    private fun processCameraFrame(data: ByteArray) {
        if (!libraryInitialized || libraryBusy) return
        libraryBusy = true

        val imageData = data.copyOfRange(0, cameraPreviewWidth * cameraPreviewHeight)
        val anprOut = ProcessImageCaller(
            imageData,
            cameraPreviewWidth,
            cameraPreviewHeight,
            1,
            1,
            0,
            1,
            1)
        anprOutputDecoder.refreshFromBuffer(anprOut)

        if (!anprOutputDecoder.isValid) {
            camera?.stopPreview()
            error("Invalid data from ANPPR library!")
        }

        if (anprOutputDecoder.numberOfChars > 0) {
            val recognizedString = charsToString(anprOutputDecoder.charBuffer, anprOutputDecoder.numberOfChars)
            if (recognizedString == lastRecognizedString) {
                recognizingConunter++
                if (recognizingConunter >= 2) {
                    val result = recognizedString + " - " + charsToString(anprOutputDecoder.syntaxName)
                    val now = System.currentTimeMillis()
                    results.removeAll { it.second < System.currentTimeMillis() }
                    results.find { it.first == result }?:let {
                        results.add(Pair(result, now + 5000))
                        saveFrameImage(data, result + "_" + now.toString() + ".jpg")
                        title = result
                    }
                    lastRecognizedString = ""
                }
            } else {
                lastRecognizedString = recognizedString
                recognizingConunter = 0
            }
        }

        libraryBusy = false
    }

    private fun saveFrameImage(imageData: ByteArray, name: String) {
        try {
            val yImage = YuvImage(imageData, cameraPreviewFormat, cameraPreviewWidth, cameraPreviewHeight, null)
            getExternalFilesDir(null)?.let {
                val file = File(it, name)
                if (!file.exists()) file.createNewFile()
                FileOutputStream(file).use {
                    yImage.compressToJpeg(Rect(0, 0, yImage.width, yImage.height), 100, it)
                }
            }
        } catch (e: Exception) { }
    }

    private fun initializeCamera(holder: SurfaceHolder?) {
        var success = false
        try {
            camera = Camera.open()
            camera?.let { cam ->
                cam.parameters.let { par ->
                    val cameraResolution = par.supportedPreviewSizes.find {
                        it.width == 640
                    }
                    cameraResolution?.let {
                        cameraPreviewWidth = cameraResolution.width
                        cameraPreviewHeight = cameraResolution.height
                        cameraPreviewFormat = par.previewFormat
                        par.setPreviewSize(cameraPreviewWidth, cameraPreviewHeight)
                        cam.parameters = par
                        cam.setPreviewCallback(this)
                        cam.setPreviewDisplay(holder)
                        cam.startPreview()
                        success = true
                    }
                }
            }
        } catch (e: Exception) {}
        if (!success) error("Error to initialize camera!")
    }

    private fun closeCamera() {
        camera?.apply {
            setPreviewCallback(null);
            stopPreview();
            release();
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeActivity()
    }

    override fun surfaceCreated(holder: SurfaceHolder?) {
        initializeCamera(holder)
    }

    override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
    }

    override fun surfaceDestroyed(holder: SurfaceHolder?) {
        closeCamera()
    }

    override fun onPreviewFrame(data: ByteArray?, camera: Camera?) {
        data?.let {
            processCameraFrame(it)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST) {
            if (grantResults.find { it != PackageManager.PERMISSION_GRANTED } != null) {
                error("Without these permissions, the app cannot be used.")
            } else {
                recreate()
            }
        }
    }

    override fun onBackPressed() {
        finish()
        exitProcess(1)

    }

    private fun writeClassPathFile() : Boolean =
        try {
            val file = File(filesDir.absolutePath + "/class.txt")
            if (!file.exists()) file.createNewFile()
            FileOutputStream(file).use {
                it.write(CLASS_PATH.toByteArray())
            }
            true
        } catch (e: Exception) {
            false
        }


    private fun checkPermissions() : Boolean = if (Build.VERSION.SDK_INT < 23) true else {
        permissions.find { permission ->
                checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
            } == null
        }

    private fun askPermissions() {
        if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(permissions.toTypedArray(), PERMISSION_REQUEST)
        }
    }

    private fun error(msg: String, fatal: Boolean = true) {
        AlertDialog.Builder(this).apply {
            setMessage(msg)
            setPositiveButton("OK") { _, _ ->
                if (fatal) {
                    finish()
                    exitProcess(1)
                }
            }
        }.create().show()
    }

    private fun charsToString(chars: CharArray, charCount: Int = 100) =
        StringBuilder().apply {
            for (i in 0 .. charCount) {
                val c = chars[i]
                if (c.toInt() == 0) return@apply
                append(c)
            }
        }.toString()


    external fun InitCaller(v: Int): Int

    external fun ProcessImageCaller(
        buffer: ByteArray, width: Int, height: Int,
        reserved1: Int, reserved2: Int,
        useLightCorrection: Int, detectSquarePlates: Int, detectWhiteOnBlack: Int
    ): ByteArray

}