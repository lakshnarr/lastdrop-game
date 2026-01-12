package earth.lastdrop.app.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import earth.lastdrop.app.R
import earth.lastdrop.app.ChanceCard

/**
 * ChanceCardSelectionDialog - Shows 6 random chance cards for dice selection
 * 
 * Card distribution (provided by caller):
 * - 2 cards from Tier A (1-10): Safe/mild effects
 * - 2 cards from Tier B (11-14): Special abilities  
 * - 2 cards from Tier C (15-20): High risk/negative effects
 * 
 * Player rolls dice (1-6) to select their card.
 */
class ChanceCardSelectionDialog(
    context: Context,
    private val sixCards: List<ChanceCard>,  // The 6 pre-selected cards
    private val onCardSelected: (ChanceCard, Int) -> Unit,  // (selectedCard, diceRoll)
    private val onDiceRollRequested: () -> Unit  // Callback to request dice roll
) : Dialog(context) {

    private var isWaitingForDice = true
    private var hasSelectedCard = false
    
    // Card views
    private lateinit var cardViews: List<CardView>
    private lateinit var cardImages: List<ImageView>
    private lateinit var cardNumbers: List<TextView>
    private lateinit var titleText: TextView
    private lateinit var instructionText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_chance_card_selection)
        
        // Make dialog full-width with dark transparent background
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.parseColor("#CC000000")))
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        }
        setCancelable(false)
        
        initViews()
        displayCards()
        animateCardsIn()
        
        // Request dice roll after cards are shown
        instructionText.postDelayed({
            onDiceRollRequested()
        }, 1500)
    }
    
    private fun initViews() {
        titleText = findViewById(R.id.tvChanceTitle)
        instructionText = findViewById(R.id.tvChanceInstruction)
        
        cardViews = listOf(
            findViewById(R.id.cardSlot1),
            findViewById(R.id.cardSlot2),
            findViewById(R.id.cardSlot3),
            findViewById(R.id.cardSlot4),
            findViewById(R.id.cardSlot5),
            findViewById(R.id.cardSlot6)
        )
        
        cardImages = listOf(
            findViewById(R.id.imgCard1),
            findViewById(R.id.imgCard2),
            findViewById(R.id.imgCard3),
            findViewById(R.id.imgCard4),
            findViewById(R.id.imgCard5),
            findViewById(R.id.imgCard6)
        )
        
        cardNumbers = listOf(
            findViewById(R.id.tvCardNumber1),
            findViewById(R.id.tvCardNumber2),
            findViewById(R.id.tvCardNumber3),
            findViewById(R.id.tvCardNumber4),
            findViewById(R.id.tvCardNumber5),
            findViewById(R.id.tvCardNumber6)
        )
    }
    
    private fun displayCards() {
        sixCards.forEachIndexed { index, card ->
            // Load card image from assets
            try {
                val inputStream = context.assets.open("chance/${card.number}.png")
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                cardImages[index].setImageBitmap(bitmap)
            } catch (e: Exception) {
                // Fallback to water drop icon
                cardImages[index].setImageResource(R.drawable.ic_drop)
            }
            
            // Set dice number (1-6)
            cardNumbers[index].text = (index + 1).toString()
            
            // Initially show cards with scale animation
            cardViews[index].alpha = 0f
            cardViews[index].scaleX = 0.5f
            cardViews[index].scaleY = 0.5f
        }
    }
    
    private fun animateCardsIn() {
        cardViews.forEachIndexed { index, cardView ->
            cardView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(400)
                .setStartDelay(index * 100L)
                .setInterpolator(OvershootInterpolator(1.2f))
                .start()
        }
    }
    
    /**
     * Called when dice roll result is received (1-6)
     */
    fun onDiceRollResult(diceValue: Int) {
        if (!isWaitingForDice || hasSelectedCard) return
        if (diceValue < 1 || diceValue > 6) return
        
        isWaitingForDice = false
        hasSelectedCard = true
        
        val selectedIndex = diceValue - 1
        val selectedCard = sixCards[selectedIndex]
        
        // Highlight selected card
        highlightSelectedCard(selectedIndex)
        
        // Update instruction
        instructionText.text = "You rolled $diceValue! Card selected!"
        
        // Dismiss and callback after animation
        cardViews[selectedIndex].postDelayed({
            onCardSelected(selectedCard, diceValue)
            dismiss()
        }, 1500)
    }
    
    private fun highlightSelectedCard(selectedIndex: Int) {
        cardViews.forEachIndexed { index, cardView ->
            if (index == selectedIndex) {
                // Selected card - pulse and glow
                cardView.animate()
                    .scaleX(1.15f)
                    .scaleY(1.15f)
                    .setDuration(300)
                    .setInterpolator(OvershootInterpolator(2f))
                    .start()
                
                // Add glow effect
                cardView.cardElevation = 24f
                cardView.setCardBackgroundColor(Color.parseColor("#FFFFCC"))
            } else {
                // Non-selected cards - fade out
                cardView.animate()
                    .alpha(0.3f)
                    .scaleX(0.9f)
                    .scaleY(0.9f)
                    .setDuration(300)
                    .start()
            }
        }
    }
    
    /**
     * Get the list of 6 cards (for API sync)
     */
    fun getSelectedCards(): List<ChanceCard> = sixCards.toList()
    
    /**
     * Get card numbers as list (for live.html sync)
     */
    fun getCardNumbers(): List<Int> = sixCards.map { it.number }
}
