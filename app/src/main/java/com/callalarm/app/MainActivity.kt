package com.callalarm.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var store: AlarmStore
    private lateinit var listBox: LinearLayout
    private lateinit var subtitle: TextView
    private lateinit var banner: TextView
    private var bannerAction: (() -> Unit)? = null

    private val bg = Color.parseColor("#0F1419")
    private val cardColor = Color.parseColor("#1B222B")
    private val green = Color.parseColor("#2EB84B")
    private val muted = Color.parseColor("#8B97A5")

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AlarmStore(this)
        buildUi()
        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        updateBanner()
    }

    // ---------------------------------------------------------------- helpers

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun pill(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 14f
        setTextColor(Color.WHITE)
        setPadding(dp(14), dp(8), dp(14), dp(8))
        background = rounded(Color.parseColor("#26303B"), 20)
        setOnClickListener { onClick() }
    }

    // ---------------------------------------------------------------- main UI

    private fun buildUi() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(bg)

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(20), dp(24), dp(20), dp(8))

        val titleCol = LinearLayout(this)
        titleCol.orientation = LinearLayout.VERTICAL
        titleCol.addView(TextView(this).apply {
            text = "Call Alarm"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        subtitle = TextView(this).apply {
            textSize = 14f
            setTextColor(muted)
        }
        titleCol.addView(subtitle)
        header.addView(titleCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(pill("Test call") { testCall() })
        root.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        banner = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#1A1200"))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = rounded(Color.parseColor("#FFB300"), 12)
            visibility = View.GONE
            setOnClickListener { bannerAction?.invoke() }
        }
        val bannerLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        bannerLp.setMargins(dp(20), dp(8), dp(20), dp(4))
        root.addView(banner, bannerLp)

        listBox = LinearLayout(this)
        listBox.orientation = LinearLayout.VERTICAL
        listBox.setPadding(dp(20), dp(12), dp(20), dp(12))
        val scroll = ScrollView(this)
        scroll.addView(listBox)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val add = TextView(this).apply {
            text = "+  Add alarm"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(green, 28)
            setOnClickListener { showEditor(null) }
        }
        val addLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        addLp.setMargins(dp(20), dp(8), dp(20), dp(20))
        root.addView(add, addLp)

        setContentView(root)
    }

    private fun refresh() {
        val alarms = store.load().sortedWith(compareBy({ it.hour }, { it.minute }))
        listBox.removeAllViews()

        if (alarms.isEmpty()) {
            listBox.addView(TextView(this).apply {
                text = "No alarms yet.\nTap \"Add alarm\" to set your first call."
                textSize = 16f
                setTextColor(muted)
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(60), dp(16), dp(16))
            })
        }
        for (a in alarms) {
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(12)
            listBox.addView(buildCard(a), lp)
        }

        val now = System.currentTimeMillis()
        val next = alarms.filter { it.enabled }.minOfOrNull { it.nextTriggerMillis(now) }
        subtitle.text = if (next == null) "No alarms are on" else "Next call in ${Fmt.until(next - now)}"
    }

    private fun buildCard(a: Alarm): View {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.HORIZONTAL
        card.gravity = Gravity.CENTER_VERTICAL
        card.background = rounded(cardColor, 18)
        card.setPadding(dp(18), dp(14), dp(14), dp(14))

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.alpha = if (a.enabled) 1f else 0.45f
        col.addView(TextView(this).apply {
            text = Fmt.time(this@MainActivity, a.hour, a.minute)
            textSize = 34f
            setTextColor(Color.WHITE)
        })
        col.addView(TextView(this).apply {
            text = a.label
            textSize = 16f
            setTextColor(Color.parseColor("#C9D1D9"))
        })
        col.addView(TextView(this).apply {
            text = Fmt.days(a.days)
            textSize = 13f
            setTextColor(muted)
        })
        card.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val sw = Switch(this)
        sw.isChecked = a.enabled
        sw.setOnCheckedChangeListener { _, on -> toggle(a, on) }
        card.addView(sw)

        card.setOnClickListener { showEditor(a) }
        return card
    }

    private fun toggle(a: Alarm, on: Boolean) {
        a.enabled = on
        store.upsert(a)
        if (on) {
            val t = AlarmScheduler.schedule(this, a)
            toast("Call in ${Fmt.until(t - System.currentTimeMillis())}")
        } else {
            AlarmScheduler.cancel(this, a.id)
        }
        refresh()
    }

    private fun testCall() {
        startForegroundService(
            AlarmService.startIntent(
                this, -1, "Test Caller",
                "Hello! This is a test of your wake up call. Everything is working."
            )
        )
    }

    // ---------------------------------------------------------------- editor dialog

    private fun showEditor(existing: Alarm?) {
        var hour = existing?.hour ?: 7
        var minute = existing?.minute ?: 0
        var days = existing?.days ?: 0

        val form = LinearLayout(this)
        form.orientation = LinearLayout.VERTICAL
        form.setPadding(dp(22), dp(4), dp(22), dp(8))

        val timeView = TextView(this).apply {
            textSize = 46f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, dp(6), 0, dp(10))
            text = Fmt.time(this@MainActivity, hour, minute)
        }
        timeView.setOnClickListener {
            TimePickerDialog(
                this,
                { _, h, m ->
                    hour = h
                    minute = m
                    timeView.text = Fmt.time(this, h, m)
                },
                hour, minute, DateFormat.is24HourFormat(this)
            ).show()
        }
        form.addView(timeView)

        // Day-of-week chips
        form.addView(TextView(this).apply {
            text = "Repeat (none selected = ring once)"
            textSize = 12f
            setTextColor(muted)
        })
        val dayNames = arrayOf("M", "T", "W", "T", "F", "S", "S")
        val chipRow = LinearLayout(this)
        chipRow.orientation = LinearLayout.HORIZONTAL
        chipRow.gravity = Gravity.CENTER
        chipRow.setPadding(0, dp(8), 0, dp(8))
        for (i in 0..6) {
            val chip = TextView(this)
            chip.text = dayNames[i]
            chip.textSize = 14f
            chip.gravity = Gravity.CENTER
            chip.setTextColor(Color.WHITE)
            fun paint() {
                val on = (days and (1 shl i)) != 0
                chip.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (on) green else Color.parseColor("#2C3744"))
                }
            }
            paint()
            chip.setOnClickListener {
                days = days xor (1 shl i)
                paint()
            }
            val lp = LinearLayout.LayoutParams(dp(38), dp(38))
            lp.marginStart = dp(3)
            lp.marginEnd = dp(3)
            chipRow.addView(chip, lp)
        }
        form.addView(chipRow)

        form.addView(TextView(this).apply {
            text = "Caller name"
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(8), 0, 0)
        })
        val nameEt = EditText(this).apply {
            hint = "e.g. Mom, Coach, Boss"
            setText(existing?.label ?: "Wake Up")
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(muted)
        }
        form.addView(nameEt)

        form.addView(TextView(this).apply {
            text = "What they say when you pick up (optional)"
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(12), 0, 0)
        })
        val msgEt = EditText(this).apply {
            hint = "Leave empty for a default wake-up message"
            setText(existing?.message ?: "")
            setTextColor(Color.WHITE)
            setHintTextColor(muted)
            minLines = 2
            maxLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        form.addView(msgEt)

        val scroll = ScrollView(this)
        scroll.addView(form)

        val builder = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(if (existing == null) "New alarm" else "Edit alarm")
            .setView(scroll)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val list = store.load()
                val alarm = existing ?: Alarm(store.nextId(list), hour, minute, "", "", 0, true)
                alarm.hour = hour
                alarm.minute = minute
                alarm.days = days
                alarm.label = nameEt.text.toString().trim().ifEmpty { "Wake Up" }
                alarm.message = msgEt.text.toString().trim()
                alarm.enabled = true
                store.upsert(alarm)
                val t = AlarmScheduler.schedule(this, alarm)
                toast("Call in ${Fmt.until(t - System.currentTimeMillis())}")
                refresh()
            }
        if (existing != null) {
            builder.setNeutralButton("Delete") { _, _ ->
                AlarmScheduler.cancel(this, existing.id)
                store.delete(existing.id)
                refresh()
            }
        }
        builder.show()
    }

    // ---------------------------------------------------------------- permissions

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun updateBanner() {
        val nm = getSystemService(NotificationManager::class.java)
        var text: String? = null
        var action: (() -> Unit)? = null

        if (!nm.areNotificationsEnabled()) {
            text = "Notifications are off, so the call screen can't pop up. Tap to allow."
            action = {
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                )
            }
        } else if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()) {
            text = "Allow full-screen calls so the call screen shows over your lock screen. Tap to allow."
            action = {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))
                )
            }
        }

        if (text == null) {
            banner.visibility = View.GONE
            bannerAction = null
        } else {
            banner.text = text
            bannerAction = action
            banner.visibility = View.VISIBLE
        }
    }
}
