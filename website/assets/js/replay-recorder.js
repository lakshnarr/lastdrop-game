/**
 * Game Replay Recording Module
 * Phase 5.2: Game Replay System
 * 
 * Records game events and state changes for later playback
 */

class ReplayRecorder {
  constructor() {
    this.recording = false;
    this.sessionId = null;
    this.boardId = null;
    this.startTime = null;
    this.events = [];
    this.playerCount = 0;
    this.playerNames = [];
    this.playerColors = [];
    this.initialState = null;
    this.rollCount = 0;
  }

  /**
   * Start recording a new game session
   * @param {string} sessionId - Unique session identifier
   * @param {string} boardId - Board identifier (e.g., "LASTDROP-0001")
   * @param {Array} players - Initial player state
   */
  startRecording(sessionId, boardId, players) {
    this.recording = true;
    this.sessionId = sessionId;
    this.boardId = boardId;
    this.startTime = Date.now();
    this.events = [];
    this.rollCount = 0;
    
    // Extract player metadata
    this.playerCount = players.length;
    this.playerNames = players.map(p => p.name || p.id);
    this.playerColors = players.map(p => p.color || '#FF0000');
    
    // Store initial state
    this.initialState = {
      players: players.map(p => ({
        id: p.id,
        name: p.name || p.id,
        color: p.color,
        position: p.pos || 0,
        score: p.score || p.drops || 10,
        eliminated: p.eliminated || false
      })),
      timestamp: 0
    };
    
    console.log('[Replay] Recording started:', this.sessionId);
  }

  /**
   * Record a game event
   * @param {string} type - Event type (roll, move, chance, eliminated, winner)
   * @param {Object} data - Event-specific data
   */
  recordEvent(type, data) {
    if (!this.recording) return;
    
    const event = {
      type,
      timestamp: Date.now() - this.startTime,
      ...data
    };
    
    this.events.push(event);
    
    // Track roll count
    if (type === 'roll') {
      this.rollCount++;
    }
    
    console.log('[Replay] Event recorded:', type, data);
  }

  /**
   * Record a dice roll event
   */
  recordRoll(playerId, playerName, dice1, dice2, diceColor1, diceColor2) {
    this.recordEvent('roll', {
      playerId,
      playerName,
      dice1,
      dice2,
      diceColor1,
      diceColor2,
      rollNumber: this.rollCount + 1
    });
  }

  /**
   * Record a player movement
   */
  recordMove(playerId, fromPosition, toPosition, tileName) {
    this.recordEvent('move', {
      playerId,
      fromPosition,
      toPosition,
      tileName
    });
  }

  /**
   * Record a chance card event
   */
  recordChanceCard(playerId, cardText) {
    this.recordEvent('chance', {
      playerId,
      cardText
    });
  }

  /**
   * Record player elimination
   */
  recordElimination(playerId, playerName) {
    this.recordEvent('eliminated', {
      playerId,
      playerName
    });
  }

  /**
   * Record game winner
   */
  recordWinner(playerId, playerName, finalScore) {
    this.recordEvent('winner', {
      playerId,
      playerName,
      finalScore
    });
  }

  /**
   * Record current game state snapshot
   */
  recordStateSnapshot(players) {
    this.recordEvent('snapshot', {
      players: players.map(p => ({
        id: p.id,
        position: p.pos,
        score: p.score || p.drops,
        eliminated: p.eliminated
      }))
    });
  }

  /**
   * Stop recording and prepare replay data for saving
   * @param {string} endReason - How the game ended (completed, abandoned, timeout)
   * @returns {Object} Complete replay data ready for API submission
   */
  stopRecording(endReason = 'completed', winner = null, finalScores = []) {
    if (!this.recording) {
      console.warn('[Replay] Not recording, cannot stop');
      return null;
    }
    
    this.recording = false;
    const duration = Math.floor((Date.now() - this.startTime) / 1000);
    
    const replayData = {
      sessionId: this.sessionId,
      boardId: this.boardId,
      playerCount: this.playerCount,
      playerNames: this.playerNames,
      playerColors: this.playerColors,
      duration,
      totalRolls: this.rollCount,
      winner: winner ? winner.name : null,
      winnerPlayerId: winner ? winner.id : null,
      finalScores,
      endReason,
      replayData: {
        initialState: this.initialState,
        events: this.events
      }
    };
    
    console.log('[Replay] Recording stopped:', {
      duration: `${duration}s`,
      events: this.events.length,
      rolls: this.rollCount
    });
    
    return replayData;
  }

  /**
   * Save replay to server
   * @param {Object} replayData - Complete replay data
   * @returns {Promise} Server response with replay ID and share URL
   */
  async saveReplay(replayData) {
    try {
      const response = await fetch('/api/save_replay.php', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(replayData)
      });
      
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      
      const result = await response.json();
      
      if (result.success) {
        console.log('[Replay] Saved successfully:', result.replayId);
        console.log('[Replay] Share URL:', result.shareUrl);
        return result;
      } else {
        throw new Error(result.error || 'Failed to save replay');
      }
    } catch (error) {
      console.error('[Replay] Save failed:', error);
      throw error;
    }
  }

  /**
   * Auto-save replay when game ends
   */
  async autoSave(endReason, winner, finalScores) {
    const replayData = this.stopRecording(endReason, winner, finalScores);
    
    if (!replayData) {
      console.warn('[Replay] No replay data to save');
      return null;
    }
    
    try {
      const result = await this.saveReplay(replayData);
      return result;
    } catch (error) {
      console.error('[Replay] Auto-save failed:', error);
      return null;
    }
  }

  /**
   * Check if currently recording
   */
  isRecording() {
    return this.recording;
  }

  /**
   * Get current session info
   */
  getSessionInfo() {
    return {
      sessionId: this.sessionId,
      boardId: this.boardId,
      recording: this.recording,
      eventCount: this.events.length,
      rollCount: this.rollCount,
      duration: this.startTime ? Math.floor((Date.now() - this.startTime) / 1000) : 0
    };
  }
}

// Export for use in main script
if (typeof module !== 'undefined' && module.exports) {
  module.exports = ReplayRecorder;
}
