package com.igormeira.camera2api

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.TextureView.SurfaceTextureListener
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.igormeira.camera2api.util.AutoFitTextureView
import com.igormeira.camera2api.util.CameraFocusOnTouchHandler
import java.nio.ByteBuffer
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private val HANDLER = Handler()

    companion object {
        private const val TAG = "AndroidCamera2Api"
        private const val REQUEST_CAMERA_PERMISSION = 200
        private val ORIENTATIONS = SparseIntArray()
        init {
            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }
    }

    private var mTextureView: AutoFitTextureView? = null
    private var mImageDimension: Size? = null
    private var mCameraDevice: CameraDevice? = null
    private var mCameraCaptureSessions: CameraCaptureSession? = null
    private var mCaptureRequestBuilder: CaptureRequest.Builder? = null
    private var mBackgroundHandler: Handler? = null
    private var mBackgroundThread: HandlerThread? = null
    private var mManager: CameraManager? = null
    private var mCharacteristics: CameraCharacteristics? = null
    private var bitmap: Bitmap? = null
    private val flashSupport = false
    private var isFlashTorch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mTextureView = findViewById(R.id.texture)
        assert(mTextureView != null)
    }

    // PREVIEW CAMERA //
    var textureListener: SurfaceTextureListener = object : SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(
            surface: SurfaceTexture,
            width: Int,
            height: Int
        ) {
            //open your camera here
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(
            surface: SurfaceTexture,
            width: Int,
            height: Int
        ) {
            // Transform you image captured size according to the surface width and height
            //configureTransform(width, height);
            val a = 0
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            if (mCameraDevice != null) {
                closeCamera()
            }
            surface.release()
            return false
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }

    private val stateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            //This is called when the camera is open
            Log.d(TAG, "cameraDeviceCallback onOpened")
            mCameraDevice = camera
            createCameraPreview()
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.d(TAG, "cameraDeviceCallback onDisconnected")
            closeCamera()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.d(TAG, "cameraDeviceCallback onError")
            closeCamera()
        }
    }

    fun startBackgroundThread() {
        mBackgroundThread = HandlerThread("Background Thread")
        mBackgroundThread!!.start()
        mBackgroundHandler = Handler(mBackgroundThread!!.looper)
    }

    fun stopBackgroundThread() {
        mBackgroundThread!!.quitSafely()
        try {
            mBackgroundThread!!.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    fun takePicture(v: View?) {
        if (null == mCameraDevice) {
            Log.e(TAG, "mCameraDevice is null")
            return
        }
        try {
            bitmap = mTextureView?.bitmap //ARGB_8888
            val h = bitmap!!.height
            val w = bitmap!!.width
            val bytes = bitmap!!.byteCount
            val buf = ByteBuffer.allocate(bytes)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createCameraPreview() {
        try {
            val texture = mTextureView!!.surfaceTexture!!
            texture.setDefaultBufferSize(mImageDimension!!.width, mImageDimension!!.height)
            val surface = Surface(texture)

            mCaptureRequestBuilder =
                mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)

            //changeAutoFocus(false);
            switchFlashMode()
            mCaptureRequestBuilder!!.addTarget(surface)
            mCameraDevice!!.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        //The camera is already closed
                        if (null == mCameraDevice) {
                            return
                        }
                        // When the session is ready, we start displaying the preview.
                        mCameraCaptureSessions = cameraCaptureSession
                        updatePreview()
                    }

                    override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                        Toast.makeText(
                            this@MainActivity,
                            "Configuration change",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                null
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun openCamera() {
        runOnUiThread(Runnable {
            mManager =
                getSystemService(Context.CAMERA_SERVICE) as CameraManager
            Log.e(TAG, "is camera open")
            try {
                var cameraId =
                    (if (mManager != null) mManager!!.cameraIdList else arrayOfNulls(
                        0
                    ))[0]
                println("CAMERA ID: $cameraId")
                cameraId = "1"
                mCharacteristics =
                    if (mManager != null) mManager!!.getCameraCharacteristics(cameraId) else null
                val map = mCharacteristics!!.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
                )!!
                mImageDimension = map.getOutputSizes(
                    SurfaceTexture::class.java
                )[0]
                setPreviewDimensions()
                if (ActivityCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.CAMERA
                    ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        arrayOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ),
                        REQUEST_CAMERA_PERMISSION
                    )
                    return@Runnable
                }
                mManager!!.openCamera(cameraId, stateCallback, null)
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
            Log.e(TAG, "openCamera X")
        })
    }

    private fun setPreviewDimensions() {
        val tv: AutoFitTextureView? = mTextureView
        val previewView = findViewById<View>(R.id.previewView)
        tv!!.setAspectRatio(mImageDimension!!.height, mImageDimension!!.width)
        val params: ViewGroup.LayoutParams = tv.layoutParams
        val widthFit =
            previewView.height.toFloat() * mImageDimension!!.height
                .toFloat() / mImageDimension!!.width.toFloat()
        params.height = previewView.height
        params.width = widthFit.roundToInt()
        tv.layoutParams = params
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun updatePreview() {
        if (null == mCameraDevice) {
            Log.e(TAG, "updatePreview error, return")
        }
        mCaptureRequestBuilder!!.set(
            CaptureRequest.CONTROL_MODE,
            CameraMetadata.CONTROL_MODE_AUTO
        )
        try {
            mCameraCaptureSessions!!.setRepeatingRequest(
                mCaptureRequestBuilder!!.build(),
                null,
                mBackgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        mTextureView?.setOnTouchListener(
            CameraFocusOnTouchHandler(
                mCharacteristics!!,
                mCaptureRequestBuilder!!,
                mCameraCaptureSessions!!,
                mBackgroundHandler!!
            )
        )
    }

    private fun closeCamera() {
        runOnUiThread {
            if (null != mCameraDevice) {
                mCameraDevice!!.close()
                mCameraDevice = null
            }
        }
    }

    // ANDROID STUFF //
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(
                    this@MainActivity,
                    "Sorry!!!, you can't use this app without granting permission",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.e(TAG, "onResume")
        startBackgroundThread()
        if (mTextureView?.isAvailable!!) {
            openCamera()
        } else {
            mTextureView?.surfaceTextureListener = textureListener
        }
    }

    override fun onPause() {
        Log.e(TAG, "onPause")
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    override fun onBackPressed() {
        finish()
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2) {
            if (resultCode == Activity.RESULT_OK) {
                // TODO
            } else if (resultCode == Activity.RESULT_CANCELED) {
                // TODO
            }
        }
    }

    // FUNCTIONS //
    fun switchFlashMode(): Unit {
        if (!flashSupport) return
        try {
            if (isFlashTorch) {
                isFlashTorch = false
                mCaptureRequestBuilder!!.set(
                    CaptureRequest.FLASH_MODE,
                    CameraMetadata.FLASH_MODE_OFF
                )
            } else {
                isFlashTorch = true
                mCaptureRequestBuilder!!.set(
                    CaptureRequest.FLASH_MODE,
                    CameraMetadata.FLASH_MODE_TORCH
                )
            }
            if (mCameraCaptureSessions != null) {
                mCameraCaptureSessions!!.setRepeatingRequest(
                    mCaptureRequestBuilder!!.build(),
                    null,
                    null
                )
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private fun backToResult() {
        // TODO
    }
}
