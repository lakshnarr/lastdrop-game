package earth.lastdrop.app

import android.app.Application
import android.util.Log

/**
 * Application class to initialize global services and logging
 */
class LastDropApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize debug logger at app start
        DebugFileLogger.init(this)
        DebugFileLogger.i("App", "=== LastDropApplication onCreate ===")
        DebugFileLogger.i("App", "App package: ${packageName}")
        DebugFileLogger.i("App", "Version: ${packageManager.getPackageInfo(packageName, 0).versionName}")
        DebugFileLogger.i("App", "Build: ${packageManager.getPackageInfo(packageName, 0).versionCode}")
        
        Log.d("LastDropApplication", "Debug logger initialized")
    }
}
