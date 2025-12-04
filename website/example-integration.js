/**
 * Example: How to use the modular Last Drop components
 * This file demonstrates integration of DiceAnimator, BoardRenderer, AudioManager, and Scoreboard
 */

import { DiceAnimator } from './assets/js/core/dice-animator.js';
import { BoardRenderer } from './assets/js/core/board-renderer.js';
import { AudioManager } from './assets/js/core/audio-manager.js';
import { Scoreboard } from './assets/js/ui/scoreboard.js';
import { EventLog } from './assets/js/ui/event-log.js';
import { Overlays } from './assets/js/ui/overlays.js';
import { SettingsPanel } from './assets/js/ui/settings-panel.js';
import { ApiClient } from './assets/js/network/api-client.js';
import { SessionManager } from './assets/js/network/session-manager.js';
import { TILE_NAMES, PLAYER_COLORS } from './assets/js/utils/constants.js';

// Initialize on page load
window.addEventListener('load', () => {
  
  // 1. Create Audio Manager
  const audioManager = new AudioManager({
    enabled: true
  });
  
  // 2. Create Board Renderer
  const boardRenderer = new BoardRenderer({
    tilesGrid: document.getElementById('tilesGrid'),
    tokensLayer: document.getElementById('tokensLayer'),
    boardWrapper: document.getElementById('boardWrapper'),
    board3d: document.getElementById('board3d'),
    zoomRange: document.getElementById('zoomRange'),
    rotateRange: document.getElementById('rotateRange'),
    rotateXRange: document.getElementById('rotateXRange'),
    rotateYRange: document.getElementById('rotateYRange'),
    offsetXRange: document.getElementById('offsetXRange'),
    offsetYRange: document.getElementById('offsetYRange'),
    viewToggle: document.getElementById('viewToggle'),
    viewStatus: document.getElementById('viewStatus'),
    soundManager: audioManager
  });
  
  // 3. Create Dice Animator
  const diceAnimator = new DiceAnimator({
    scoreboardDiceDisplay: document.getElementById('scoreboardDiceDisplay'),
    scoreboardDice1: document.getElementById('scoreboardDice1'),
    scoreboardDice2: document.getElementById('scoreboardDice2'),
    dicePlayerName: document.getElementById('dicePlayerName'),
    soundManager: audioManager
  });
  
  // 4. Create Scoreboard
  const scoreboard = new Scoreboard({
    playersList: document.getElementById('playersList'),
    eliminatedList: document.getElementById('eliminatedList'),
    eliminatedCount: document.getElementById('eliminatedCount'),
    onWinnerDetected: (winner) => {
      announceWinner(winner.id);
    },
    onPlayerEliminated: (playerId) => {
      audioManager.playEliminated();
      console.log(`Player ${playerId} was eliminated!`);
    }
  });
  
  // 5. Create Event Log
  const eventLog = new EventLog({
    container: document.getElementById('eventLog')
  });
  
  // Show initial message
  eventLog.showInitial();
  
  // 6. Create Overlays
  const overlays = new Overlays({
    winnerOverlay: document.getElementById('winnerOverlay'),
    winnerName: document.getElementById('winnerName'),
    winnerDrops: document.getElementById('winnerDrops'),
    chanceCard: document.getElementById('chanceCard'),
    chanceImage: document.getElementById('chanceImage'),
    chanceTitle: document.getElementById('chanceTitle'),
    chanceText: document.getElementById('chanceText'),
    connectionOverlay: document.getElementById('connectionOverlay'),
    connectionIndicator: document.getElementById('connectionIndicator'),
    connectionText: document.getElementById('connectionText'),
    reconnectionOverlay: document.getElementById('reconnectionOverlay'),
    reconnectionText: document.getElementById('reconnectionText'),
    reconnectionInfo: document.getElementById('reconnectionInfo'),
    retryAttempt: document.getElementById('retryAttempt'),
    retryDelay: document.getElementById('retryDelay'),
    welcomeOverlay: document.getElementById('welcomeOverlay'),
    soundManager: audioManager
  });
  
  // 7. Create Settings Panel
  const settingsPanel = new SettingsPanel({
    panel: document.getElementById('settingsPanel'),
    backdrop: document.getElementById('settingsBackdrop'),
    openBtn: document.getElementById('settingsBtn'),
    closeBtn: document.getElementById('closeSettings'),
    viewToggle: document.getElementById('viewToggle'),
    viewStatus: document.getElementById('viewStatus'),
    zoomRange: document.getElementById('zoomRange'),
    rotateRange: document.getElementById('rotateRange'),
    rotateXRange: document.getElementById('rotateXRange'),
    rotateYRange: document.getElementById('rotateYRange'),
    audioToggle: document.getElementById('audioToggle'),
    audioStatus: document.getElementById('audioStatus'),
    bgMusicVolume: document.getElementById('bgMusicVolume'),
    diceVolume: document.getElementById('diceVolume'),
    moveVolume: document.getElementById('moveVolume'),
    chanceVolume: document.getElementById('chanceVolume'),
    tileVolume: document.getElementById('tileVolume'),
    eliminatedVolume: document.getElementById('eliminatedVolume'),
    winnerVolume: document.getElementById('winnerVolume'),
    offsetXRange: document.getElementById('offsetXRange'),
    offsetYRange: document.getElementById('offsetYRange'),
    offsetXValue: document.getElementById('offsetXValue'),
    offsetYValue: document.getElementById('offsetYValue'),
    applyCoinOffset: document.getElementById('applyCoinOffset'),
    kidToggle: document.getElementById('kidToggle'),
    kidStatus: document.getElementById('kidStatus'),
    devToggle: document.getElementById('devToggle'),
    devStatus: document.getElementById('devStatus'),
    devControls: document.getElementById('devControls'),
    demoToggle: document.getElementById('demoToggle'),
    demoStatus: document.getElementById('demoStatus'),
    helpBtn: document.getElementById('helpBtn'),
    boardRenderer: boardRenderer,
    audioManager: audioManager
  });
  
  // 8. Create Session Manager
  const sessionManager = new SessionManager({
    onSessionChange: (boardId, sessionId) => {
      console.log('Session changed:', { boardId, sessionId });
    }
  });
  
  // Parse session from URL (e.g., ?session=LASTDROP-0001_abc-123)
  sessionManager.parseFromUrl();
  
  // 9. Create API Client (for live mode)
  const apiClient = new ApiClient({
    baseUrl: '/api/live_state.php',
    apiKey: 'ABC123',
    pollingInterval: 2000,
    onStateUpdate: (state) => {
      // Update scoreboard and board when state is received
      if (state.players) {
        scoreboard.updatePlayers(state.players);
      }
      console.log('State updated from API:', state);
    },
    onConnectionChange: (status) => {
      console.log('Connection status:', status);
      if (status === 'connected') {
        eventLog.showCustom('Connected to live game!', true);
      } else if (status === 'offline') {
        eventLog.showCustom('Connection lost', true);
      }
    },
    onRetry: (attempt, delay) => {
      console.log(`Retry attempt ${attempt}, waiting ${delay}ms`);
      overlays.showReconnectionOverlay(attempt, delay, 5);
    },
    onFirstConnection: () => {
      overlays.onControllerConnected();
    }
  });
  
  // Set session for API client
  if (sessionManager.hasSession()) {
    apiClient.setSession(sessionManager.getBoardId(), sessionManager.getSessionId());
  }
  
  // Note: apiClient.start() to begin polling (not started in demo)
  
  // 10. Example: Create playerss
  const players = [
    { id: 'P1', name: 'Player 1', color: 'red', pos: 1, score: 10 },
    { id: 'P2', name: 'Player 2', color: 'green', pos: 1, score: 10 },
    { id: 'P3', name: 'Player 3', color: 'blue', pos: 1, score: 10 },
    { id: 'P4', name: 'Player 4', color: 'yellow', pos: 1, score: 10 }
  ];
  
  // 11. Example: Initialize tokens on board and scoreboard
  players.forEach((player, index) => {
    boardRenderer.ensureTokenForPlayer(player);
    boardRenderer.positionToken(player.id, player.pos, index);
  });
  
  // Update scoreboard with initial player data
  scoreboard.updatePlayers(players);
  
  // 12. Example: Simulate a dice roll and token movement
  function simulateRoll(playerId, playerName, diceValue) {
    // Show rolling message
    eventLog.showRolling(playerName, 1);
    const player = players.find(p => p.id === playerId);
    if (!player) return;
    
    const oldPos = player.pos;
    const newPos = ((player.pos - 1 + diceValue) % 20) + 1;
    player.pos = newPos;
    
    // Update score (simulate tile effect)
    const scoreChange = Math.floor(Math.random() * 5) - 2; // Random -2 to +2
    player.score = Math.max(0, player.score + scoreChange);
    
    // Check for elimination (score reaches 0)
    if (player.score === 0 && !player.eliminated) {
      player.eliminated = true;
    }
    
    // Link dice animator to board renderer
    boardRenderer.setAnimationFlag(true);
    
    // Show 3D dice animation
    diceAnimator.show3DDiceRoll({
      value1: diceValue,
      value2: null,
      playerName: playerName,
      diceColor1: player.color,
      callback: () => {
        // After dice animation, move token
        boardRenderer.setAnimationFlag(false);
        
        const playerIndex = players.indexOf(player);
        boardRenderer.positionToken(
          playerId, 
          newPos, 
          playerIndex, 
          true, // animate = true
          oldPos // from tile
        );
        
        // Update scoreboard after movement
        scoreboard.updatePlayers(players);
        
        // Update event log with result
        eventLog.showRollResult(playerName, diceValue, null, newPos, null);
        
        console.log(`${playerName} rolled ${diceValue}, moved from tile ${oldPos} to ${newPos}, score: ${player.score}`);
      }
    });
  }
  
  // 13. Example: Test button - simulate Player 1 rolling dice
  const testButton = document.createElement('button');
  testButton.textContent = 'Test: Roll Dice for Player 1';
  testButton.className = 'btn-small';
  testButton.style.position = 'fixed';
  testButton.style.bottom = '20px';
  testButton.style.right = '20px';
  testButton.style.zIndex = '9999';
  testButton.style.padding = '12px 24px';
  testButton.style.fontSize = '14px';
  
  testButton.addEventListener('click', () => {
    const diceValue = Math.floor(Math.random() * 6) + 1;
    simulateRoll('P1', 'Player 1', diceValue);
  });
  
  document.body.appendChild(testButton);
  
  // 14. Example: Eliminated player
  function eliminatePlayer(playerId) {
    boardRenderer.markTokenAsEliminated(playerId);
    
    const player = players.find(p => p.id === playerId);
    if (player) {
      player.eliminated = true;
      player.score = 0;
      scoreboard.updatePlayers(players);
      eventLog.showElimination(player.name);
      console.log(`${player.name} has been eliminated!`);
    }
  }
  
  // 15. Example: Winner announcement
  function announceWinner(playerId) {
    const player = players.find(p => p.id === playerId);
    if (!player) return;
    
    // Show winner overlay with confetti
    overlays.showWinner({
      name: player.name,
      drops: player.score
    });
    
    // Update event log
    eventLog.showWinner(player.name);
    
    console.log(`${player.name} wins with ${player.score} drops!`);
  }
  
  // 16. Global access for debugging
  window.lastDropModules = {
    audioManager,
    boardRenderer,
    diceAnimator,
    scoreboard,
    eventLog,
    overlays,
    settingsPanel,
    apiClient,
    sessionManager,
    players,
    simulateRoll,
    eliminatePlayer,
    announceWinner
  };
  
  console.log('✅ Last Drop modules initialized!');
  console.log('Try: lastDropModules.simulateRoll("P1", "Player 1", 4)');
  console.log('Or: lastDropModules.eliminatePlayer("P3")');
  console.log('Or: lastDropModules.overlays.showChanceCard("5", "Lucky bonus!")');
  console.log('Or: lastDropModules.settingsPanel.toggle()');
  console.log('Or: lastDropModules.apiClient.start() // Start live polling');
  console.log('Session:', lastDropModules.sessionManager.toString());
});
