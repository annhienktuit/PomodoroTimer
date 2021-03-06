package com.annhienktuit.pomodorotimer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.annhienktuit.pomodorotimer.util.NotificationUtil
import com.annhienktuit.pomodorotimer.util.PrefUtil

class TimerExpiredReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        NotificationUtil.showTimerExpired(context)
        //
        PrefUtil.setTimerState(MainActivity.TimerState.Stopped, context)
        PrefUtil.setAlarmSetTime(0, context)
    }
}