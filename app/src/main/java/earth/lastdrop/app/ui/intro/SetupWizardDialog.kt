package earth.lastdrop.app.ui.intro

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import earth.lastdrop.app.R

/**
 * Kid-friendly setup wizard for game configuration
 * Steps through: Board → Dice Question → Dice Count → Dice Connect → Web Server → Welcome
 */
class SetupWizardDialog(
    context: Context,
    private val playerNames: List<String>,
    private val onSpeak: (String) -> Unit,
    private val onConnectBoard: (onResult: (Boolean) -> Unit) -> Unit,
    private val onConnectDice: (diceCount: Int, onResult: (Boolean) -> Unit) -> Unit,
    private val onConnectServer: (onResult: (Boolean) -> Unit) -> Unit,
    private val onComplete: (boardConnected: Boolean, diceConnected: Boolean, serverConnected: Boolean, diceCount: Int) -> Unit,
    private val onQuit: () -> Unit
) : Dialog(context) {

    private lateinit var tvStepTitle: TextView
    private lateinit var tvStepDescription: TextView
    private lateinit var ivStepIcon: ImageView
    private lateinit var progressIndicator: LinearLayout
    private lateinit var btnYes: Button
    private lateinit var btnNo: Button
    private lateinit var btnRetry: Button
    private lateinit var loadingProgress: ProgressBar
    private lateinit var tvLoadingText: TextView
    private lateinit var layoutButtons: LinearLayout
    private lateinit var layoutLoading: LinearLayout

    private var currentStep = 0
    private var boardConnected = false
    private var diceConnected = false
    private var serverConnected = false
    private var selectedDiceCount = 1  // 1 or 2 dice
    private var wantsSmartDice = false
    private var allSkipped = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_setup_wizard)
        
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
        setCancelable(false)

        initViews()
        showStep(0)
    }

    private fun initViews() {
        tvStepTitle = findViewById(R.id.tvStepTitle)
        tvStepDescription = findViewById(R.id.tvStepDescription)
        ivStepIcon = findViewById(R.id.ivStepIcon)
        progressIndicator = findViewById(R.id.progressIndicator)
        btnYes = findViewById(R.id.btnYes)
        btnNo = findViewById(R.id.btnNo)
        btnRetry = findViewById(R.id.btnRetry)
        loadingProgress = findViewById(R.id.loadingProgress)
        tvLoadingText = findViewById(R.id.tvLoadingText)
        layoutButtons = findViewById(R.id.layoutButtons)
        layoutLoading = findViewById(R.id.layoutLoading)

        btnYes.setOnClickListener { handleYes() }
        btnNo.setOnClickListener { handleNo() }
        btnRetry.setOnClickListener { retryCurrentStep() }
    }

    private fun showStep(step: Int) {
        currentStep = step
        btnRetry.visibility = View.GONE
        showButtons()
        updateProgressIndicator()

        when (step) {
            0 -> showBoardStep()
            1 -> showDiceQuestionStep()      // Ask: want smart dice?
            2 -> showDiceCountStep()          // Ask: 1 or 2 dice?
            3 -> showDiceConnectStep()        // Connect dice
            4 -> showServerStep()
            5 -> showWelcomeStep()
            6 -> showQuitConfirmStep()
        }
    }

    private fun showBoardStep() {
        tvStepTitle.text = "Connect Game Board? 🎮"
        tvStepDescription.text = "Connect your LED board for the full experience!\nYou can skip this to play without the physical board."
        ivStepIcon.setImageResource(R.drawable.ic_board)
        btnYes.text = "Yes, Connect! ✓"
        btnNo.text = "Skip for now"
        onSpeak("Want to connect the game board? Tap yes to connect, or skip for now.")
    }

    private fun showDiceQuestionStep() {
        tvStepTitle.text = "Connect Smart Dice? 🎲"
        tvStepDescription.text = "Want to use GoDice for automatic rolling?\nOr prefer virtual dice on screen?"
        ivStepIcon.setImageResource(R.drawable.ic_dice_bluetooth)
        btnYes.text = "Yes, Smart Dice! ✓"
        btnNo.text = "Use Virtual Dice"
        onSpeak("Want to use smart dice? Tap yes for Bluetooth dice, or use virtual dice on screen.")
    }

    private fun showDiceCountStep() {
        tvStepTitle.text = "How Many Dice? 🎲"
        tvStepDescription.text = "Choose your play style:\n\n🎲 ONE dice - Classic mode\n🎲🎲 TWO dice - Faster & more fun!"
        ivStepIcon.setImageResource(R.drawable.ic_dice_bluetooth)
        btnYes.text = "One Dice 🎲"
        btnNo.text = "Two Dice 🎲🎲"
        onSpeak("How many dice? One dice for classic mode, or two dice for faster fun!")
    }

    private fun showDiceConnectStep() {
        val diceText = if (selectedDiceCount == 1) "your dice" else "both dice"
        tvStepTitle.text = "Connecting... 🎲"
        tvStepDescription.text = "Make sure $diceText are on and nearby!\nThey should be blinking."
        ivStepIcon.setImageResource(R.drawable.ic_dice_bluetooth)
        onSpeak("Searching for $diceText. Make sure they are on and nearby!")
        // Auto-start connection
        layoutButtons.visibility = View.GONE
        showLoading("Searching for dice...")
        onConnectDice(selectedDiceCount) { success ->
            hideLoading()
            if (success) {
                diceConnected = true
                showStep(4) // Next: Server
            } else {
                showDiceConnectError()
            }
        }
    }

    private fun showDiceConnectError() {
        val diceText = if (selectedDiceCount == 1) "dice" else "dice"
        tvStepTitle.text = "Dice Not Found 😢"
        tvStepDescription.text = "Couldn't find your $diceText.\n\nMake sure they're:\n• Turned on (blinking)\n• Nearby\n• Not connected to another device"
        ivStepIcon.setImageResource(R.drawable.ic_dice_bluetooth)
        btnRetry.visibility = View.VISIBLE
        btnRetry.text = "Try Again"
        btnNo.text = "Use Virtual Instead"
        btnNo.visibility = View.VISIBLE
        btnYes.visibility = View.GONE
        layoutButtons.visibility = View.VISIBLE
        onSpeak("Oops! Couldn't find your dice. Try again or use virtual dice instead.")
    }

    private fun showServerStep() {
        tvStepTitle.text = "Share on Web? 🌐"
        tvStepDescription.text = "Share your game on a big screen!\nSpectators can watch at lastdrop.earth"
        ivStepIcon.setImageResource(R.drawable.ic_server)
        btnYes.text = "Yes, Scan QR! ✓"
        btnNo.text = "Skip"
        btnYes.visibility = View.VISIBLE
        onSpeak("Want to share on the web? Spectators can watch on a big screen! Tap yes to scan QR code.")
    }

    private fun showWelcomeStep() {
        val namesText = when (playerNames.size) {
            1 -> playerNames[0]
            2 -> "${playerNames[0]} and ${playerNames[1]}"
            else -> playerNames.dropLast(1).joinToString(", ") + " and ${playerNames.last()}"
        }
        
        tvStepTitle.text = "Ready to Play! 🎉"
        tvStepDescription.text = "Hey $namesText!\n\n${playerNames[0]}, you're up first!\nRoll the dice when you're ready!"
        ivStepIcon.setImageResource(R.drawable.ic_game_start)
        btnYes.text = "Let's Go! 🚀"
        btnYes.visibility = View.VISIBLE
        btnNo.visibility = View.GONE
        
        // Show connection summary
        val diceMode = if (diceConnected) {
            if (selectedDiceCount == 2) "✓ Smart Dice (2x)" else "✓ Smart Dice (1x)"
        } else {
            "○ Virtual dice"
        }
        val summary = buildString {
            append("\n\n")
            if (boardConnected) append("✓ Board connected\n") else append("○ Board: offline\n")
            append("$diceMode\n")
            if (serverConnected) append("✓ Web sharing on\n") else append("○ Web: offline\n")
        }
        tvStepDescription.append(summary)
        
        // Voice announcement
        onSpeak("All set! Hey $namesText! ${playerNames[0]}, you're up first. Tap Let's Go when you're ready!")
    }

    private fun showQuitConfirmStep() {
        tvStepTitle.text = "Want to Quit? 🤔"
        tvStepDescription.text = "You skipped all connections.\nDo you want to quit or try again?"
        ivStepIcon.setImageResource(R.drawable.ic_close)
        btnYes.text = "Quit"
        btnYes.visibility = View.VISIBLE
        btnNo.text = "Try Again"
        onSpeak("You skipped everything. Want to quit or try again?")
    }

    private fun handleYes() {
        when (currentStep) {
            0 -> { // Connect Board
                allSkipped = false
                onSpeak("Great! Searching for the board...")
                showLoading("Searching for board...")
                onConnectBoard { success ->
                    hideLoading()
                    if (success) {
                        boardConnected = true
                        onSpeak("Board connected!")
                        showStep(1) // Next: Dice question
                    } else {
                        showError("Board not found")
                    }
                }
            }
            1 -> { // Want Smart Dice? Yes -> ask count
                allSkipped = false
                wantsSmartDice = true
                onSpeak("Awesome! Let's pick how many dice.")
                showStep(2) // Go to dice count selection
            }
            2 -> { // Dice count: Yes = 1 dice
                selectedDiceCount = 1
                onSpeak("One dice it is! Classic mode.")
                showStep(3) // Go to dice connection
            }
            3 -> { // Dice connect retry (handled in showDiceConnectStep)
                showStep(3)
            }
            4 -> { // Connect Server
                allSkipped = false
                onSpeak("Let's set up web sharing!")
                showLoading("Opening scanner...")
                onConnectServer { success ->
                    hideLoading()
                    if (success) {
                        serverConnected = true
                        onSpeak("Web sharing is on!")
                    }
                    showStep(5) // Next: Welcome (regardless of server result)
                }
            }
            5 -> { // Welcome - Start Game
                dismiss()
                onComplete(boardConnected, diceConnected, serverConnected, selectedDiceCount)
            }
            6 -> { // Quit Confirm - Yes = Quit
                dismiss()
                onQuit()
            }
        }
    }

    private fun handleNo() {
        when (currentStep) {
            0 -> { // Skip board
                onSpeak("Okay, skipping the board.")
                showStep(1) // go to dice question
            }
            1 -> { // Skip dice (virtual mode)
                onSpeak("Virtual dice it is! Easy peasy.")
                showStep(4) // go to server
            }
            2 -> { // Dice count: No = 2 dice
                selectedDiceCount = 2
                onSpeak("Two dice! Double the fun!")
                showStep(3) // Go to dice connection
            }
            3 -> { // Dice connect failed - use virtual instead
                wantsSmartDice = false
                diceConnected = false
                onSpeak("No problem! Virtual dice are ready.")
                showStep(4) // Go to server
            }
            4 -> { // Skip server
                if (allSkipped && !boardConnected && !diceConnected) {
                    showStep(6) // All skipped - ask if quit
                } else {
                    onSpeak("Okay, skipping web sharing.")
                    showStep(5) // Go to welcome
                }
            }
            6 -> { // Try Again
                onSpeak("Let's try again from the start!")
                showStep(0)
            }
        }
    }

    private fun retryCurrentStep() {
        btnRetry.visibility = View.GONE
        if (currentStep == 3) {
            // Retry dice connection
            showStep(3)
        } else {
            handleYes()
        }
    }

    private fun showLoading(message: String) {
        layoutButtons.visibility = View.GONE
        layoutLoading.visibility = View.VISIBLE
        tvLoadingText.text = message
    }

    private fun hideLoading() {
        layoutLoading.visibility = View.GONE
        layoutButtons.visibility = View.VISIBLE
    }

    private fun showButtons() {
        layoutLoading.visibility = View.GONE
        layoutButtons.visibility = View.VISIBLE
        btnNo.visibility = View.VISIBLE
    }

    private fun showError(message: String) {
        tvStepDescription.text = "$message\n\nWould you like to try again or skip?"
        btnRetry.visibility = View.VISIBLE
        btnRetry.text = "Try Again"
        btnNo.text = "Skip"
    }

    private fun updateProgressIndicator() {
        progressIndicator.removeAllViews()
        val steps = listOf("Board", "Dice", "Web", "Play")
        // Map internal steps to display steps: 0->0, 1/2/3->1, 4->2, 5->3
        val displayStep = when (currentStep) {
            0 -> 0
            1, 2, 3 -> 1  // All dice-related steps show as "Dice"
            4 -> 2
            5 -> 3
            else -> 0 // Quit screen shows step 0
        }
        
        steps.forEachIndexed { index, _ ->
            val dot = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(24, 24).apply {
                    marginStart = 8
                    marginEnd = 8
                }
                background = context.getDrawable(
                    when {
                        index < displayStep -> R.drawable.dot_completed
                        index == displayStep -> R.drawable.dot_active
                        else -> R.drawable.dot_inactive
                    }
                )
            }
            progressIndicator.addView(dot)
        }
    }
}
