package mattecarra.accapp.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.os.HandlerCompat
import mattecarra.accapp.R
import mattecarra.accapp.receivers.AdvWidgetReceiver
import mattecarra.accapp.utils.LogExt
import xml.*

interface OnAdvWidgetInterface
{
    fun onScreen(screenOn: Boolean)
    fun onPowerState(connectOn: Boolean)
}

class WidgetService : Service(), OnAdvWidgetInterface
{
    private var mAdvWidgetReceiver: AdvWidgetReceiver? = null
    private lateinit var mScreenService: PowerManager
    private lateinit var mWidgetHandler: Handler
    private var isPowerConnected = false
    private var isScreenOn = false

    override fun onBind(intent: Intent?): IBinder?
    {
        return null
    }

    override fun onCreate()
    {
        super.onCreate()

        LogExt().d(javaClass.simpleName, ".onCreate()")
        // Android 8+ kills background services started from widget
        // broadcasts; run as a low-profile foreground service instead.
        startAsForeground()
        mScreenService = getSystemService(POWER_SERVICE) as PowerManager
        mWidgetHandler = HandlerCompat.createAsync(Looper.getMainLooper())
        isScreenOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) mScreenService.isInteractive else mScreenService.isScreenOn
        registerWidgetReceiver()
    }

    private fun startAsForeground()
    {
        try {
            val channelId = "acca_widget_updates"
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    getString(R.string.widget_batteryInfo_name),
                    NotificationManager.IMPORTANCE_MIN
                )
            )
            val notification = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_battery_charging_80)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.widget_batteryInfo_name))
                .setOngoing(true)
                .build()
            startForeground(WIDGET_FGS_ID, notification)
        } catch (ex: Exception) {
            LogExt().e(javaClass.simpleName, "startAsForeground failed: $ex")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int
    {
        super.onStartCommand(intent, flags, startId)
        LogExt().d(javaClass.simpleName, ".onStartCommand(): "+intent?.action)

        when(intent?.action)
        {
            WIDGET_ALL_ENABLED ->
            {
                registerWidgetReceiver()
            }

            WIDGET_ONE_UPDATE ->
            {
                if (mAdvWidgetReceiver != null && isScreenOn) // HAS widget + ScreenON + PowerON = Update)
                {
                    isPowerConnected = intent.getBooleanExtra("isCharging", isPowerConnected)

                    if (isPowerConnected) // power connected
                    {
                        LogExt().d(javaClass.simpleName, ".onStartCommand(): Screen+Power=True, send MSG with 2000 ms delay ")
                        mWidgetHandler.sendMessageDelayed(Message.obtain(mWidgetHandler, Runnable {
                            mWidgetHandler.removeCallbacksAndMessages(null)
                            LogExt().d(javaClass.simpleName, "MainLooperRunnable(): Clear all MSG, send WIDGET_ONE_UPDATE")
                            sendBroadcast(Intent(this, BatteryInfoWidget::class.java).setAction(WIDGET_ONE_UPDATE).putExtras(intent))
                        }), 2500)
                    }
                    else // NO connected
                    {
                        LogExt().d(javaClass.simpleName, ".onStartCommand(): Power=False, send WIDGET_ONE_UPDATE")
                        sendBroadcast(Intent(this, BatteryInfoWidget::class.java).setAction(WIDGET_ONE_UPDATE).putExtras(intent))
                    }
                }
            }

            WIDGET_ALL_DISABLED ->
            {
                stopSelf()
            }
        }

        return START_STICKY;
    }

    override fun onDestroy()
    {
        super.onDestroy()

        LogExt().d(javaClass.simpleName, ".onDestroy()")
        unregisterWidgetReceiver()
        mWidgetHandler.removeCallbacksAndMessages(null)
    }

    //-----------------------------------------------------------------

    private fun registerWidgetReceiver()
    {
        if (mAdvWidgetReceiver == null && BatteryInfoWidget().getAppWidgetIds(this).isNotEmpty())
        {
            LogExt().d(javaClass.simpleName, ".registerWidgetReceiver()")
            mAdvWidgetReceiver = AdvWidgetReceiver()
            val widgetFilter = IntentFilter()
            widgetFilter.addAction(Intent.ACTION_BOOT_COMPLETED)
            widgetFilter.addAction(Intent.ACTION_POWER_CONNECTED)
            widgetFilter.addAction(Intent.ACTION_POWER_DISCONNECTED)
            widgetFilter.addAction(Intent.ACTION_PACKAGE_REPLACED)
            widgetFilter.addAction(Intent.ACTION_SCREEN_ON)
            widgetFilter.addAction(Intent.ACTION_SCREEN_OFF)
            // Android 14+ requires an explicit exported flag for runtime
            // receivers; ours only listens to system broadcasts for this
            // process, so keep it private.
            ContextCompat.registerReceiver(
                this,
                mAdvWidgetReceiver,
                widgetFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            mAdvWidgetReceiver?.setEventInterface(this)
        }
    }

    private fun unregisterWidgetReceiver()
    {
        LogExt().d(javaClass.simpleName, ".unregisterWidgetReceiver()")
        if (mAdvWidgetReceiver != null) unregisterReceiver(mAdvWidgetReceiver)
        mAdvWidgetReceiver?.setEventInterface(null)
        mAdvWidgetReceiver = null
    }

    //--------------------------------------------------------------------

    override fun onScreen(screenOn: Boolean)
    {
        isScreenOn = screenOn
        if (isScreenOn && isPowerConnected) sendBroadcast(Intent(this, BatteryInfoWidget::class.java).setAction(WIDGET_ALL_UPDATE))
    }

    override fun onPowerState(connectOn: Boolean)
    {
        isPowerConnected = connectOn
        if (isPowerConnected && isScreenOn) sendBroadcast(Intent(this, BatteryInfoWidget::class.java).setAction(WIDGET_ALL_UPDATE))
    }

    //--------------------------------------------------------------------

    fun runSelfIntent(context: Context, intent: Intent)
    {
        intent.setClass(context, WidgetService::class.java)
        try
        {
            // The widget provider runs on broadcasts; on Android 8+ a plain
            // startService from the background is rejected, so go through
            // the foreground-service path (onCreate promotes to foreground).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ContextCompat.startForegroundService(context, intent)
            else context.startService(intent)
        }
        catch (ignored: Exception)
        {
            LogExt().e(javaClass.simpleName, "Error starting WidgetService: $ignored")
        }
    }

    //------------------------------------------------------------------------------------
}