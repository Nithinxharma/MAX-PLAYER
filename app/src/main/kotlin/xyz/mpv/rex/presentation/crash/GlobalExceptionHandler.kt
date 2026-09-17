package xyz.mpv.rex.presentation.crash

import android.content.Context
import android.content.Intent
import kotlin.system.exitProcess

class GlobalExceptionHandler(
  private val context: Context,
  private val activity: Class<*>,
  private val defaultHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()
) : Thread.UncaughtExceptionHandler {
  
  private val appLaunchTime = System.currentTimeMillis()

  override fun uncaughtException(
    t: Thread,
    e: Throwable,
  ) {
    if (System.currentTimeMillis() - appLaunchTime < 3000) {
      defaultHandler?.uncaughtException(t, e)
      return
    }

    val intent = Intent(context, activity)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
    intent.putExtra("exception", e.stackTraceToString())
    context.startActivity(intent)
    exitProcess(0)
  }
}
