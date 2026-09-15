package com.redwind.hyperorig.hook

import android.annotation.SuppressLint
import android.app.StatusBarManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.redwind.hyperorig.pods.RfcommController
import com.redwind.hyperorig.utils.SystemApisUtils
import com.redwind.hyperorig.utils.SystemApisUtils.setIconVisibility
import java.lang.reflect.Method

/**
 * 监听 A2DP 连接状态变化，在耳机连接/断开时启动/停止本地 SPP 控制器，并维护状态栏图标。
 *
 * 兼容性：
 * - HyperOS 3 的 A2dpService 方法名为 `handleConnectionStateChanged(...)`；
 * - AOSP / Android 13(MIUI14) 的方法名为 `connectionStateChanged(device, fromState, toState)`。
 * 两者参数顺序一致，因此按名字列表探测即可同时兼容。
 *
 * 整个回调体用 runCatching 包裹，避免在蓝牙进程中抛异常导致 com.android.bluetooth 崩溃。
 */
object HeadsetStateDispatcher : HookContext() {
    private const val TAG = "HyperOriG-A2dp"

    private val CANDIDATE_METHODS = listOf("handleConnectionStateChanged", "connectionStateChanged")

    override fun onHook() {
        val method = findCompatMethod()
        if (method == null) {
            Log.w(TAG, "A2dpService connection-state method not found, skip")
            return
        }
        hookAfter(method) {
            val device = args.getOrNull(0) as? BluetoothDevice ?: return@hookAfter
            val fromState = args.getOrNull(1) as? Int ?: return@hookAfter
            val currState = args.getOrNull(2) as? Int ?: return@hookAfter
            if (currState == fromState) {
                return@hookAfter
            }
            val service = instance
            val handler = runCatching { getObjectField(service, "mHandler") as? Handler }.getOrNull()
                ?: Handler(Looper.getMainLooper())
            handler.post { handleStateChange(service, device, currState) }
        }
        Log.d(TAG, "A2dpService hooked via ${method.name}")
    }

    private fun findCompatMethod(): Method? {
        val cls = runCatching { findClass("com.android.bluetooth.a2dp.A2dpService") }.getOrNull() ?: return null
        return CANDIDATE_METHODS.firstNotNullOfOrNull { name ->
            cls.declaredMethods.firstOrNull { it.name == name && it.parameterTypes.size == 3 }
                ?.apply { isAccessible = true }
        }
    }

    /** 记住当前已连接的耳机地址：断开时 device.name 可能取不到，用它兜底判断 */
    private var activePodAddress: String? = null

    @SuppressLint("MissingPermission")
    private fun handleStateChange(service: Any?, device: BluetoothDevice, currState: Int) {
        runCatching {
            val context = (service as? Context)
                ?: runCatching { getObjectField(service, "mContext") as? Context }.getOrNull()
                ?: return
            val address = runCatching { device.address }.getOrNull()
            val isPod = isOriGPod(device) || (address != null && address.equals(activePodAddress, ignoreCase = true))
            if (!isPod) return
            val statusBarManager = context.getSystemService("statusbar") as? StatusBarManager
            when (currState) {
                BluetoothHeadset.STATE_CONNECTED -> {
                    activePodAddress = address
                    // MIUI14 的 com.android.bluetooth 没有 STATUS_BAR 权限，setIconVisibility 会抛
                    // SecurityException。必须单独吞掉，否则会中断后面的注册与 SPP 连接。
                    runCatching { statusBarManager?.setIconVisibility("wireless_headset", true) }
                    // MIUI14 / 无焦点岛：注册并驱动普通通知回退
                    if (!SystemApisUtils.isHyperOS) {
                        runCatching { PodsUiBridge.onPodConnected(context, device) }
                    }
                    RfcommController.connectPod(context, device, prefs)
                }
                BluetoothHeadset.STATE_DISCONNECTING, BluetoothHeadset.STATE_DISCONNECTED -> {
                    runCatching { statusBarManager?.setIconVisibility("wireless_headset", false) }
                    // 关键：先取消通知、再清空状态。顺序反了会导致没有可取消的目标 → 通知残留。
                    if (!SystemApisUtils.isHyperOS) {
                        runCatching { PodsUiBridge.cancelPodsNotification(context, device) }
                        runCatching { PodsUiBridge.onPodDisconnected(device) }
                    }
                    RfcommController.disconnectedPod(context, device)
                    activePodAddress = null
                    Log.i(TAG, "disconnect cleanup done address=$address")
                }
            }
        }.onFailure { Log.w(TAG, "handleStateChange failed state=$currState", it) }
    }

    @SuppressLint("MissingPermission")
    fun isOriGPod(device: BluetoothDevice): Boolean {
        val name = device.name ?: return false
        return name.contains("YUANDAO", ignoreCase = true) ||
               name.contains("OriG", ignoreCase = true) ||
               name.contains("NiceHCK", ignoreCase = true)
    }
}
