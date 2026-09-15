package com.redwind.hyperorig.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.redwind.hyperorig.utils.LogLevels
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.core.content.FileProvider
import com.redwind.hyperorig.R
import com.redwind.hyperorig.utils.RuntimeLog
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TabRow
import androidx.compose.foundation.shape.RoundedCornerShape
import com.redwind.hyperorig.utils.PollSettings
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Settings
import androidx.compose.animation.AnimatedVisibility
import android.content.SharedPreferences
import com.redwind.hyperorig.HyperOriGApp
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperSwitch

@Composable
fun SettingsPage(
    modifier: Modifier = Modifier,
    themeMode: MutableState<Int> = mutableStateOf(0),
    onThemeModeChange: (Int) -> Unit = {},
    openorig: MutableState<Boolean> = mutableStateOf(false),
    onOpenorigChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val showorigWarning = remember { mutableStateOf(false) }
    val showLog = remember { mutableStateOf(false) }
    val logText = remember { mutableStateOf("") }
    val logPrefs = remember { context.getSharedPreferences(LogLevels.PREFS_NAME, Context.MODE_PRIVATE) }
    val logLevelsExpanded = remember { mutableStateOf(false) }
    val logMask = remember { mutableStateOf(LogLevels.read(logPrefs)) }
    val pollValue = remember { mutableStateOf(PollSettings.read(logPrefs)) }
    val themeOptions = listOf(
        stringResource(R.string.theme_follow_system),
        stringResource(R.string.theme_light),
        stringResource(R.string.theme_dark)
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                SuperDropdown(
                    title = stringResource(R.string.theme_title),
                    items = themeOptions,
                    selectedIndex = themeMode.value,
                    onSelectedIndexChange = { onThemeModeChange(it) }
                )
            }
        }

        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 0.dp)) {
                SuperSwitch(
                    title = stringResource(R.string.open_orig),
                    summary = stringResource(R.string.open_orig_summary),
                    checked = openorig.value,
                    onCheckedChange = {
                        if (it) {
                            showorigWarning.value = true
                        } else {
                            onOpenorigChange(false)
                        }
                    }
                )
            }
        }

        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                BasicComponent(
                    title = "运行日志",
                    summary = "查看并导出模块运行日志（反馈问题时使用）",
                    onClick = {
                        logText.value = RuntimeLog.snapshot().joinToString("\n").ifEmpty { "(暂无日志)" }
                        showLog.value = true
                    }
                )
                // 日志开关：左侧齿轮（点击在其下方内联展开等级复选），右侧开关
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { logLevelsExpanded.value = !logLevelsExpanded.value }) {
                        Icon(
                            imageVector = MiuixIcons.Settings,
                            contentDescription = "日志级别",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "日志",
                        modifier = Modifier.weight(1f),
                        fontSize = 16.sp
                    )
                    // 开关状态 = 是否存在任一已勾选等级 -> 与复选双向同步
                    Switch(
                        logMask.value != LogLevels.OFF,
                        { on ->
                            val mask = if (on) {
                                if (logMask.value == LogLevels.OFF) LogLevels.DEFAULT_ON else logMask.value
                            } else {
                                LogLevels.OFF
                            }
                            logMask.value = mask
                            LogLevels.write(logPrefs, HyperOriGApp.xposedService, mask)
                        }
                    )
                }
                AnimatedVisibility(visible = logLevelsExpanded.value) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 16.dp, bottom = 12.dp)
                    ) {
                        LogCheckRow("Info", LogLevels.INFO, logMask, logPrefs)
                        LogCheckRow("Debug", LogLevels.DEBUG, logMask, logPrefs)
                        LogCheckRow("Warn", LogLevels.WARN, logMask, logPrefs)
                        LogCheckRow("Error", LogLevels.ERROR, logMask, logPrefs)
                    }
                }
            }
        }

        item {
            // vertical = 0：与上一张卡片（bottom=12）合计 12dp，和其余卡片间距保持一致
            Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 0.dp)) {
                BasicComponent(
                    title = "轮询间隔",
                    summary = PollSettings.labelOf(pollValue.value)
                )
                // 分段控件左右内缩，作为卡片内的独立层级，避免贴边而失去层次
                // 外层裁成与选中项一致的圆角，避免整体呈矩形
                Box(
                    modifier = Modifier
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                        // 12dp 即 miuix TabRow 自身的圆角（TabRowDefaults.TabRowCornerRadius），
                        // 与选中项圆角保持一致
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    TabRow(
                        PollSettings.LABELS,
                        PollSettings.indexOf(pollValue.value),
                        { index ->
                            val value = PollSettings.OPTIONS.getOrElse(index) { PollSettings.DEFAULT }
                            pollValue.value = value
                            PollSettings.write(logPrefs, HyperOriGApp.xposedService, value)
                        }
                    )
                }
            }
        }

        item {
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 12.dp, bottom = 12.dp)
            )
        }

        item {
            Text(
                text = "我们",
                modifier = Modifier.padding(start = 28.dp, top = 2.dp, bottom = 0.dp),
                fontSize = 14.sp,
                color = Color.Gray
            )
        }
        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                BasicComponent(
                    title = "开发者",
                )
                BasicComponent(
                    title = "KiriChen | 陈有为",
                    summary = "追风团队成员",
                    onClick = {
                        Intent(Intent.ACTION_VIEW).apply {
                            this.data = Uri.parse("https://www.coolapk.com/u/22989975")
                            context.startActivity(this)
                        }
                    }
                )
                BasicComponent(
                    title = "加入内部测试、问题报告",
                )
                BasicComponent(
                    title = "AAA陈皮糖批发市场",
                    summary = "官方 Q Group",
                    onClick = {
                        Intent(Intent.ACTION_VIEW).apply {
                            this.data = Uri.parse("https://qun.qq.com/universal-share/share?ac=1&authKey=veiuXz4u6NenB8EoNjITTU5l%2Bzs8i1dbgL1w65GRBaP2Uj4z734aJKhPcgt0A2P6&busi_data=eyJncm91cENvZGUiOiI3NDEyNjcyOTgiLCJ0b2tlbiI6IlNNbkVGekFYaUVxRVZicnRUQ0ozRGRoS2dpNDdack9oUzhaVGVjTGNwZzBoN2ZuRjFPdzJSc3ZhYlA4UjZJNU4iLCJ1aW4iOiIxODQxOTM4MDQwIn0%3D&data=8ODFZUZTUtydXUELqSTZnuyFLThRvTZ3Ail6wXx3-VMJr8B14JSzrX2SMI5Q0_yF5MqzKrm2kq8LA7IZ4ci1Cw&svctype=4&tempid=h5_group_info")
                            context.startActivity(this)
                        }
                    }
                )
            }
        }
        item {
            Text(
                text = "开放",
                modifier = Modifier.padding(start = 28.dp, top = 0.dp, bottom = 0.dp),
                fontSize = 14.sp,
                color = Color.Gray
            )
        }

        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                BasicComponent(
                    title = "项目开放",
                )
                BasicComponent(
                    title = "HyperOriG",
                    summary = "https://github.com/KiriChen-Wind/HyperOriG",
                    onClick = {
                        Intent(Intent.ACTION_VIEW).apply {
                            this.data = Uri.parse("https://github.com/KiriChen-Wind/HyperOriG")
                            context.startActivity(this)
                        }
                    }
                )
                BasicComponent(
                    title = "本项目引用的开放代码",
                )
                BasicComponent(
                    title = "HyperPods",
                    summary = "https://github.com/Art-Chen/HyperPods",
                    onClick = {
                        Intent(Intent.ACTION_VIEW).apply {
                            this.data = Uri.parse("https://github.com/Art-Chen/HyperPods")
                            context.startActivity(this)
                        }
                    }
                )
                BasicComponent(
                    title = "OppoPods",
                    summary = "https://github.com/Leaf-lsgtky/OppoPods",
                    onClick = {
                        Intent(Intent.ACTION_VIEW).apply {
                            this.data = Uri.parse("https://github.com/Leaf-lsgtky/OppoPods")
                            context.startActivity(this)
                        }
                    }
                )
                BasicComponent(
                    title = "OppoPods-Enhanced",
                    summary = "https://github.com/1812z/OppoPods",
                    onClick = {
                        Intent(Intent.ACTION_VIEW).apply {
                            this.data = Uri.parse("https://github.com/1812z/OppoPods")
                            context.startActivity(this)
                        }
                    }
                )
                BasicComponent(
                    title = "NiceHCK Controller",
                    summary = "https://github.com/ZaeXT/NiceHCK_Controller",
                    onClick = {
                        Intent(Intent.ACTION_VIEW).apply {
                            this.data = Uri.parse("https://github.com/ZaeXT/NiceHCK_Controller")
                            context.startActivity(this)
                        }
                    }
                )
                BasicComponent(
                    title = "Miuix",
                    summary = "https://github.com/compose-miuix-ui/miuix",
                    onClick = {
                        Intent(Intent.ACTION_VIEW).apply {
                            this.data = Uri.parse("https://github.com/compose-miuix-ui/miuix")
                            context.startActivity(this)
                        }
                    }
                )
                BasicComponent(
                    title = "LibXposed API",
                    summary = "https://github.com/libxposed/api",
                    onClick = {
                        Intent(Intent.ACTION_VIEW).apply {
                            this.data = Uri.parse("https://github.com/libxposed/api")
                            context.startActivity(this)
                        }
                    }
                )
            }
        }
    }

    SuperDialog(
        title = stringResource(R.string.orig_warning_title),
        show = showorigWarning,
        onDismissRequest = {
            showorigWarning.value = false
        }
    ) {
        Text(
            text = stringResource(R.string.orig_warning),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Start
        )
        TextButton(
            text = stringResource(R.string.confirm),
            onClick = {
                showorigWarning.value = false
                onOpenorigChange(true)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColorsPrimary()
        )
    }

    SuperDialog(
        title = "运行日志",
        show = showLog,
        onDismissRequest = { showLog.value = false }
    ) {
        Text(
            text = logText.value,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp)
                .verticalScroll(rememberScrollState()),
            fontSize = 11.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(text = "刷新", onClick = {
                logText.value = RuntimeLog.snapshot().joinToString("\n").ifEmpty { "(暂无日志)" }
            })
            TextButton(text = "复制", onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("HyperOriG-Log", logText.value))
            })
            TextButton(text = "分享", onClick = { shareLog(context) })
            TextButton(text = "清空", onClick = {
                RuntimeLog.clear()
                logText.value = "(暂无日志)"
            })
        }
    }

}

@Composable
private fun LogCheckRow(
    title: String,
    bit: Int,
    mask: MutableState<Int>,
    prefs: SharedPreferences
) {
    val checked = (mask.value and bit) != 0
    val apply = { on: Boolean ->
        val next = if (on) (mask.value or bit) else (mask.value and bit.inv())
        mask.value = next
        LogLevels.write(prefs, HyperOriGApp.xposedService, next)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { apply(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked,
            { apply(it) },
            Modifier.size(22.dp)
        )
        Text(
            text = title,
            modifier = Modifier.padding(start = 12.dp),
            fontSize = 15.sp
        )
    }
}

private fun shareLog(context: Context) {
    val file = RuntimeLog.exportToFile(context) ?: return
    val uri = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull() ?: return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "HyperOriG 运行日志")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "导出运行日志").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}
