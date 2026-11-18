package com.yanyan.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class AppLockService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return

        val prefs = getSharedPreferences("yanyan_prefs", Context.MODE_PRIVATE)
        val lockedApps = prefs.getStringSet("locked_apps", emptySet()) ?: emptySet()
        val lockEnabled = prefs.getBoolean("lock_enabled", false)

        if (!lockEnabled) return
        if (packageName == this.packageName) return

        if (lockedApps.contains(packageName)) {
            // 锁定：显示悬浮窗拦截
            val intent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_SHOW
                putExtra(OverlayService.EXTRA_MESSAGE, "🔒 衍衍锁上了这个软件")
            }
            startService(intent)

            // 返回主屏幕
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    override fun onInterrupt() {}
}
