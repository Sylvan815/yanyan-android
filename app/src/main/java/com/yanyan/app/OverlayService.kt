package com.yanyan.app

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.*
import android.widget.TextView
import android.os.Handler
import android.os.Looper

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        const val ACTION_SHOW = "show"
        const val ACTION_HIDE = "hide"
        const val EXTRA_MESSAGE = "message"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                val message = intent.getStringExtra(EXTRA_MESSAGE) ?: return START_NOT_STICKY
                showBubble(message)
            }
            ACTION_HIDE -> hideBubble()
        }
        return START_NOT_STICKY
    }

    private fun showBubble(message: String) {
        hideBubble()

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_bubble, null)
        view.findViewById<TextView>(R.id.bubble_text).text = message

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 180
        }

        // 支持拖动
        var initialX = 0; var initialY = 0
        var touchX = 0f; var touchY = 0f
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    touchX = event.rawX; touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    windowManager?.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(view, params)
        overlayView = view

        // 8秒后自动消失
        handler.postDelayed({ hideBubble() }, 8000)
    }

    private fun hideBubble() {
        overlayView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
            overlayView = null
        }
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        hideBubble()
        super.onDestroy()
    }
}
