package com.redwind.hyperorig.hook

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.redwind.hyperorig.R
import com.redwind.hyperorig.pods.RfcommController
import com.redwind.hyperorig.utils.FocusIslandUtil
import com.redwind.hyperorig.utils.RuntimeLog
import com.redwind.hyperorig.utils.SystemApisUtils
import com.redwind.hyperorig.utils.SystemApisUtils.cancelAsUser
import com.redwind.hyperorig.utils.SystemApisUtils.isHyperOS
import com.redwind.hyperorig.utils.SystemApisUtils.notifyAsUser
import com.redwind.hyperorig.utils.miuiStrongToast.data.BatteryParams
import com.redwind.hyperorig.utils.miuiStrongToast.data.HyperOriGAction
import com.redwind.hyperorig.utils.miuiStrongToast.data.PodParams

/**
 * 共享的耳机电量/降噪通知桥。
 *
 * - HyperOS（有焦点岛）：走 [MiBluetoothToastHook.createPodsNotification] 的焦点岛通知。
 * - MIUI14 / 其他无焦点岛的系统：回退到普通 heads-up Notification，
 *   保证电量与降噪循环按钮在 Android 13 上也能显示。
 *
 * 在 MIUI14 上由 [HeadsetStateDispatcher]（A2DP 连接事件）注册到 com.android.bluetooth 进程，
 * 并监听 [com.redwind.hyperorig.pods.RfcommController] 发出的电量/降噪广播。
 */
@SuppressLint("WrongConstant", "MissingPermission")
object PodsUiBridge {
    private const val TAG = "HyperOriG-PodsUiBridge"
    private const val CHANNEL_PREFIX = "BTHeadset"
    private const val NOTIFICATION_ID = 10003
    private const val MODULE_PACKAGE = "com.redwind.hyperorig"

    @Volatile
    private var registered = false
    private var localAncMode = 1
    private var lastConnectedDevice: BluetoothDevice? = null
    private var lastBatteryParams: BatteryParams? = null
    private var lastSignature: String? = null

    fun register(context: Context) {
        if (registered) return
        registered = true
        try {
            val filter = IntentFilter(HyperOriGAction.ACTION_SEND_STRONG_TOAST)
            filter.addAction(HyperOriGAction.ACTION_UPDATE_PODS_NOTIFICATION)
            filter.addAction(HyperOriGAction.ACTION_CANCEL_PODS_NOTIFICATION)
            filter.addAction(HyperOriGAction.ACTION_CYCLE_ANC)
            filter.addAction(HyperOriGAction.ACTION_PODS_ANC_CHANGED)
            filter.addAction(HyperOriGAction.ACTION_ADAPTIVE_MODE_CHANGED)
            // MIUI14 回退路径：直接消费 RfcommController 的状态广播
            filter.addAction(HyperOriGAction.ACTION_PODS_CONNECTED)
            filter.addAction(HyperOriGAction.ACTION_PODS_BATTERY_CHANGED)
            filter.addAction(HyperOriGAction.ACTION_PODS_DISCONNECTED)
            ContextCompat.registerReceiver(context, buildReceiver(), filter, ContextCompat.RECEIVER_EXPORTED)
            Log.i(TAG, "registered pods UI receiver")
        } catch (t: Throwable) {
            Log.e(TAG, "failed to register pods UI receiver", t)
        }
    }

    /** MIUI14：A2DP 连接后由 [HeadsetStateDispatcher] 调用，注册广播并记录当前设备。 */
    fun onPodConnected(context: Context, device: BluetoothDevice) {
        lastConnectedDevice = device
        lastSignature = null
        register(context)
        RuntimeLog.i(TAG, "pod connected device=${device.address}")
    }

    fun onPodDisconnected(device: BluetoothDevice) {
        if (lastConnectedDevice?.address == device.address) {
            lastConnectedDevice = null
            lastBatteryParams = null
            lastSignature = null
        }
        RuntimeLog.i(TAG, "pod disconnected device=${device.address}")
    }

    private fun buildReceiver(): BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
            val context = p0 ?: return
            when (p1?.action) {
                HyperOriGAction.ACTION_SEND_STRONG_TOAST -> {
                    val batteryParams = p1.getParcelableExtra("batteryParams", BatteryParams::class.java) ?: return
                    if (isHyperOS) {
                        FocusIslandUtil.showBatteryIsland(context, batteryParams)
                    } else {
                        showPodsNotification(context, lastConnectedDevice, batteryParams, localAncMode)
                    }
                }
                HyperOriGAction.ACTION_UPDATE_PODS_NOTIFICATION -> {
                    val batteryParams = p1.getParcelableExtra("batteryParams", BatteryParams::class.java)
                    val device = p1.getParcelableExtra("device", BluetoothDevice::class.java)
                    if (device != null) lastConnectedDevice = device
                    if (batteryParams != null) lastBatteryParams = batteryParams
                    if (batteryParams != null && device != null) {
                        showPodsNotification(context, device, batteryParams, localAncMode)
                    }
                }
                HyperOriGAction.ACTION_CANCEL_PODS_NOTIFICATION -> {
                    val device = p1.getParcelableExtra("device", BluetoothDevice::class.java) ?: return
                    cancelNotification(context, device)
                }
                HyperOriGAction.ACTION_PODS_ANC_CHANGED -> {
                    localAncMode = p1.getIntExtra("status", 1)
                    RuntimeLog.i(TAG, "ANC broadcast -> localAncMode=$localAncMode")
                    if (lastConnectedDevice != null && lastBatteryParams != null) {
                        showPodsNotification(context, lastConnectedDevice!!, lastBatteryParams!!, localAncMode)
                    }
                }
                HyperOriGAction.ACTION_ADAPTIVE_MODE_CHANGED -> {
                    val adaptiveEnabled = p1.getBooleanExtra("enabled", true)
                    if (!adaptiveEnabled && localAncMode == 4) localAncMode = 2
                    if (lastConnectedDevice != null && lastBatteryParams != null) {
                        showPodsNotification(context, lastConnectedDevice!!, lastBatteryParams!!, localAncMode)
                    }
                }
                HyperOriGAction.ACTION_CYCLE_ANC -> {
                    RuntimeLog.i(TAG, "ANC cycle tapped, from=$localAncMode")
                    // 回到降噪时恢复用户上次使用的子模式（普通/深度/实验性），而不是固定“普通”
                    val preferredNc = RfcommController.preferredNoiseControlMode()
                    localAncMode = when (localAncMode) {
                        1 -> preferredNc   // OFF → 降噪（上次子模式）
                        3, 4, 5 -> 2       // 降噪 → 通透
                        2 -> 1             // 通透 → OFF
                        6 -> preferredNc   // 抗风噪 → 降噪（上次子模式）
                        else -> 1
                    }
                    RuntimeLog.i(TAG, "ANC cycle -> $localAncMode, sending ACTION_ANC_SELECT")
                    Intent(HyperOriGAction.ACTION_ANC_SELECT).apply {
                        putExtra("status", localAncMode)
                        addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                        context.sendBroadcast(this)
                    }
                    if (lastConnectedDevice != null && lastBatteryParams != null) {
                        showPodsNotification(context, lastConnectedDevice!!, lastBatteryParams!!, localAncMode)
                    }
                }
                // ---- MIUI14 回退路径：直接消费 RfcommController 的广播 ----
                HyperOriGAction.ACTION_PODS_CONNECTED -> {
                    resolveDevice(p1.getStringExtra("address"))?.let { lastConnectedDevice = it }
                    context.sendBroadcast(Intent(HyperOriGAction.ACTION_REFRESH_STATUS).apply {
                        setPackage("com.android.bluetooth")
                        addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    })
                }
                HyperOriGAction.ACTION_PODS_BATTERY_CHANGED -> {
                    val batteryParams = p1.getParcelableExtra("status", BatteryParams::class.java)
                        ?: parseBatteryExtras(p1)
                        ?: return
                    // 已断开则忽略迟到的电量广播，避免把通知又“复活”
                    if (lastConnectedDevice == null) {
                        RuntimeLog.i(TAG, "battery ignored: no active device")
                        return
                    }
                    lastBatteryParams = batteryParams
                    val address = p1.getStringExtra("address")
                    if (address != null && !address.equals(lastConnectedDevice?.address, ignoreCase = true)) {
                        resolveDevice(address)?.let { lastConnectedDevice = it }
                    }
                    if (!isHyperOS) {
                        lastConnectedDevice?.let { showPodsNotification(context, it, batteryParams, localAncMode) }
                    }
                }
                HyperOriGAction.ACTION_PODS_DISCONNECTED -> {
                    // 注意：不能依赖 lastConnectedDevice（可能已被提前清空），
                    // 优先用广播里携带的 address 来取消通知。
                    val address = p1.getStringExtra("address") ?: lastConnectedDevice?.address
                    RuntimeLog.i(TAG, "disconnect broadcast, cancel notification address=$address")
                    address?.let { cancelNotificationByAddress(context, it) }
                    lastConnectedDevice = null
                    lastBatteryParams = null
                    lastSignature = null
                }
            }
        }
    }

    private fun resolveDevice(address: String?): BluetoothDevice? {
        if (address.isNullOrEmpty()) return null
        return runCatching { BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(address) }.getOrNull()
    }

    private fun parseBatteryExtras(intent: Intent): BatteryParams? {
        if (!intent.hasExtra("left_connected") && !intent.hasExtra("right_connected") && !intent.hasExtra("case_connected")) {
            return null
        }
        return BatteryParams(
            left = PodParams(
                intent.getIntExtra("left_battery", 0),
                intent.getBooleanExtra("left_charging", false),
                intent.getBooleanExtra("left_connected", false),
                0
            ),
            right = PodParams(
                intent.getIntExtra("right_battery", 0),
                intent.getBooleanExtra("right_charging", false),
                intent.getBooleanExtra("right_connected", false),
                0
            ),
            case = PodParams(
                intent.getIntExtra("case_battery", 0),
                intent.getBooleanExtra("case_charging", false),
                intent.getBooleanExtra("case_connected", false),
                0
            )
        )
    }

    private fun showPodsNotification(
        context: Context,
        device: BluetoothDevice?,
        batteryParams: BatteryParams,
        ancMode: Int
    ) {
        if (device == null) return
        if (isHyperOS) {
            try {
                MiBluetoothToastHook.createPodsNotification(context, device, batteryParams, ancMode)
                return
            } catch (t: Throwable) {
                Log.e(TAG, "HyperOS focus notification failed, fallback to plain notification", t)
            }
        }
        showPlainPodsNotification(context, device, batteryParams, ancMode)
    }

    private fun showPlainPodsNotification(
        context: Context,
        device: BluetoothDevice,
        batteryParams: BatteryParams,
        ancMode: Int
    ) {
        // 通知在 com.android.bluetooth 等系统进程内构建，必须使用模块自身包的资源。
        val moduleContext = runCatching {
            context.createPackageContext(MODULE_PACKAGE, Context.CONTEXT_IGNORE_SECURITY)
        }.getOrNull() ?: context

        val address = device.address
        val alias = runCatching { device.alias ?: device.name }.getOrNull().orEmpty().ifEmpty { address }
        val channelId = "$CHANNEL_PREFIX$address"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(channelId, alias, NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
            }
        )

        val contentText = buildContentText(moduleContext, batteryParams)
        val ancLabel = ancLabel(moduleContext, ancMode)
        RuntimeLog.i(
            TAG,
            "battery update alias=$alias content=\"$contentText\" anc=$ancLabel " +
                "left=${batteryParams.left?.battery}/${batteryParams.left?.isConnected} " +
                "right=${batteryParams.right?.battery}/${batteryParams.right?.isConnected} " +
                "case=${batteryParams.case?.battery}/${batteryParams.case?.isConnected}"
        )
        // 内容未变化则不重复发布，避免每 30s 电量轮询导致通知反复弹出
        val signature = "$alias|$contentText|$ancLabel"
        if (signature == lastSignature) return
        lastSignature = signature

        val disconnectAction = Notification.Action.Builder(
            Icon.createWithResource(context, android.R.drawable.ic_delete),
            moduleContext.getString(R.string.notification_btn_disconnect),
            PendingIntent.getBroadcast(
                context, 2,
                Intent("com.android.bluetooth.headset.notification").apply {
                    val b = Bundle().apply { putParcelable("Device", device) }
                    putExtra("btData", b)
                    putExtra("disconnect", "1")
                    setIdentifier(channelId)
                },
                201326592
            )
        ).build()

        val ancCycleIntent = Intent(HyperOriGAction.ACTION_CYCLE_ANC).apply { setIdentifier(channelId) }
        val ancAction = Notification.Action.Builder(
            Icon.createWithResource(context, android.R.drawable.ic_lock_silent_mode),
            ancLabel,
            PendingIntent.getBroadcast(context, 1, ancCycleIntent, 201326592)
        ).build()

        val pendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent("com.redwind.hyperorig.action.show_pods_ui").apply {
                setClassName("com.redwind.hyperorig", "com.redwind.hyperorig.PopupActivity")
                putExtra("android.bluetooth.device.extra.DEVICE", device)
                putExtra("bluetoothaddress", address)
                putExtra("device_name", alias)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val accentColor = runCatching {
            val id = context.resources.getIdentifier("system_notification_accent_color", "color", "android")
            if (id != 0) context.getColor(id) else 0
        }.getOrElse { 0 }

        val notification = Notification.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setWhen(0L)
            .setContentTitle(alias)
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(deleteIntent(context, device))
            .setColor(accentColor)
            .addAction(disconnectAction)
            .addAction(ancAction)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()

        try {
            nm.notifyAsUser(channelId, NOTIFICATION_ID, notification, SystemApisUtils.getUserAllUserHandle())
        } catch (_: Throwable) {
            nm.notify(channelId, NOTIFICATION_ID, notification)
        }
    }

    private fun buildContentText(context: Context, batteryParams: BatteryParams): String {
        val parts = mutableListOf<String>()
        batteryParams.left?.let {
            if (it.isConnected) parts.add("${context.getString(R.string.batt_left_pod)} ${it.battery}%${if (it.isCharging) "⚡" else ""}")
        }
        batteryParams.right?.let {
            if (it.isConnected) parts.add("${context.getString(R.string.batt_right_pod)} ${it.battery}%${if (it.isCharging) "⚡" else ""}")
        }
        batteryParams.case?.let {
            if (it.isConnected) parts.add("${context.getString(R.string.pod_case)} ${it.battery}%${if (it.isCharging) "⚡" else ""}")
        }
        return parts.joinToString("  ")
    }

    private fun ancLabel(context: Context, mode: Int): String {
        val resId = when (mode) {
            3 -> R.string.anc_notif_nc
            4 -> R.string.anc_notif_deep
            5 -> R.string.anc_notif_experiment
            2 -> R.string.anc_notif_transparency
            6 -> R.string.anc_notif_wind
            else -> R.string.anc_notif_off
        }
        return context.getString(resId)
    }

    private fun deleteIntent(context: Context, device: BluetoothDevice): PendingIntent {
        return PendingIntent.getBroadcast(
            context, 0,
            Intent("com.android.bluetooth.headset.notification.cancle").apply {
                putExtra("android.bluetooth.device.extra.DEVICE", device)
            },
            201326592
        )
    }

    /** 供 [HeadsetStateDispatcher] 在断开时直接调用，立即取消残留通知。 */
    fun cancelPodsNotification(context: Context, device: BluetoothDevice) {
        cancelNotificationByAddress(context, device.address)
    }

    private fun cancelNotification(context: Context, device: BluetoothDevice) {
        cancelNotificationByAddress(context, device.address)
    }

    private fun cancelNotificationByAddress(context: Context, address: String) {
        val channelId = "$CHANNEL_PREFIX$address"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        RuntimeLog.i(TAG, "cancel notification tag=$channelId id=$NOTIFICATION_ID")
        try {
            nm.cancelAsUser(channelId, NOTIFICATION_ID, SystemApisUtils.getUserAllUserHandle())
        } catch (_: Throwable) {
            nm.cancel(channelId, NOTIFICATION_ID)
        }
    }
}
