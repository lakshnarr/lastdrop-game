/**
 * Overlays.js
 * 
 * Manages all overlay displays in the game:
 * - Winner celebration overlay with confetti animation
 * - Chance card popup display
 * - Connection status overlays (connecting, reconnecting)
 * - Welcome screen overlay
 * 
 * Integration:
 * const overlays = new Overlays({
 *   winnerOverlay: document.getElementById('winnerOverlay'),
 *   winnerName: document.getElementById('winnerName'),
 *   winnerDrops: document.getElementById('winnerDrops'),
 *   chanceCard: document.getElementById('chanceCard'),
 *   chanceImage: document.getElementById('chanceImage'),
 *   chanceTitle: document.getElementById('chanceTitle'),
 *   chanceText: document.getElementById('chanceText'),
 *   connectionOverlay: document.getElementById('connectionOverlay'),
 *   connectionIndicator: document.getElementById('connectionIndicator'),
 *   connectionText: document.getElementById('connectionText'),
 *   reconnectionOverlay: document.getElementById('reconnectionOverlay'),
 *   reconnectionText: document.getElementById('reconnectionText'),
 *   reconnectionInfo: document.getElementById('reconnectionInfo'),
 *   retryAttempt: document.getElementById('retryAttempt'),
 *   retryDelay: document.getElementById('retryDelay'),
 *   welcomeOverlay: document.getElementById('welcomeOverlay'),
 *   soundManager: audioManager // Optional
 * });
 */

export class Overlays {
  /**
   * Initialize the overlays manager
   * @param {Object} config - Configuration object with all overlay elements
   */
  constructor({
    winnerOverlay,
    winnerName,
    winnerDrops,
    chanceCard,
    chanceImage,
    chanceTitle,
    chanceText,
    connectionOverlay,
    connectionIndicator,
    connectionText,
    reconnectionOverlay,
    reconnectionText,
    reconnectionInfo,
    retryAttempt,
    retryDelay,
    welcomeOverlay,
    soundManager = null
  }) {
    // Winner overlay elements
    this.winnerOverlay = winnerOverlay;
    this.winnerName = winnerName;
    this.winnerDrops = winnerDrops;
    
    // Chance card elements
    this.chanceCard = chanceCard;
    this.chanceImage = chanceImage;
    this.chanceTitle = chanceTitle;
    this.chanceText = chanceText;
    this.chanceTimeout = null;
    
    // Connection overlay elements
    this.connectionOverlay = connectionOverlay;
    this.connectionIndicator = connectionIndicator;
    this.connectionText = connectionText;
    
    // Reconnection overlay elements
    this.reconnectionOverlay = reconnectionOverlay;
    this.reconnectionText = reconnectionText;
    this.reconnectionInfo = reconnectionInfo;
    this.retryAttempt = retryAttempt;
    this.retryDelay = retryDelay;
    
    // Welcome overlay
    this.welcomeOverlay = welcomeOverlay;
    
    // Optional sound manager
    this.soundManager = soundManager;
  }

  /**
   * Show winner celebration overlay
   * @param {Object} player - Player object with name and drops
   * @param {string} player.name - Player name
   * @param {number} player.drops - Number of drops
   */
  showWinner(player) {
    if (!this.winnerOverlay || !this.winnerName || !this.winnerDrops) return;
    
    // Play winner sound
    if (this.soundManager) {
      this.soundManager.playWinner();
    }
    
    // Update winner details
    this.winnerName.textContent = player.name || player.id || 'Player';
    this.winnerDrops.textContent = `💧 ${player.drops || player.score || 0} drops`;
    
    // Show overlay
    this.winnerOverlay.classList.remove('hidden');
  }

  /**
   * Hide winner overlay
   */
  hideWinner() {
    if (this.winnerOverlay) {
      this.winnerOverlay.classList.add('hidden');
    }
  }

  /**
   * Show chance card popup
   * @param {string} cardId - Chance card ID (e.g., "1", "17")
   * @param {string} description - Card description text
   * @param {number} displayDuration - How long to show (ms), default 4500
   */
  showChanceCard(cardId, description = null, displayDuration = 4500) {
    if (!this.chanceCard || !this.chanceImage || !this.chanceTitle || !this.chanceText) return;
    
    // Play chance card sound
    if (this.soundManager) {
      this.soundManager.playChance();
    }
    
    // Update card details
    this.chanceImage.style.backgroundImage = `url('/assets/chance/${cardId}.jpg')`;
    this.chanceTitle.textContent = `Chance Card – ${cardId}`;
    this.chanceText.textContent = description || "Special event triggered by the board.";
    
    // Show card
    this.chanceCard.classList.add('visible');
    
    // Clear existing timeout
    if (this.chanceTimeout) {
      clearTimeout(this.chanceTimeout);
    }
    
    // Auto-hide after duration
    this.chanceTimeout = setTimeout(() => {
      this.hideChanceCard();
    }, displayDuration);
  }

  /**
   * Hide chance card popup
   */
  hideChanceCard() {
    if (this.chanceCard) {
      this.chanceCard.classList.remove('visible');
    }
    
    if (this.chanceTimeout) {
      clearTimeout(this.chanceTimeout);
      this.chanceTimeout = null;
    }
  }

  /**
   * Show connection overlay (waiting for controller)
   * @param {Function} onStartPolling - Callback to start polling when shown
   */
  showConnectionOverlay(onStartPolling = null) {
    if (!this.connectionOverlay) return;
    
    this.connectionOverlay.classList.remove('hidden');
    
    // Trigger polling callback if provided
    if (onStartPolling) {
      onStartPolling();
    }
  }

  /**
   * Hide connection overlay
   */
  hideConnectionOverlay() {
    if (this.connectionOverlay) {
      this.connectionOverlay.classList.add('hidden');
    }
  }

  /**
   * Update connection status (connected)
   * @param {number} hideDelayMs - Delay before hiding (ms), default 2000
   */
  onControllerConnected(hideDelayMs = 2000) {
    if (!this.connectionIndicator || !this.connectionText) return;
    
    // Update status indicator
    const statusDot = this.connectionIndicator.querySelector('.status-dot');
    if (statusDot) {
      statusDot.classList.add('connected');
    }
    
    this.connectionText.textContent = 'Controller Connected! 🎉';
    
    // Auto-hide after delay
    setTimeout(() => {
      this.hideConnectionOverlay();
    }, hideDelayMs);
  }

  /**
   * Show reconnection overlay
   * @param {number} attempt - Current retry attempt number
   * @param {number|null} delayMs - Delay until next retry (null if max retries reached)
   * @param {number} maxRetries - Maximum retry attempts
   */
  showReconnectionOverlay(attempt, delayMs, maxRetries = 5) {
    if (!this.reconnectionOverlay) return;
    
    this.reconnectionOverlay.classList.remove('hidden');
    
    if (delayMs === null) {
      // Max retries reached
      if (this.reconnectionText) {
        this.reconnectionText.textContent = 'Connection lost - Max retries reached';
      }
      
      if (this.reconnectionInfo) {
        this.reconnectionInfo.innerHTML = '<p style="color: var(--danger);">Unable to reach server. Please check your network connection.</p>';
      }
    } else {
      // Still retrying
      if (this.reconnectionText) {
        this.reconnectionText.textContent = 'Attempting to reconnect...';
      }
      
      if (this.retryAttempt) {
        this.retryAttempt.textContent = attempt;
      }
      
      if (this.retryDelay) {
        this.retryDelay.textContent = (delayMs / 1000).toFixed(0);
      }
    }
  }

  /**
   * Hide reconnection overlay
   */
  hideReconnectionOverlay() {
    if (this.reconnectionOverlay) {
      this.reconnectionOverlay.classList.add('hidden');
    }
  }

  /**
   * Show welcome screen overlay
   */
  showWelcome() {
    if (this.welcomeOverlay) {
      this.welcomeOverlay.classList.remove('hidden');
    }
  }

  /**
   * Hide welcome screen overlay
   */
  hideWelcome() {
    if (this.welcomeOverlay) {
      this.welcomeOverlay.classList.add('hidden');
    }
  }

  /**
   * Hide all overlays
   */
  hideAll() {
    this.hideWinner();
    this.hideChanceCard();
    this.hideConnectionOverlay();
    this.hideReconnectionOverlay();
    this.hideWelcome();
  }

  /**
   * Check if any overlay is currently visible
   * @returns {boolean} True if any overlay is visible
   */
  isAnyVisible() {
    const overlays = [
      this.winnerOverlay,
      this.chanceCard,
      this.connectionOverlay,
      this.reconnectionOverlay,
      this.welcomeOverlay
    ];
    
    return overlays.some(overlay => 
      overlay && !overlay.classList.contains('hidden') && 
      (overlay.classList.contains('visible') || overlay.offsetParent !== null)
    );
  }

  /**
   * Show a custom loading overlay (can be used for other purposes)
   * @param {string} message - Loading message to display
   */
  showLoading(message = 'Loading...') {
    // Can use reconnection overlay as a generic loading screen
    if (this.reconnectionOverlay && this.reconnectionText) {
      this.reconnectionText.textContent = message;
      this.reconnectionOverlay.classList.remove('hidden');
      
      // Hide retry info
      if (this.reconnectionInfo) {
        this.reconnectionInfo.style.display = 'none';
      }
    }
  }

  /**
   * Hide loading overlay
   */
  hideLoading() {
    this.hideReconnectionOverlay();
    
    // Restore retry info display
    if (this.reconnectionInfo) {
      this.reconnectionInfo.style.display = 'block';
    }
  }
}
