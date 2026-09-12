package com.redwind.hyperorig.hook

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.os.Bundle
import com.xzakota.hyper.notification.focus.FocusNotification
import com.redwind.hyperorig.R
import com.redwind.hyperorig.utils.SystemApisUtils
import com.redwind.hyperorig.utils.SystemApisUtils.notifyAsUser
import com.redwind.hyperorig.utils.miuiStrongToast.data.BatteryParams
import com.redwind.hyperorig.utils.miuiStrongToast.data.HyperOriGAction

@SuppressLint("MissingPermission")
object MiBluetoothToastHook : HookContext() {

    private const val TAG = "HyperOriG-MiBluetoothToast"

    override fun onHook() {
        hookMiuiBluetoothNotificationConstructor()
        hookMxBluetoothContextEntry()
    }

    /**
     * HyperOS 3：通过 MiuiBluetoothNotification 构造拿到 context 并注册通知桥。
     * MIUI14 上该类不存在，此 hook 安全跳过（由 [hookMxBluetoothContextEntry] 兜底）。
     */
    private fun hookMiuiBluetoothNotificationConstructor() {
        runCatching {
            val ctor = findConstructorByParamCount("com.android.bluetooth.ble.app.MiuiBluetoothNotification", 2)
            hookConstructorAfter(ctor) {
                val context = getObjectField(instance, "mContext") as? Context
                if (context != null) PodsUiBridge.register(context)
            }
            Log.d(TAG, "MiuiBluetoothNotification constructor hook installed")
        }.onFailure { Log.w(TAG, "MiuiBluetoothNotification constructor hook skipped (expected on MIUI14)", it) }
    }

    /**
     * 在 com.xiaomi.bluetooth 内通过 mxbluetoothsdk 的入口方法拿到 Context，
     * 作为 MIUI14（无 MiuiBluetoothNotification）下注册通知桥的兜底触发点。
     */
    private fun hookMxBluetoothContextEntry() {
        listOf(
            "com.xiaomi.mxbluetoothsdk.service.MxBluetoothService",
            "com.xiaomi.mxbluetoothsdk.manager.MxBluetoothManager"
        ).forEach { className ->
            listOf("getInstance", "getInstanceForIsMiTWS").forEach { methodName ->
                runCatching {
                    hookBefore(findMethod(className, methodName, Context::class.java)) {
                        (args[0] as? Context)?.let { PodsUiBridge.register(it) }
                    }
                    Log.d(TAG, "$className.$methodName context-entry hook installed")
                }.onFailure { Log.w(TAG, "hook $className.$methodName skipped", it) }
            }
        }
    }

    /**
     * HyperOS 焦点岛通知。仅在 [com.redwind.hyperorig.utils.SystemApisUtils.isHyperOS] 时由
     * [PodsUiBridge] 调用。MIUI14 不会走到这里。
     */
    fun createPodsNotification(
        context: Context,
        bluetoothDevice: BluetoothDevice,
        batteryParams: BatteryParams,
        ancMode: Int
    ) {
        val miheadset_notification_Box = context.resources.getIdentifier("miheadset_notification_Box", "string", "com.xiaomi.bluetooth")
        val miheadset_notification_LeftEar = context.resources.getIdentifier("miheadset_notification_LeftEar", "string", "com.xiaomi.bluetooth")
        val miheadset_notification_RightEar = context.resources.getIdentifier("miheadset_notification_RightEar", "string", "com.xiaomi.bluetooth")
        val miheadset_notification_Disconnect = context.resources.getIdentifier("miheadset_notification_Disconnect", "string", "com.xiaomi.bluetooth")
        val system_notification_accent_color = context.resources.getIdentifier("system_notification_accent_color", "color", "android")
        if (bluetoothDevice == null) {
            Log.e("HyperOriG", "createPodsNotification: btDevice null")
            return
        }
        try {
            val address: String = bluetoothDevice.address
            var alias: String? = bluetoothDevice.alias
            if (alias?.isEmpty() == true) {
                alias = bluetoothDevice.name
            }

            val caseBattStr = if (batteryParams.case != null && batteryParams.case!!.isConnected)
                " ${context.resources.getString(miheadset_notification_Box)}${batteryParams.case!!.battery}%" +
                        "${if (batteryParams.case!!.isCharging) "⚡" else ""}"
            else ""
            val leftEar = if (batteryParams.left != null && batteryParams.left!!.isConnected)
                "${context.resources.getString(miheadset_notification_LeftEar)}${batteryParams.left!!.battery}%" +
                    (if (batteryParams.left!!.isCharging) "⚡" else "")
            else ""
            val leftToRight = if (batteryParams.left?.isConnected == true && batteryParams.right?.isConnected == true) " " else ""
            val rightEar = if (batteryParams.right != null && batteryParams.right!!.isConnected)
                "$leftToRight${context.resources.getString(miheadset_notification_RightEar)}${batteryParams.right!!.battery}%" +
                    (if (batteryParams.right!!.isCharging) "⚡ " else " ")
            else ""

            val contentText: String = leftEar + rightEar + caseBattStr
            val notificationManager = context.getSystemService("notification") as NotificationManager
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    "BTHeadset$address",
                    alias,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    setSound(null, null)
                    setAllowBubbles(true)
                }
            )
            val bundle = Bundle()
            bundle.putParcelable("Device", bluetoothDevice)
            val intent = Intent("com.android.bluetooth.headset.notification")
            intent.putExtra("btData", bundle)
            intent.putExtra("disconnect", "1")
            intent.setIdentifier("BTHeadset$address")
            val disconnectAction = Notification.Action(
                285737079,
                context.resources.getString(miheadset_notification_Disconnect),
                PendingIntent.getBroadcast(context, 0, intent, 201326592)
            )
            // 循环切换降噪模式
            val ancCycleIntent = Intent(HyperOriGAction.ACTION_CYCLE_ANC)
            ancCycleIntent.setIdentifier("BTHeadset$address")
            val moduleContext = context.createPackageContext(
                "com.redwind.hyperorig", Context.CONTEXT_IGNORE_SECURITY
            )
            val headsetIcon = Icon.createWithBitmap(
                BitmapFactory.decodeResource(moduleContext.resources, R.drawable.img_box_mini
                )
            )
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                Intent("com.redwind.hyperorig.action.show_pods_ui").apply {
                    setClassName("com.redwind.hyperorig", "com.redwind.hyperorig.PopupActivity")
                    putExtra("android.bluetooth.device.extra.DEVICE", bluetoothDevice)
                    putExtra("bluetoothaddress", bluetoothDevice.address)
                    putExtra("device_name", alias)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val focusExtras = FocusNotification.buildV3 {
                val logo = createPicture("key_headset", headsetIcon)
                enableFloat = true
                ticker = alias ?: ""
                updatable = true

                iconTextInfo {
                    animIconInfo{
                        type = 0
                        src = logo
                    }
                    title = alias ?: ""
                    content = contentText
                }

                island {
                    islandProperty = 1
                    bigIslandArea {
                        imageTextInfoLeft {
                            type = 1
                            picInfo {
                                type = 1
                                pic = logo
                            }
                        }
                        imageTextInfoRight {
                            type = 2
                            textInfo {
                                title = alias ?: ""
                                content = contentText
                            }
                        }
                    }
                }

                textButton {
                    addActionInfo {
                        val ancLabel = when (ancMode) {
                            3 -> moduleContext.getString(R.string.anc_notif_nc)
                            4 -> moduleContext.getString(R.string.anc_notif_deep)
                            5 -> moduleContext.getString(R.string.anc_notif_experiment)
                            2 -> moduleContext.getString(R.string.anc_notif_transparency)
                            6 -> moduleContext.getString(R.string.anc_notif_wind)
                            else -> moduleContext.getString(R.string.anc_notif_off)
                        }
                        val ancAction = Notification.Action.Builder(
                            Icon.createWithResource(context, android.R.drawable.ic_lock_silent_mode),
                            ancLabel,
                            PendingIntent.getBroadcast(context, 1, ancCycleIntent, 201326592)
                        ).build()
                        action = createAction("key_anc_cycle", ancAction)
                        actionTitle = ancLabel
                    }
                    addActionInfo {
                        val disconnectLabel = moduleContext.getString(R.string.notification_btn_disconnect)
                        val disconnectIntent = Intent("com.android.bluetooth.headset.notification").apply {
                            putExtra("btData", bundle)
                            putExtra("disconnect", "1")
                            setIdentifier("BTHeadset$address")
                        }
                        val disconnectAction2 = Notification.Action.Builder(
                            Icon.createWithResource(context, android.R.drawable.ic_delete),
                            disconnectLabel,
                            PendingIntent.getBroadcast(context, 2, disconnectIntent, 201326592)
                        ).build()
                        action = createAction("key_disconnect", disconnectAction2)
                        actionTitle = disconnectLabel
                    }
                }
            }
            // AOD 息屏显示
            if (focusExtras != null) {
                val aodParts = mutableListOf<String>()
                if (batteryParams.left?.isConnected == true)
                    aodParts.add("L ${batteryParams.left!!.battery}%")
                if (batteryParams.right?.isConnected == true)
                    aodParts.add("R ${batteryParams.right!!.battery}%")
                val aodTitle = aodParts.joinToString(" | ")
                try {
                    val json = org.json.JSONObject(focusExtras.getString("miui.focus.param") ?: "{}")
                    val pv2 = json.optJSONObject("param_v2") ?: org.json.JSONObject()
                    pv2.put("aodTitle", aodTitle)
                    pv2.put("aodPic", "key_headset")
                    json.put("param_v2", pv2)
                    focusExtras.putString("miui.focus.param", json.toString())
                } catch (_: Exception) {}
            }
            notificationManager.notifyAsUser(
                "BTHeadset$address",
                10003,
                Notification.Builder(context, "BTHeadset$address")
                    .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                    .setWhen(0L)
                    .setTicker(alias)
                    .setDefaults(-1)
                    .setContentTitle(alias)
                    .setContentText(contentText)
                    .setContentIntent(pendingIntent)
                    .setDeleteIntent(deleteIntent(context, bluetoothDevice))
                    .setColor(context.getColor(system_notification_accent_color))
                    .addAction(disconnectAction)
                    .apply { focusExtras?.let { addExtras(it) } }
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .build(),
                SystemApisUtils.getUserAllUserHandle()
            )
        } catch (e: Exception) {
            Log.e("HyperOriG", "Failed to create Pod Notification", e)
        }
    }

    private fun deleteIntent(context: Context, bluetoothDevice: BluetoothDevice): PendingIntent {
        return PendingIntent.getBroadcast(
            context, 0,
            Intent("com.android.bluetooth.headset.notification.cancle").apply {
                putExtra("android.bluetooth.device.extra.DEVICE", bluetoothDevice)
            },
            201326592
        )
    }
}
