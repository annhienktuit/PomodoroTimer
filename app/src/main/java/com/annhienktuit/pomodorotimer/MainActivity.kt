package com.annhienktuit.pomodorotimer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.*
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.annhienktuit.pomodorotimer.util.NotificationUtil
import com.annhienktuit.pomodorotimer.util.PrefUtil
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*

class MainActivity : AppCompatActivity() {
    companion object {
        fun setAlarm(context: Context, nowSeconds: Long, secondsRemaining: Long): Long{
            Log.i("Function: ", "setAlarm")
            val wakeupTime = (secondsRemaining + nowSeconds) * 1000 //convert millisec to sec
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, TimerExpiredReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0)
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, wakeupTime, pendingIntent)
            PrefUtil.setAlarmSetTime(nowSeconds, context)
           return wakeupTime
        }

        fun removeAlarm(context: Context){
            Log.i("Function: ", "removeAlarm")
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, TimerExpiredReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0)
            alarmManager.cancel(pendingIntent)
            PrefUtil.setAlarmSetTime(0, context)
        }

        val nowSeconds:Long
            get() = Calendar.getInstance().timeInMillis/1000
    }
    enum class TimerState {
        Stopped, Paused, Running
    }
    //khai bao bien
    private lateinit var timer: CountDownTimer
    private var timerLengthSeconds: Long = 0
    private var timerState = TimerState.Stopped
    private var secondsRemaining: Long  = 0
    private var countCycle: Int = 0
    private var countFlag: Int = -2
    var lengthInMinutes = PrefUtil.getTimerLength(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); //screen always on
        AppCompatDelegate.setDefaultNightMode(
            AppCompatDelegate.MODE_NIGHT_NO
        ); //turn off night mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val window: Window = window
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.setStatusBarColor(Color.parseColor("#f44336"))
        }
        countFlag++

        fab_start.setOnClickListener { v ->
            startTimer()
            timerState = TimerState.Running
            updateButtons()
        }

        fab_pause.setOnClickListener { v->
            timer.cancel()
            timerState = TimerState.Paused
            updateButtons()
        }
        fab_stop.setOnClickListener { v->
            timer.cancel()
            onTimerFinished()
            textViewDescription.text = "Choose a work from to-do list and press start button"
        }
        textViewCount.text = "$countCycle of 4 pomodoros completed"
        textViewDescription.text = "Choose a work from to-do list and press start button"
        //seekbar
        seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                var timeinSecond = progress
                lengthInMinutes = progress * 5
                textView_countdown.text = "$timeinSecond:00"
                timerLengthSeconds = timeinSecond*60L
                secondsRemaining = timerLengthSeconds
                progress_countdown.max = timerLengthSeconds.toInt()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                //here we can write some code to do something whenever the user touche the seekbar
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                // show some message after user stopped scrolling the seekbar

            }
        })
    }
    override fun onResume() {
        super.onResume()
        Log.i("test", secondsRemaining.toString())
        initTimer()
        countFlag++
        //background
        Log.i("secondremaing", secondsRemaining.toString())
        if(secondsRemaining == lengthInMinutes*60.toLong() && countFlag > 0) {
            timerState = TimerState.Stopped
            if(countCycle < 4) countCycle++
            else countCycle = 0
            updateCountdownUI()
            textViewDescription.text = "Take a break"
        }
        removeAlarm(this)
        NotificationUtil.hideTimerNotification(this)
    }

    override fun onPause() {
        super.onPause()
        if(timerState==TimerState.Running){
            timer.cancel()
            val wakeUpTime = setAlarm(this, nowSeconds, secondsRemaining)
            //background running
            NotificationUtil.showTimerRunning(this,wakeUpTime)
        }
        else if(timerState == TimerState.Paused){
            //show notification
            NotificationUtil.showTimerPaused(this)

        }
        else if(timerState == TimerState.Stopped){
            countCycle--
        }
        PrefUtil.setPreviousTimerLengthSeconds(timerLengthSeconds, this)
        PrefUtil.setSecondsRemaining(secondsRemaining, this)
        PrefUtil.setTimerState(timerState, this)
    }

    fun getCycle(): Int{
        return countCycle
    }

    private fun initTimer(){
        Log.i("Function: ", "initTimer")
        timerState = PrefUtil.getTimerState(this)
        if(timerState == TimerState.Stopped){
            setNewTimerLength()
        }
        else {
            setPreviousTimerLength()
        }
        secondsRemaining = if (timerState == TimerState.Running || timerState == TimerState.Paused){
            PrefUtil.getSecondsRemaining(this)
        }
        else
            timerLengthSeconds
        //
        val alarmSetTime = PrefUtil.getAlarmSetTime(this)
        if(alarmSetTime > 0) {
            secondsRemaining -= nowSeconds - alarmSetTime
        }
        if(secondsRemaining <= 0) {
            onTimerFinished()
        }
        //continue count
        else if(timerState == TimerState.Running)
            startTimer()
        updateButtons()
        updateCountdownUI()
    }

     private fun onTimerFinished(){
        timerState = TimerState.Stopped
        setNewTimerLength()
        //running
        if(textView_countdown.text == "0:00"){
            setFinishedPomodoro()
        }
        progress_countdown.progress = 0
        PrefUtil.setSecondsRemaining(timerLengthSeconds, this)
        secondsRemaining = timerLengthSeconds
        updateButtons()
        updateCountdownUI()

    }

    private fun startTimer(){
        Log.i("Function: ", "startTimer")
        timerState = TimerState.Running
        textViewDescription.text = "Focus on your work until the timer rings"
        timer = object: CountDownTimer(secondsRemaining * 1000, 1000) {
            override fun onFinish() = onTimerFinished()
            override fun onTick(millisUltilFinished: Long){
                secondsRemaining = millisUltilFinished / 1000
                updateCountdownUI()
            }
        }.start()
    }

    private fun setFinishedPomodoro(){
        val eventually: MediaPlayer = MediaPlayer.create(this, R.raw.eventually)
        eventually.start()
        if(countCycle < 4) countCycle++
        else countCycle = 0
        textViewDescription.text = "Take a break"
    }

    private fun setNewTimerLength(){
        Log.i("Function: ", "setNewTimerLength")
        //val lengthInMinutes = PrefUtil.getTimerLength(this)
        var lengthInMinutes = seekBar.progress
        timerLengthSeconds = lengthInMinutes*60L
        progress_countdown.max = timerLengthSeconds.toInt()
    }

    private fun setPreviousTimerLength(){
        Log.i("Function: ", "setPreviousTimerLength")
        timerLengthSeconds = PrefUtil.getPreviousTimerLengthSeconds(this)
        progress_countdown.max = timerLengthSeconds.toInt()
    }

    private fun updateCountdownUI(){
        val minutesUntilFinished = secondsRemaining / 60
        val secondsInMinuteUntilFinished = secondsRemaining - minutesUntilFinished*60
        val secondsStr = secondsInMinuteUntilFinished.toString()
        val minutesStr = {
            if(minutesUntilFinished < 10) "0" + minutesUntilFinished.toString()
            else minutesUntilFinished.toString()
        }
        textView_countdown.text = "$minutesUntilFinished:${
            if(secondsStr.length == 2) secondsStr
            else "0" + secondsStr
        }"
        textViewCount.text = "$countCycle of 4 pomodoros completed"
        progress_countdown.progress = (timerLengthSeconds - secondsRemaining).toInt()
    }

    private fun updateButtons(){
        when(timerState){
            TimerState.Running -> {
                fab_start.isEnabled = false
                fab_stop.isEnabled = true
                fab_pause.isEnabled = true
            }
            TimerState.Stopped -> {
                fab_start.isEnabled = true
                fab_stop.isEnabled = false
                fab_pause.isEnabled = false
            }
            TimerState.Paused -> {
                fab_start.isEnabled = true
                fab_stop.isEnabled = true
                fab_pause.isEnabled = false
            }
        }
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_info -> {
                val intent = Intent(this, AboutActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

