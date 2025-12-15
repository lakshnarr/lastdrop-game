package earth.lastdrop.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    
    private val SPLASH_DELAY = 2500L // 2.5 seconds
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        
        // Hide action bar
        supportActionBar?.hide()
        
        // Navigate to ProfileSelectionActivity after delay
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, ProfileSelectionActivity::class.java))
            finish()
        }, SPLASH_DELAY)
    }
}
