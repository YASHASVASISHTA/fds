package com.callalarm.app

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Date
import java.util.Locale

/**
 * Looks like a phone's incoming-call screen: caller name on top, a red button and a
 * green button at the bottom.
 *
 * The RED button deliberately does nothing - the only way to stop the alarm is to
 * answer with the GREEN one. After answering, a "call" starts: a timer runs and a
 * voice speaks your message to you.
 */
class IncomingCallActivity : Activity() {

    companion object {
        fun intent(ctx: Context, id: Int, label: String, message: String, autoAnswer: Boolean): Intent =
            Intent(ctx, IncomingCallActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                .putExtra(AlarmService.EXTRA_ID, id)
                .putExtra(AlarmService.EXTRA_LABEL, label)
                .putExtra(AlarmService.EXTRA_MESSAGE, message)
                .putExtra(AlarmService.EXTRA_AUTO_ANSWER, autoAnswer)
    }

    private val handler = Handler(Looper.getMainLooper())

    private var label = "Alarm"
    private var message = ""
    private var answered = false
    private var callSeconds = 0

    private lateinit var topText: TextView
    private lateinit var nameView: TextView
    private lateinit var statusView: TextView
    private lateinit var avatarLetter: TextView
    private lateinit var ring: View
    private lateinit var ringingRow: LinearLayout
    private lateinit var callRow: LinearLayout
    private lateinit var declineBtn: View
    private lateinit var answerBtn: View

    private var pulse: ObjectAnimator? = null
    private var bounce: ObjectAnimator? = null

    private var tts: TextToSpeech? = null
    private val followUps = listOf(
        "Hello? Are you still there? Please wake up!",
        "Hey, I'm still on the line. Time to get out of bed!",
        "Come on, wake up! You asked me to call you."
    )
    private var followIdx = 0

    private val stoppedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Ring timed out (missed call) while we were still ringing.
            if (!answered) finishAndRemoveTask()
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            callSeconds++
            statusView.text = String.format(Locale.US, "%02d:%02d", callSeconds / 60, callSeconds % 60)
            if (callSeconds >= 300) {
                endCall()
            } else {
                handler.postDelayed(this, 1000)
            }
        }
    }

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        buildUi()
        registerStoppedReceiver()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null) return
        setIntent(intent)
        stopVoice()
        handler.removeCallbacks(tick)
        handleIntent(intent)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        // Can't back out of a ringing alarm; once answered, back = hang up.
        if (answered) endCall()
    }

    override fun onDestroy() {
        pulse?.cancel()
        bounce?.cancel()
        handler.removeCallbacksAndMessages(null)
        stopVoice()
        try {
            unregisterReceiver(stoppedReceiver)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun registerStoppedReceiver() {
        val filter = IntentFilter(AlarmService.ACTION_STOPPED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stoppedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stoppedReceiver, filter)
        }
    }

    private fun handleIntent(i: Intent) {
        label = i.getStringExtra(AlarmService.EXTRA_LABEL) ?: "Alarm"
        message = i.getStringExtra(AlarmService.EXTRA_MESSAGE) ?: ""
        nameView.text = label
        avatarLetter.text = label.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "A"
        showRinging()
        if (i.getBooleanExtra(AlarmService.EXTRA_AUTO_ANSWER, false)) {
            answer()
        }
    }

    // ---------------------------------------------------------------- UI

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun circleButton(color: Int, iconRes: Int): FrameLayout {
        val f = FrameLayout(this)
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(color)
        f.background = bg
        f.isClickable = true
        f.isFocusable = true
        val img = ImageView(this)
        img.setImageResource(iconRes)
        f.addView(img, FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER))
        return f
    }

    private fun caption(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(Color.parseColor("#C3CBD4"))
        gravity = Gravity.CENTER
        setPadding(0, dp(10), 0, 0)
    }

    private fun column(button: View, label: String): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER_HORIZONTAL
        c.addView(button, LinearLayout.LayoutParams(dp(76), dp(76)))
        c.addView(caption(label))
        return c
    }

    private fun buildUi() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.setPadding(dp(24), dp(72), dp(24), dp(56))
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#26323F"), Color.parseColor("#0A0E13"))
        )

        topText = TextView(this).apply {
            text = "Incoming call"
            textSize = 16f
            letterSpacing = 0.08f
            setTextColor(Color.parseColor("#9AA7B4"))
        }
        root.addView(topText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        nameView = TextView(this).apply {
            textSize = 34f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
        }
        root.addView(nameView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        statusView = TextView(this).apply {
            text = "Mobile"
            textSize = 18f
            setTextColor(Color.parseColor("#9AA7B4"))
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        }
        root.addView(statusView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Avatar with a pulsing ring behind it
        val avatarFrame = FrameLayout(this)
        ring = View(this)
        val ringBg = GradientDrawable()
        ringBg.shape = GradientDrawable.OVAL
        ringBg.setStroke(dp(3), Color.parseColor("#3DDC84"))
        ringBg.setColor(Color.TRANSPARENT)
        ring.background = ringBg
        avatarFrame.addView(ring, FrameLayout.LayoutParams(dp(130), dp(130), Gravity.CENTER))

        avatarLetter = TextView(this).apply {
            textSize = 52f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val avatarBg = GradientDrawable()
        avatarBg.shape = GradientDrawable.OVAL
        avatarBg.setColor(Color.parseColor("#3A4756"))
        avatarLetter.background = avatarBg
        avatarFrame.addView(avatarLetter, FrameLayout.LayoutParams(dp(120), dp(120), Gravity.CENTER))

        val avatarLp = LinearLayout.LayoutParams(dp(190), dp(190))
        avatarLp.topMargin = dp(48)
        root.addView(avatarFrame, avatarLp)

        // spacer
        root.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))

        // Ringing: red + green
        declineBtn = circleButton(Color.parseColor("#E53935"), R.drawable.ic_call_end)
        answerBtn = circleButton(Color.parseColor("#2EB84B"), R.drawable.ic_call)
        declineBtn.setOnClickListener {
            // Intentionally does nothing except a little shake: only Answer stops the alarm.
            ObjectAnimator.ofFloat(declineBtn, View.TRANSLATION_X, 0f, -18f, 18f, -12f, 12f, 0f)
                .setDuration(350).start()
        }
        answerBtn.setOnClickListener { answer() }

        ringingRow = LinearLayout(this)
        ringingRow.orientation = LinearLayout.HORIZONTAL
        ringingRow.addView(column(declineBtn, "Decline"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        ringingRow.addView(column(answerBtn, "Answer"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(ringingRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // In call: a single "end call" button
        val endBtn = circleButton(Color.parseColor("#E53935"), R.drawable.ic_call_end)
        endBtn.setOnClickListener { endCall() }
        callRow = LinearLayout(this)
        callRow.orientation = LinearLayout.VERTICAL
        callRow.gravity = Gravity.CENTER_HORIZONTAL
        callRow.addView(column(endBtn, "End call"))
        callRow.visibility = View.GONE
        root.addView(callRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        setContentView(root)
    }

    private fun showRinging() {
        answered = false
        topText.text = "Incoming call"
        statusView.text = "Mobile"
        ringingRow.visibility = View.VISIBLE
        callRow.visibility = View.GONE

        pulse?.cancel()
        pulse = ObjectAnimator.ofPropertyValuesHolder(
            ring,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.45f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.45f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.8f, 0f)
        ).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            start()
        }

        bounce?.cancel()
        bounce = ObjectAnimator.ofFloat(answerBtn, View.TRANSLATION_Y, 0f, -dp(14).toFloat()).apply {
            duration = 550
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
    }

    // ---------------------------------------------------------------- call logic

    private fun answer() {
        if (answered) return
        answered = true

        pulse?.cancel()
        bounce?.cancel()
        ring.alpha = 0f
        answerBtn.translationY = 0f

        // This is what switches the alarm off (ringtone + vibration stop).
        startService(Intent(this, AlarmService::class.java).setAction(AlarmService.ACTION_STOP))

        topText.text = "On call"
        ringingRow.visibility = View.GONE
        callRow.visibility = View.VISIBLE
        callSeconds = 0
        statusView.text = "00:00"
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 1000)

        startVoice()
    }

    private fun endCall() {
        handler.removeCallbacksAndMessages(null)
        stopVoice()
        finishAndRemoveTask()
    }

    // ---------------------------------------------------------------- the "voice" on the call

    private fun defaultMessage(): String {
        val now = DateFormat.getTimeFormat(this).format(Date())
        return "Hello! This is your wake up call from $label. It's $now. Time to get up!"
    }

    private fun startVoice() {
        stopVoice()
        followIdx = 0
        val text = if (message.isNotBlank()) message else defaultMessage()

        var engine: TextToSpeech? = null
        engine = TextToSpeech(applicationContext) { status ->
            runOnUiThread {
                val e = engine
                if (isDestroyed || !answered || e == null || status != TextToSpeech.SUCCESS) return@runOnUiThread

                val r = e.setLanguage(Locale.getDefault())
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    e.setLanguage(Locale.US)
                }
                e.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                e.setSpeechRate(0.95f)
                e.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        scheduleFollowUp()
                    }

                    override fun onDone(utteranceId: String?) {
                        scheduleFollowUp()
                    }
                })
                handler.postDelayed({ speak(text) }, 800)
            }
        }
        tts = engine
    }

    private fun speak(text: String) {
        if (!answered) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "call-" + System.nanoTime())
    }

    /** Like a person saying "hello? are you still there?" if you go quiet. */
    private fun scheduleFollowUp() {
        if (followIdx >= followUps.size) return
        handler.postDelayed({
            if (answered && followIdx < followUps.size) {
                val line = followUps[followIdx]
                followIdx++
                speak(line)
            }
        }, 9000)
    }

    private fun stopVoice() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
    }
}
