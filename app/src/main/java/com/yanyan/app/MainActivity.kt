package com.yanyan.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val OVERLAY_PERMISSION_REQ = 1001
    private val SCREEN_CAPTURE_REQ = 1002

    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("yanyan_prefs", Context.MODE_PRIVATE)

        setupUI()
    }

    private fun setupUI() {
        // API设置
        val etUrl = findViewById<EditText>(R.id.et_api_url)
        val etKey = findViewById<EditText>(R.id.et_api_key)
        val etModel = findViewById<EditText>(R.id.et_api_model)
        etUrl.setText(prefs.getString("api_url", "https://api.deepseek.com/v1"))
        etKey.setText(prefs.getString("api_key", ""))
        etModel.setText(prefs.getString("api_model", "deepseek-vl2"))

        findViewById<Button>(R.id.btn_save_api).setOnClickListener {
            prefs.edit()
                .putString("api_url", etUrl.text.toString().trim())
                .putString("api_key", etKey.text.toString().trim())
                .putString("api_model", etModel.text.toString().trim())
                .apply()
            Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show()
        }

        // 间隔设置
        val etInterval = findViewById<EditText>(R.id.et_interval)
        etInterval.setText((prefs.getLong("watch_interval", 45000L) / 1000).toString())

        // 开始看
        findViewById<Button>(R.id.btn_start_watch).setOnClickListener {
            val sec = etInterval.text.toString().toLongOrNull() ?: 45L
            prefs.edit().putLong("watch_interval", sec * 1000).apply()
            checkAndStartWatching()
        }

        // 停止
        findViewById<Button>(R.id.btn_stop_watch).setOnClickListener {
            val intent = Intent(this, ScreenWatchService::class.java).apply {
                action = ScreenWatchService.ACTION_STOP
            }
            startService(intent)
            Toast.makeText(this, "衍衍停止查看了", Toast.LENGTH_SHORT).show()
        }

        // 锁软件开关
        val swLock = findViewById<Switch>(R.id.sw_lock)
        swLock.isChecked = prefs.getBoolean("lock_enabled", false)
        swLock.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("lock_enabled", checked).apply()
            if (checked) checkAccessibilityService()
        }

        // 要锁的软件（逗号分隔包名）
        val etLockedApps = findViewById<EditText>(R.id.et_locked_apps)
        val lockedSet = prefs.getStringSet("locked_apps", emptySet()) ?: emptySet()
        etLockedApps.setText(lockedSet.joinToString(","))

        findViewById<Button>(R.id.btn_save_lock).setOnClickListener {
            val apps = etLockedApps.text.toString()
                .split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            prefs.edit().putStringSet("locked_apps", apps).apply()
            Toast.makeText(this, "已保存 ${apps.size} 个锁定软件", Toast.LENGTH_SHORT).show()
        }

        // 无障碍服务入口
        findViewById<Button>(R.id.btn_accessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun checkAndStartWatching() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"))
            startActivityForResult(intent, OVERLAY_PERMISSION_REQ)
            Toast.makeText(this, "请允许悬浮窗权限，然后再试", Toast.LENGTH_LONG).show()
            return
        }

        // 先启动悬浮窗服务
        startService(Intent(this, OverlayService::class.java))

        // 申请截图权限
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mgr.createScreenCaptureIntent(), SCREEN_CAPTURE_REQ)
    }

    private fun checkAccessibilityService() {
        Toast.makeText(this, "请在无障碍设置中开启「衍衍的小手机」", Toast.LENGTH_LONG).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            OVERLAY_PERMISSION_REQ -> {
                if (Settings.canDrawOverlays(this)) checkAndStartWatching()
            }
            SCREEN_CAPTURE_REQ -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val intent = Intent(this, ScreenWatchService::class.java).apply {
                        action = ScreenWatchService.ACTION_START
                        putExtra(ScreenWatchService.EXTRA_RESULT_CODE, resultCode)
                        putExtra(ScreenWatchService.EXTRA_RESULT_DATA, data)
                    }
                    startForegroundService(intent)
                    Toast.makeText(this, "衍衍开始看你了 🐾", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
