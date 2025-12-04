/**
 * Scoreboard.js
 * 
 * Manages the scoreboard UI for active and eliminated players.
 * Handles player ranking, sorting, and visual representation.
 * 
 * Dependencies:
 * - constants.js (PAWN_IMAGES, PLAYER_GRADIENTS)
 * 
 * Integration:
 * const scoreboard = new Scoreboard({
 *   playersList: document.getElementById('playersList'),
 *   eliminatedList: document.getElementById('eliminatedList'),
 *   eliminatedCount: document.getElementById('eliminatedCount'),
 *   onWinnerDetected: (winner) => { ... },
 *   onPlayerEliminated: (playerId) => { ... }
 * });
 * 
 * scoreboard.updatePlayers(state.players);
 */

import { PAWN_IMAGES, PLAYER_GRADIENTS } from '../utils/constants.js';

export class Scoreboard {
  /**
   * Initialize the scoreboard manager
   * @param {Object} config - Configuration object
   * @param {HTMLElement} config.playersList - Container for active players
   * @param {HTMLElement} config.eliminatedList - Container for eliminated players
   * @param {HTMLElement} config.eliminatedCount - Badge showing elimination count
   * @param {Function} config.onWinnerDetected - Callback when winner detected (player)
   * @param {Function} config.onPlayerEliminated - Callback when player eliminated (playerId)
   */
  constructor({ playersList, eliminatedList, eliminatedCount, onWinnerDetected, onPlayerEliminated }) {
    this.playersList = playersList;
    this.eliminatedList = eliminatedList;
    this.eliminatedCount = eliminatedCount;
    this.onWinnerDetected = onWinnerDetected;
    this.onPlayerEliminated = onPlayerEliminated;
    
    // Track previously eliminated players to trigger sounds only once
    this.previouslyEliminatedIds = new Set();
  }

  /**
   * Update the entire scoreboard with new player data
   * @param {Array} players - Array of player objects
   * @param {boolean} skipAnimation - If true, skip animation state check
   */
  updatePlayers(players, skipAnimation = false) {
    if (!players || !Array.isArray(players)) return;

    // Clear existing lists
    this.playersList.innerHTML = "";
    this.eliminatedList.innerHTML = "";

    // Normalize player data (handle score vs drops field)
    const normalizedPlayers = players.map(p => ({
      ...p,
      drops: p.score !== undefined ? p.score : (p.drops !== undefined ? p.drops : 10)
    }));

    // Separate active and eliminated players
    const activePlayers = this.sortActivePlayers(normalizedPlayers);
    const eliminatedPlayers = normalizedPlayers.filter(p => p.eliminated);

    // Detect newly eliminated players
    eliminatedPlayers.forEach(p => {
      if (!this.previouslyEliminatedIds.has(p.id)) {
        if (this.onPlayerEliminated) {
          this.onPlayerEliminated(p.id);
        }
        this.previouslyEliminatedIds.add(p.id);
      }
    });

    // Calculate ranks
    this.calculateRanks(activePlayers);

    // Render both lists
    this.renderActivePlayers(activePlayers, normalizedPlayers);
    this.renderEliminatedPlayers(eliminatedPlayers, normalizedPlayers);

    // Update eliminated count badge
    if (this.eliminatedCount) {
      this.eliminatedCount.textContent = eliminatedPlayers.length;
    }

    // Check for winner (only one player remaining)
    if (activePlayers.length === 1 && eliminatedPlayers.length > 0) {
      if (this.onWinnerDetected) {
        this.onWinnerDetected(activePlayers[0]);
      }
    }
  }

  /**
   * Sort active players by drops (highest first) with seniority tiebreaker
   * @param {Array} players - All players
   * @returns {Array} Sorted active players with originalIndex preserved
   */
  sortActivePlayers(players) {
    return players
      .map((p, originalIndex) => ({ ...p, originalIndex }))
      .filter(p => !p.eliminated)
      .sort((a, b) => {
        if (b.drops !== a.drops) {
          return b.drops - a.drops; // Higher drops first
        }
        return a.originalIndex - b.originalIndex; // Same drops: earlier player first (seniority)
      });
  }

  /**
   * Calculate consecutive ranks for active players
   * Players with same drops get same rank
   * @param {Array} activePlayers - Sorted active players
   */
  calculateRanks(activePlayers) {
    let currentRank = 1;
    activePlayers.forEach((p, idx) => {
      if (idx > 0 && p.drops === activePlayers[idx - 1].drops) {
        // Same drops as previous player, same rank (don't increment currentRank)
        p.rank = activePlayers[idx - 1].rank;
      } else {
        // Different drops, assign current rank
        p.rank = currentRank;
        currentRank++; // Increment for next different score
      }
    });
  }

  /**
   * Render the active players list with ranks
   * @param {Array} activePlayers - Sorted and ranked active players
   * @param {Array} allPlayers - All players for index lookup
   */
  renderActivePlayers(activePlayers, allPlayers) {
    activePlayers.forEach((p) => {
      const row = document.createElement("div");
      row.className = "player-row";
      
      const main = document.createElement("div");
      main.className = "player-main";
      
      // Add rank badge
      const rank = document.createElement("div");
      rank.className = "player-rank";
      rank.textContent = `Rank ${p.rank}`;
      
      // Add colored dot (gradient based on player index)
      const dot = document.createElement("div");
      dot.className = "player-dot";
      dot.style.background = PLAYER_GRADIENTS[p.originalIndex % PLAYER_GRADIENTS.length];
      
      // Add player name
      const name = document.createElement("div");
      name.className = "player-name";
      name.textContent = p.name || p.id;
      
      // Add position metadata
      const meta = document.createElement("div");
      meta.className = "player-meta";
      meta.textContent = `Pos: ${p.pos ?? "-"}`;
      
      // Build main section
      main.appendChild(rank);
      main.appendChild(dot);
      main.appendChild(name);
      main.appendChild(meta);
      
      // Add drops count
      const pos = document.createElement("div");
      pos.className = "player-pos";
      pos.textContent = `💧 ${p.drops ?? 0}`;
      
      row.appendChild(main);
      row.appendChild(pos);
      this.playersList.appendChild(row);
    });
  }

  /**
   * Render the eliminated players list
   * @param {Array} eliminatedPlayers - Eliminated players
   * @param {Array} allPlayers - All players for index lookup
   */
  renderEliminatedPlayers(eliminatedPlayers, allPlayers) {
    const lastRank = allPlayers.filter(p => !p.eliminated).length + eliminatedPlayers.length;

    eliminatedPlayers.forEach((p) => {
      const item = document.createElement("div");
      item.className = "eliminated-item";
      
      // Add rank badge (all eliminated get last rank)
      const rank = document.createElement("div");
      rank.className = "player-rank eliminated-rank";
      rank.textContent = `Rank ${lastRank}`;
      
      // Add grayscale token icon
      const tokenIcon = document.createElement("div");
      tokenIcon.className = "eliminated-token";
      const playerIndex = allPlayers.indexOf(p);
      tokenIcon.style.backgroundImage = `url('${PAWN_IMAGES[playerIndex % PAWN_IMAGES.length]}')`;
      
      // Add player name
      const name = document.createElement("span");
      name.textContent = p.name || p.id;
      
      item.appendChild(rank);
      item.appendChild(tokenIcon);
      item.appendChild(name);
      this.eliminatedList.appendChild(item);
    });
  }

  /**
   * Clear all scoreboard data (useful for game reset)
   */
  clear() {
    this.playersList.innerHTML = "";
    this.eliminatedList.innerHTML = "";
    if (this.eliminatedCount) {
      this.eliminatedCount.textContent = "0";
    }
    this.previouslyEliminatedIds.clear();
  }

  /**
   * Get current active player count
   * @returns {number} Number of active players
   */
  getActivePlayerCount() {
    return this.playersList.children.length;
  }

  /**
   * Get current eliminated player count
   * @returns {number} Number of eliminated players
   */
  getEliminatedPlayerCount() {
    return this.eliminatedList.children.length;
  }

  /**
   * Check if a specific player is eliminated
   * @param {string} playerId - Player ID to check
   * @returns {boolean} True if player is eliminated
   */
  isPlayerEliminated(playerId) {
    return this.previouslyEliminatedIds.has(playerId);
  }
}
