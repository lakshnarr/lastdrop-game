/**
 * BoardRenderer - Handles 3D board rendering, tile management, and token positioning
 * Extracted from live.html/demo.html for modular architecture
 */

import { TILE_NAMES, BOARD_GRID, BOARD_SEQUENCE } from '../utils/constants.js';

export class BoardRenderer {
  constructor(options = {}) {
    // DOM elements
    this.tilesGrid = options.tilesGrid;
    this.tokensLayer = options.tokensLayer;
    this.boardWrapper = options.boardWrapper;
    this.board3d = options.board3d;
    
    // Control elements
    this.zoomRange = options.zoomRange;
    this.rotateRange = options.rotateRange;
    this.rotateXRange = options.rotateXRange;
    this.rotateYRange = options.rotateYRange;
    this.offsetXRange = options.offsetXRange;
    this.offsetYRange = options.offsetYRange;
    this.viewToggle = options.viewToggle;
    this.viewStatus = options.viewStatus;
    
    // Sound manager (optional)
    this.soundManager = options.soundManager;
    
    // State
    this.tileCenters = {};
    this.tokensByPlayerId = {};
    this.is3DView = false;
    this.isDiceAnimationPlaying = false; // External animation flag
    
    // Initialize
    this.initTiles();
    this.setupEventListeners();
    this.computeTileCenters();
  }
  
  /**
   * Set external animation flag (used to prevent token movement during dice animations)
   */
  setAnimationFlag(isPlaying) {
    this.isDiceAnimationPlaying = isPlaying;
  }
  
  /**
   * Initialize the 20-tile board grid
   */
  initTiles() {
    if (!this.tilesGrid) return;
    
    BOARD_GRID.forEach((tileNum) => {
      const tile = document.createElement("div");
      
      if (tileNum === 0) {
        // Empty center tiles
        tile.className = "tile empty";
      } else {
        // Game tiles
        tile.className = "tile";
        tile.dataset.tile = tileNum;
        
        const inner = document.createElement("div");
        inner.className = "tile-inner";
        inner.style.backgroundImage = `url('/assets/tiles/tile-${tileNum}.jpg')`;
        
        const label = document.createElement("div");
        label.className = "tile-label";
        label.textContent = TILE_NAMES[tileNum - 1] || `Tile ${tileNum}`;
        
        inner.appendChild(label);
        tile.appendChild(inner);
      }
      
      this.tilesGrid.appendChild(tile);
    });
  }
  
  /**
   * Compute center positions of all tiles (for token positioning)
   */
  computeTileCenters() {
    if (!this.tilesGrid || !this.tokensLayer) return;
    
    const tokensLayerRect = this.tokensLayer.getBoundingClientRect();
    
    this.tilesGrid.querySelectorAll(".tile[data-tile]").forEach(tile => {
      const rect = tile.getBoundingClientRect();
      const tileIndex = tile.dataset.tile;
      
      this.tileCenters[tileIndex] = {
        x: rect.left - tokensLayerRect.left + rect.width / 2,
        y: rect.top - tokensLayerRect.top + rect.height / 2
      };
    });
  }
  
  /**
   * Update board 3D transformation based on control sliders
   */
  updateBoardTransform() {
    if (!this.boardWrapper || !this.zoomRange) return;
    
    const zoom = parseInt(this.zoomRange.value, 10) / 100;
    
    if (!this.is3DView) {
      // 2D top view - only apply zoom
      this.boardWrapper.style.transform = `scale(${zoom})`;
    } else {
      // 3D view - apply zoom and rotation
      const rotX = parseInt(this.rotateXRange?.value || 0, 10);
      const rotY = parseInt(this.rotateYRange?.value || 0, 10);
      const rotZ = parseInt(this.rotateRange?.value || 0, 10);
      this.boardWrapper.style.transform = `scale(${zoom}) rotateX(${rotX}deg) rotateY(${rotY}deg) rotateZ(${rotZ}deg)`;
    }
  }
  
  /**
   * Toggle between 2D and 3D view
   */
  toggleView() {
    this.is3DView = !this.is3DView;
    
    if (this.viewStatus) {
      this.viewStatus.textContent = this.is3DView ? "3D" : "Top";
    }
    
    if (this.is3DView) {
      // 3D view - restore slider values
      this.updateBoardTransform();
      if (this.board3d) {
        this.board3d.style.transform = "";
      }
    } else {
      // Top view - completely flat
      const zoom = parseInt(this.zoomRange?.value || 100, 10) / 100;
      if (this.boardWrapper) {
        this.boardWrapper.style.transform = `scale(${zoom})`;
      }
      if (this.board3d) {
        this.board3d.style.transform = "rotateX(0deg) rotateY(0deg) rotateZ(0deg)";
      }
    }
    
    // Recalculate tile centers and reposition tokens after view change
    setTimeout(() => {
      this.computeTileCenters();
      this.positionExistingTokens();
    }, 100);
  }
  
  /**
   * Setup event listeners for controls
   */
  setupEventListeners() {
    // Zoom and rotation controls
    this.zoomRange?.addEventListener("input", () => this.updateBoardTransform());
    this.rotateRange?.addEventListener("input", () => this.updateBoardTransform());
    this.rotateXRange?.addEventListener("input", () => this.updateBoardTransform());
    this.rotateYRange?.addEventListener("input", () => this.updateBoardTransform());
    
    // Coin offset controls
    this.offsetXRange?.addEventListener("input", () => this.updateCoinOffsetDisplay());
    this.offsetYRange?.addEventListener("input", () => this.updateCoinOffsetDisplay());
    
    // View toggle
    this.viewToggle?.addEventListener("click", () => this.toggleView());
    
    // Window resize
    window.addEventListener("resize", () => {
      this.computeTileCenters();
      this.positionExistingTokens();
    });
  }
  
  /**
   * Update coin offset display values
   */
  updateCoinOffsetDisplay() {
    const offsetXValue = document.getElementById("offsetXValue");
    const offsetYValue = document.getElementById("offsetYValue");
    
    if (offsetXValue && this.offsetXRange) {
      offsetXValue.textContent = this.offsetXRange.value;
    }
    if (offsetYValue && this.offsetYRange) {
      offsetYValue.textContent = this.offsetYRange.value;
    }
  }
  
  /**
   * Ensure token element exists for a player
   */
  ensureTokenForPlayer(player) {
    const existingToken = this.tokensByPlayerId[player.id];
    
    // If token exists, update its color in case it changed
    if (existingToken) {
      const color = player.color || 'red';
      existingToken.style.backgroundImage = `url("/assets/pawns/pawn-${color}.svg")`;
      return existingToken;
    }
    
    // Create new token
    const token = document.createElement("div");
    token.className = "player-token";
    token.dataset.player = player.id;
    
    // Set token color from player object
    const color = player.color || 'red';
    token.style.backgroundImage = `url("/assets/pawns/pawn-${color}.svg")`;
    
    const label = document.createElement("div");
    label.className = "player-token-label";
    label.textContent = player.name || player.id;
    token.appendChild(label);
    
    this.tokensLayer.appendChild(token);
    this.tokensByPlayerId[player.id] = token;
    return token;
  }
  
  /**
   * Position a token on a specific tile
   */
  positionToken(playerId, tileIndex, offsetIndex, animate = false, fromTile = null) {
    const token = this.tokensByPlayerId[playerId];
    if (!token || !this.tilesGrid) return;
    
    // Prevent immediate repositioning while a dice animation is playing
    if (this.isDiceAnimationPlaying && !animate) {
      const currentIdx = (token.dataset.tileIndex !== undefined && token.dataset.tileIndex !== '') 
        ? parseInt(token.dataset.tileIndex) 
        : null;
      if (currentIdx !== null && currentIdx !== tileIndex) {
        console.debug('[BoardRenderer] positionToken deferred due to dice animation', playerId, 'from', currentIdx, 'to', tileIndex);
        return;
      }
    }
    
    // Find the target tile element
    const tileElem = this.tilesGrid.querySelector(`.tile[data-tile="${tileIndex}"]`);
    if (!tileElem) return;
    
    // If animating, calculate path and animate
    if (animate && fromTile !== null && fromTile !== tileIndex) {
      this.animateTokenWalk(playerId, fromTile, tileIndex, offsetIndex);
      return;
    }
    
    // Immediate positioning (no animation)
    if (token.parentElement !== tileElem) {
      tileElem.appendChild(token);
    }
    
    // Compute per-player offsets (arranged 2x2 around center)
    const baseOffsetX = parseInt(this.offsetXRange?.value || 0, 10);
    const baseOffsetY = parseInt(this.offsetYRange?.value || 0, 10);
    const offsets = [
      { dx: baseOffsetX - 8, dy: baseOffsetY - 8 },
      { dx: baseOffsetX + 8, dy: baseOffsetY - 8 },
      { dx: baseOffsetX - 8, dy: baseOffsetY + 8 },
      { dx: baseOffsetX + 8, dy: baseOffsetY + 8 }
    ];
    const off = offsets[offsetIndex % 4] || { dx: 0, dy: 0 };
    
    // Center token within tile and apply offsets
    token.style.left = `calc(50% + ${off.dx}px)`;
    token.style.top = `calc(50% + ${off.dy}px)`;
    token.style.transform = this.is3DView
      ? "translate(-50%, -50%) translateZ(20px)"
      : "translate(-50%, -50%)";
    
    // Update label to show tile number
    const label = token.querySelector(".player-token-label");
    if (label) label.textContent = `Tile ${tileIndex}`;
  }
  
  /**
   * Animate token walking from one tile to another
   */
  animateTokenWalk(playerId, fromTileIndex, toTileIndex, offsetIndex, onComplete) {
    const token = this.tokensByPlayerId[playerId];
    if (!token) return;
    
    console.debug('[BoardRenderer] animateTokenWalk START', playerId, 'from', fromTileIndex, 'to', toTileIndex, 'offset', offsetIndex);
    
    // Calculate path (tiles to walk through)
    const path = this.calculateTilePath(fromTileIndex, toTileIndex);
    if (path.length === 0) return;
    
    let currentStep = 0;
    const stepDuration = 600; // ms per tile
    
    const walkNextStep = () => {
      if (currentStep >= path.length) {
        // Animation complete - position at final tile
        this.positionToken(playerId, toTileIndex, offsetIndex, false);
        
        // Play tile landing sound
        if (this.soundManager && typeof this.soundManager.play === 'function') {
          this.soundManager.play('tile');
        }
        
        if (typeof onComplete === 'function') {
          try {
            onComplete();
          } catch (err) {
            console.error(err);
          }
        }
        
        console.debug('[BoardRenderer] animateTokenWalk END', playerId, 'arrived', toTileIndex);
        return;
      }
      
      const currentTile = path[currentStep];
      const tileElem = this.tilesGrid.querySelector(`.tile[data-tile="${currentTile}"]`);
      
      if (tileElem && token.parentElement !== tileElem) {
        tileElem.appendChild(token);
      }
      
      // Play movement sound for each step
      if (currentStep > 0 && this.soundManager && typeof this.soundManager.play === 'function') {
        this.soundManager.play('move');
      }
      
      // Apply position with transition
      const baseOffsetX = parseInt(this.offsetXRange?.value || 0, 10);
      const baseOffsetY = parseInt(this.offsetYRange?.value || 0, 10);
      const offsets = [
        { dx: baseOffsetX - 8, dy: baseOffsetY - 8 },
        { dx: baseOffsetX + 8, dy: baseOffsetY - 8 },
        { dx: baseOffsetX - 8, dy: baseOffsetY + 8 },
        { dx: baseOffsetX + 8, dy: baseOffsetY + 8 }
      ];
      const off = offsets[offsetIndex % 4] || { dx: 0, dy: 0 };
      
      token.style.transition = `all ${stepDuration}ms ease-in-out`;
      token.style.left = `calc(50% + ${off.dx}px)`;
      token.style.top = `calc(50% + ${off.dy}px)`;
      
      currentStep++;
      setTimeout(walkNextStep, stepDuration);
    };
    
    walkNextStep();
  }
  
  /**
   * Calculate the path of tiles from start to end
   */
  calculateTilePath(fromTile, toTile) {
    const fromIndex = BOARD_SEQUENCE.indexOf(fromTile);
    const toIndex = BOARD_SEQUENCE.indexOf(toTile);
    
    if (fromIndex === -1 || toIndex === -1) return [];
    
    const path = [];
    
    if (fromIndex < toIndex) {
      // Moving forward
      for (let i = fromIndex + 1; i <= toIndex; i++) {
        path.push(BOARD_SEQUENCE[i]);
      }
    } else {
      // Moving backward or wrapping around
      for (let i = fromIndex + 1; i < BOARD_SEQUENCE.length; i++) {
        path.push(BOARD_SEQUENCE[i]);
      }
      for (let i = 0; i <= toIndex; i++) {
        path.push(BOARD_SEQUENCE[i]);
      }
    }
    
    return path;
  }
  
  /**
   * Reposition all existing tokens (useful after view changes or window resize)
   */
  positionExistingTokens() {
    Object.values(this.tokensByPlayerId).forEach((token, index) => {
      const tileIndex = token.dataset.tileIndex;
      if (tileIndex) {
        this.positionToken(token.dataset.player, tileIndex, index);
      }
    });
  }
  
  /**
   * Get token element for a player
   */
  getToken(playerId) {
    return this.tokensByPlayerId[playerId];
  }
  
  /**
   * Remove token for a player
   */
  removeToken(playerId) {
    const token = this.tokensByPlayerId[playerId];
    if (token) {
      token.remove();
      delete this.tokensByPlayerId[playerId];
    }
  }
  
  /**
   * Hide all tokens (useful for game reset)
   */
  hideAllTokens() {
    Object.values(this.tokensByPlayerId).forEach(token => {
      token.style.display = 'none';
    });
  }
  
  /**
   * Show all tokens
   */
  showAllTokens() {
    Object.values(this.tokensByPlayerId).forEach(token => {
      token.style.display = 'block';
    });
  }
  
  /**
   * Mark token as eliminated
   */
  markTokenAsEliminated(playerId) {
    const token = this.tokensByPlayerId[playerId];
    if (token) {
      token.classList.add('eliminated');
    }
  }
  
  /**
   * Unmark token as eliminated
   */
  unmarkTokenAsEliminated(playerId) {
    const token = this.tokensByPlayerId[playerId];
    if (token) {
      token.classList.remove('eliminated');
    }
  }
}
