/**
 * Example: How to use the modular Last Drop components
 * This file demonstrates integration of DiceAnimator, BoardRenderer, and AudioManager
 */

import { DiceAnimator } from './assets/js/core/dice-animator.js';
import { BoardRenderer } from './assets/js/core/board-renderer.js';
import { AudioManager } from './assets/js/core/audio-manager.js';
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
  
  // 4. Example: Create players
  const players = [
    { id: 'P1', name: 'Player 1', color: 'red', pos: 1, score: 10 },
    { id: 'P2', name: 'Player 2', color: 'green', pos: 1, score: 10 },
    { id: 'P3', name: 'Player 3', color: 'blue', pos: 1, score: 10 },
    { id: 'P4', name: 'Player 4', color: 'yellow', pos: 1, score: 10 }
  ];
  
  // 5. Example: Initialize tokens on board
  players.forEach((player, index) => {
    boardRenderer.ensureTokenForPlayer(player);
    boardRenderer.positionToken(player.id, player.pos, index);
  });
  
  // 6. Example: Simulate a dice roll and token movement
  function simulateRoll(playerId, playerName, diceValue) {
    const player = players.find(p => p.id === playerId);
    if (!player) return;
    
    const oldPos = player.pos;
    const newPos = ((player.pos - 1 + diceValue) % 20) + 1;
    player.pos = newPos;
    
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
        
        console.log(`${playerName} rolled ${diceValue}, moved from tile ${oldPos} to ${newPos}`);
      }
    });
  }
  
  // 7. Example: Test button - simulate Player 1 rolling dice
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
  
  // 8. Example: Audio controls
  const audioToggle = document.getElementById('audioToggle');
  const audioStatus = document.getElementById('audioStatus');
  
  audioToggle?.addEventListener('click', () => {
    const isEnabled = audioManager.toggle();
    if (audioStatus) {
      audioStatus.textContent = isEnabled ? 'ON' : 'OFF';
    }
  });
  
  // 9. Example: Volume controls
  const volumeControls = {
    diceVolume: 'dice',
    moveVolume: 'move',
    chanceVolume: 'chance',
    tileVolume: 'tile',
    eliminatedVolume: 'eliminated',
    winnerVolume: 'winner'
  };
  
  Object.entries(volumeControls).forEach(([elementId, soundType]) => {
    const element = document.getElementById(elementId);
    element?.addEventListener('input', (e) => {
      const value = parseInt(e.target.value, 10) / 100;
      audioManager.setVolume(soundType, value);
      
      const valueDisplay = document.getElementById(`${soundType}Value`);
      if (valueDisplay) {
        valueDisplay.textContent = `${e.target.value}%`;
      }
    });
  });
  
  // 10. Example: Eliminated player
  function eliminatePlayer(playerId) {
    boardRenderer.markTokenAsEliminated(playerId);
    audioManager.playEliminated();
    
    const player = players.find(p => p.id === playerId);
    if (player) {
      player.eliminated = true;
      console.log(`${player.name} has been eliminated!`);
    }
  }
  
  // 11. Example: Winner announcement
  function announceWinner(playerId) {
    const player = players.find(p => p.id === playerId);
    if (!player) return;
    
    audioManager.playWinner();
    
    // Show winner overlay (assuming it exists in HTML)
    const winnerOverlay = document.getElementById('winnerOverlay');
    const winnerName = document.getElementById('winnerName');
    const winnerDrops = document.getElementById('winnerDrops');
    
    if (winnerOverlay && winnerName && winnerDrops) {
      winnerName.textContent = player.name;
      winnerDrops.textContent = `💧 ${player.score} drops`;
      winnerOverlay.classList.remove('hidden');
    }
    
    console.log(`${player.name} wins with ${player.score} drops!`);
  }
  
  // 12. Global access for debugging
  window.lastDropModules = {
    audioManager,
    boardRenderer,
    diceAnimator,
    players,
    simulateRoll,
    eliminatePlayer,
    announceWinner
  };
  
  console.log('✅ Last Drop modules initialized!');
  console.log('Try: lastDropModules.simulateRoll("P1", "Player 1", 4)');
});
