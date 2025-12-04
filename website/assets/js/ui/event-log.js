/**
 * EventLog.js
 * 
 * Manages the event log display for game events.
 * Formats and displays messages for dice rolls, tile movements, chance cards, 
 * player eliminations, resets, and undo actions.
 * 
 * Integration:
 * const eventLog = new EventLog({
 *   container: document.getElementById('eventLog')
 * });
 * 
 * eventLog.showRolling(playerName, diceCount);
 * eventLog.showRollResult(playerName, dice1, dice2, tileIndex, chanceCardId);
 * eventLog.showReset();
 * eventLog.showUndo(playerName);
 */

export class EventLog {
  /**
   * Initialize the event log manager
   * @param {Object} config - Configuration object
   * @param {HTMLElement} config.container - Container element for event messages
   */
  constructor({ container }) {
    this.container = container;
  }

  /**
   * Show a message indicating player is rolling dice
   * @param {string} playerName - Name of the player rolling
   * @param {number} diceCount - Number of dice being rolled (1 or 2)
   */
  showRolling(playerName, diceCount = 1) {
    const rollingText = diceCount > 1 
      ? `${playerName} is rolling both dice...` 
      : `${playerName} is rolling the dice...`;
    this.container.innerHTML = `<span class='event-highlight'>${rollingText}</span>`;
  }

  /**
   * Show the result of a dice roll with tile and optional chance card
   * @param {string} playerName - Name of the player
   * @param {number} dice1 - First dice value
   * @param {number|null} dice2 - Second dice value (null for single dice)
   * @param {number} tileIndex - Tile the player landed on
   * @param {string|null} chanceCardId - ID of chance card drawn (if any)
   */
  showRollResult(playerName, dice1, dice2, tileIndex, chanceCardId = null) {
    const diceText = dice2 !== null && dice2 !== undefined
      ? `<span class='event-highlight'>${dice1}</span> and <span class='event-highlight'>${dice2}</span>`
      : `<span class='event-highlight'>${dice1}</span>`;
    
    const chanceText = chanceCardId 
      ? ` and drew card <span class='event-highlight'>${chanceCardId}</span>.`
      : ".";
    
    this.container.innerHTML = `<span class='event-highlight'>${playerName}</span> rolled ${diceText}, moved to <span class='event-highlight'>Tile ${tileIndex}</span>${chanceText}`;
  }

  /**
   * Show game reset message
   */
  showReset() {
    this.container.innerHTML = "Game reset - Ready to play!";
  }

  /**
   * Show undo action message
   * @param {string} playerName - Name of the player who undid their move
   */
  showUndo(playerName) {
    this.container.innerHTML = `<span class='event-highlight'>Undo</span> - ${playerName} returns to previous position`;
  }

  /**
   * Show waiting message (no rolls yet)
   */
  showWaiting() {
    this.container.innerHTML = "Waiting for first roll...";
  }

  /**
   * Show initial message when game starts
   */
  showInitial() {
    this.container.textContent = "Waiting for first drop…";
  }

  /**
   * Show player elimination message
   * @param {string} playerName - Name of the eliminated player
   */
  showElimination(playerName) {
    this.container.innerHTML = `<span class='event-highlight'>${playerName}</span> has been <span class='event-highlight'>eliminated</span>!`;
  }

  /**
   * Show winner announcement
   * @param {string} playerName - Name of the winning player
   */
  showWinner(playerName) {
    this.container.innerHTML = `<span class='event-highlight'>🎉 ${playerName} wins!</span>`;
  }

  /**
   * Show custom message with optional highlighting
   * @param {string} message - Message to display
   * @param {boolean} highlight - Whether to wrap entire message in highlight
   */
  showCustom(message, highlight = false) {
    if (highlight) {
      this.container.innerHTML = `<span class='event-highlight'>${message}</span>`;
    } else {
      this.container.innerHTML = message;
    }
  }

  /**
   * Clear the event log
   */
  clear() {
    this.container.innerHTML = "";
  }

  /**
   * Show error message
   * @param {string} errorMessage - Error message to display
   */
  showError(errorMessage) {
    this.container.innerHTML = `<span style='color: #ef4444;'>⚠️ ${errorMessage}</span>`;
  }

  /**
   * Show connection status message
   * @param {string} status - Connection status ('connecting', 'connected', 'disconnected', 'reconnecting')
   */
  showConnectionStatus(status) {
    const messages = {
      connecting: "🔄 Connecting to server...",
      connected: "✅ Connected to live game",
      disconnected: "❌ Disconnected from server",
      reconnecting: "🔄 Reconnecting..."
    };
    
    const message = messages[status] || status;
    this.container.innerHTML = `<span class='event-highlight'>${message}</span>`;
  }

  /**
   * Get current message text (without HTML)
   * @returns {string} Plain text of current message
   */
  getText() {
    return this.container.textContent;
  }

  /**
   * Get current message HTML
   * @returns {string} HTML of current message
   */
  getHTML() {
    return this.container.innerHTML;
  }
}
