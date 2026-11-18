package com.yanyan.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import androidx.core.app.NotificationCompat
import java.io.ByteArrayOutputStream

class ScreenWatchService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isWatching = false

    companion object {
        const val CHANNEL_ID = "yanyan_watch"
        const val NOTIF_ID = 1001
        const val ACTION_START = "start_watch"
        const val ACTION_STOP = "stop_watch"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                if (resultData != null) startWatching(resultCode, resultData)
            }
            ACTION_STOP -> stopWatching()
        }
        return START_STICKY
    }

    private fun startWatching(resultCode: Int, data: Intent) {
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mgr.getMediaProjection(resultCode, data)

        val dm = resources.displayMetrics
        val width = dm.widthPixels
        val height = dm.heightPixels
        val density = dm.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "YanYanCapture", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )

        isWatching = true
        scheduleCapture()
    }

    private fun scheduleCapture() {
        if (!isWatching) return
        val prefs = getSharedPreferences("yanyan_prefs", Context.MODE_PRIVATE)
        val interval = prefs.getLong("watch_interval", 45000L) // 默认45秒

        handler.postDelayed({
            captureAndAnalyze()
            scheduleCapture()
        }, interval)
    }

    private fun captureAndAnalyze() {
        val image = imageReader?.acquireLatestImage() ?: return
        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height, Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            // 压缩成JPEG，降低分辨率节省流量
            val scaled = Bitmap.createScaledBitmap(bitmap, 720, 1280, true)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 60, out)
            val imageBytes = out.toByteArray()

            bitmap.recycle()
            scaled.recycle()

            // 发给AI
            ApiClient.analyzeScreen(this, imageBytes,
                onResult = { comment ->
                    handler.post {
                        showComment(comment)
                    }
                },
                onError = { /* 静默失败 */ }
            )
        } finally {
            image.close()
        }
    }

    private fun showComment(text: String) {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SHOW
            putExtra(OverlayService.EXTRA_MESSAGE, text)
        }
        startService(intent)
    }

    private fun stopWatching() {
        isWatching = false
        handler.removeCallbacksAndMessages(null)
        virtualDisplay?.release()
        mediaProjection?.stop()
        imageReader?.close()
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopWatching()
        super.onDestroy()
    }
}
