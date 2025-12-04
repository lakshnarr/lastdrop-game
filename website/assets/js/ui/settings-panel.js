/**
 * SettingsPanel.js
 * 
 * Manages the settings sidebar panel with controls for:
 * - Board view (2D/3D toggle, zoom, rotation, tilt)
 * - Audio controls (master toggle, individual volume controls)
 * - Coin offset adjustments (developer mode)
 * - Kid mode, dev mode, demo mode toggles
 * 
 * Integration:
 * const settingsPanel = new SettingsPanel({
 *   panel: document.getElementById('settingsPanel'),
 *   backdrop: document.getElementById('settingsBackdrop'),
 *   openBtn: document.getElementById('settingsBtn'),
 *   closeBtn: document.getElementById('closeSettings'),
 *   viewToggle: document.getElementById('viewToggle'),
 *   viewStatus: document.getElementById('viewStatus'),
 *   audioToggle: document.getElementById('audioToggle'),
 *   audioStatus: document.getElementById('audioStatus'),
 *   // ... other elements
 *   boardRenderer: boardRenderer,  // Optional
 *   audioManager: audioManager     // Optional
 * });
 */

export class SettingsPanel {
  /**
   * Initialize the settings panel manager
   * @param {Object} config - Configuration object
   */
  constructor({
    panel,
    backdrop,
    openBtn,
    closeBtn,
    viewToggle,
    viewStatus,
    zoomRange,
    rotateRange,
    rotateXRange,
    rotateYRange,
    audioToggle,
    audioStatus,
    bgMusicVolume,
    diceVolume,
    moveVolume,
    chanceVolume,
    tileVolume,
    eliminatedVolume,
    winnerVolume,
    offsetXRange,
    offsetYRange,
    offsetXValue,
    offsetYValue,
    applyCoinOffset,
    kidToggle,
    kidStatus,
    devToggle,
    devStatus,
    devControls,
    demoToggle,
    demoStatus,
    helpBtn,
    boardRenderer = null,
    audioManager = null
  }) {
    // Panel elements
    this.panel = panel;
    this.backdrop = backdrop;
    this.openBtn = openBtn;
    this.closeBtn = closeBtn;
    
    // View controls
    this.viewToggle = viewToggle;
    this.viewStatus = viewStatus;
    this.zoomRange = zoomRange;
    this.rotateRange = rotateRange;
    this.rotateXRange = rotateXRange;
    this.rotateYRange = rotateYRange;
    
    // Audio controls
    this.audioToggle = audioToggle;
    this.audioStatus = audioStatus;
    this.bgMusicVolume = bgMusicVolume;
    this.diceVolume = diceVolume;
    this.moveVolume = moveVolume;
    this.chanceVolume = chanceVolume;
    this.tileVolume = tileVolume;
    this.eliminatedVolume = eliminatedVolume;
    this.winnerVolume = winnerVolume;
    
    // Coin offset controls
    this.offsetXRange = offsetXRange;
    this.offsetYRange = offsetYRange;
    this.offsetXValue = offsetXValue;
    this.offsetYValue = offsetYValue;
    this.applyCoinOffset = applyCoinOffset;
    
    // Mode toggles
    this.kidToggle = kidToggle;
    this.kidStatus = kidStatus;
    this.devToggle = devToggle;
    this.devStatus = devStatus;
    this.devControls = devControls;
    this.demoToggle = demoToggle;
    this.demoStatus = demoStatus;
    this.helpBtn = helpBtn;
    
    // Optional integrations
    this.boardRenderer = boardRenderer;
    this.audioManager = audioManager;
    
    // Initialize event listeners
    this.initEventListeners();
  }

  /**
   * Initialize all event listeners
   */
  initEventListeners() {
    // Panel open/close
    this.openBtn?.addEventListener('click', () => this.open());
    this.closeBtn?.addEventListener('click', () => this.close());
    this.backdrop?.addEventListener('click', () => this.close());
    
    // View controls (delegated to boardRenderer if available)
    this.viewToggle?.addEventListener('click', () => {
      if (this.boardRenderer && this.boardRenderer.toggleView) {
        this.boardRenderer.toggleView();
        this.updateViewStatus();
      }
    });
    
    this.zoomRange?.addEventListener('input', (e) => {
      if (this.boardRenderer && this.boardRenderer.updateBoardTransform) {
        this.boardRenderer.updateBoardTransform();
      }
    });
    
    this.rotateRange?.addEventListener('input', (e) => {
      if (this.boardRenderer && this.boardRenderer.updateBoardTransform) {
        this.boardRenderer.updateBoardTransform();
      }
    });
    
    this.rotateXRange?.addEventListener('input', (e) => {
      if (this.boardRenderer && this.boardRenderer.updateBoardTransform) {
        this.boardRenderer.updateBoardTransform();
      }
    });
    
    this.rotateYRange?.addEventListener('input', (e) => {
      if (this.boardRenderer && this.boardRenderer.updateBoardTransform) {
        this.boardRenderer.updateBoardTransform();
      }
    });
    
    // Audio controls (delegated to audioManager if available)
    this.audioToggle?.addEventListener('click', () => {
      if (this.audioManager) {
        const isEnabled = this.audioManager.toggle();
        this.updateAudioStatus(isEnabled);
      }
    });
    
    // Volume controls
    const volumeControls = [
      { element: this.bgMusicVolume, type: 'bgMusic', display: 'bgMusicValue' },
      { element: this.diceVolume, type: 'dice', display: 'diceValue' },
      { element: this.moveVolume, type: 'move', display: 'moveValue' },
      { element: this.chanceVolume, type: 'chance', display: 'chanceValue' },
      { element: this.tileVolume, type: 'tile', display: 'tileValue' },
      { element: this.eliminatedVolume, type: 'eliminated', display: 'eliminatedValue' },
      { element: this.winnerVolume, type: 'winner', display: 'winnerValue' }
    ];
    
    volumeControls.forEach(({ element, type, display }) => {
      element?.addEventListener('input', (e) => {
        const value = parseInt(e.target.value, 10) / 100;
        if (this.audioManager) {
          this.audioManager.setVolume(type, value);
        }
        
        const valueDisplay = document.getElementById(display);
        if (valueDisplay) {
          valueDisplay.textContent = `${e.target.value}%`;
        }
      });
    });
    
    // Coin offset controls
    this.applyCoinOffset?.addEventListener('click', () => {
      this.applyCoinOffsets();
    });
    
    // Mode toggles
    this.kidToggle?.addEventListener('click', () => this.toggleKidMode());
    this.devToggle?.addEventListener('click', () => this.toggleDevMode());
    this.demoToggle?.addEventListener('click', () => this.toggleDemoMode());
    
    // Help button
    this.helpBtn?.addEventListener('click', () => this.showHelp());
  }

  /**
   * Open settings panel
   */
  open() {
    this.panel?.classList.remove('hidden');
    this.backdrop?.classList.remove('hidden');
  }

  /**
   * Close settings panel
   */
  close() {
    this.panel?.classList.add('hidden');
    this.backdrop?.classList.add('hidden');
  }

  /**
   * Toggle settings panel
   */
  toggle() {
    if (this.panel?.classList.contains('hidden')) {
      this.open();
    } else {
      this.close();
    }
  }

  /**
   * Update view status text
   */
  updateViewStatus() {
    if (!this.viewStatus || !this.boardRenderer) return;
    
    const is3D = this.boardRenderer.is3DView;
    this.viewStatus.textContent = is3D ? '3D' : 'Top';
  }

  /**
   * Update audio status text
   * @param {boolean} isEnabled - Whether audio is enabled
   */
  updateAudioStatus(isEnabled) {
    if (this.audioStatus) {
      this.audioStatus.textContent = isEnabled ? 'ON' : 'OFF';
    }
  }

  /**
   * Apply coin offset adjustments
   */
  applyCoinOffsets() {
    if (!this.boardRenderer) return;
    
    const offsetX = parseInt(this.offsetXRange?.value || 0, 10);
    const offsetY = parseInt(this.offsetYRange?.value || 0, 10);
    
    // Update display values
    if (this.offsetXValue) {
      this.offsetXValue.textContent = offsetX;
    }
    if (this.offsetYValue) {
      this.offsetYValue.textContent = offsetY;
    }
    
    // Apply to board renderer if method exists
    if (this.boardRenderer.setTokenOffset) {
      this.boardRenderer.setTokenOffset(offsetX, offsetY);
    }
    
    console.log(`Coin offsets applied: X=${offsetX}, Y=${offsetY}`);
  }

  /**
   * Toggle kid mode
   */
  toggleKidMode() {
    const isKidMode = this.kidStatus?.textContent === 'ON';
    const newStatus = !isKidMode;
    
    if (this.kidStatus) {
      this.kidStatus.textContent = newStatus ? 'ON' : 'OFF';
    }
    
    // Apply kid mode changes (e.g., hide complex controls)
    if (newStatus) {
      // Hide dev controls in kid mode
      this.devControls?.classList.add('hidden');
    }
    
    console.log(`Kid mode: ${newStatus ? 'ON' : 'OFF'}`);
  }

  /**
   * Toggle dev mode
   */
  toggleDevMode() {
    const isDevMode = this.devStatus?.textContent === 'ON';
    const newStatus = !isDevMode;
    
    if (this.devStatus) {
      this.devStatus.textContent = newStatus ? 'ON' : 'OFF';
    }
    
    // Show/hide dev controls
    if (this.devControls) {
      if (newStatus) {
        this.devControls.classList.remove('hidden');
      } else {
        this.devControls.classList.add('hidden');
      }
    }
    
    console.log(`Dev mode: ${newStatus ? 'ON' : 'OFF'}`);
  }

  /**
   * Toggle demo mode
   */
  toggleDemoMode() {
    const isDemoMode = this.demoStatus?.textContent === 'ON';
    const newStatus = !isDemoMode;
    
    if (this.demoStatus) {
      this.demoStatus.textContent = newStatus ? 'ON' : 'OFF';
    }
    
    console.log(`Demo mode: ${newStatus ? 'ON' : 'OFF'}`);
    
    // Return new status for external handling
    return newStatus;
  }

  /**
   * Show help/tutorial
   */
  showHelp() {
    console.log('Showing tutorial...');
    // This can trigger a welcome overlay or tutorial
    // Implement based on your needs
  }

  /**
   * Get current zoom value
   * @returns {number} Zoom percentage
   */
  getZoom() {
    return parseInt(this.zoomRange?.value || 100, 10);
  }

  /**
   * Set zoom value
   * @param {number} value - Zoom percentage
   */
  setZoom(value) {
    if (this.zoomRange) {
      this.zoomRange.value = value;
      if (this.boardRenderer && this.boardRenderer.updateBoardTransform) {
        this.boardRenderer.updateBoardTransform();
      }
    }
  }

  /**
   * Reset all settings to defaults
   */
  resetToDefaults() {
    // Reset zoom and rotation
    if (this.zoomRange) this.zoomRange.value = 100;
    if (this.rotateRange) this.rotateRange.value = 45;
    if (this.rotateXRange) this.rotateXRange.value = 55;
    if (this.rotateYRange) this.rotateYRange.value = 0;
    
    // Reset coin offsets
    if (this.offsetXRange) this.offsetXRange.value = -12;
    if (this.offsetYRange) this.offsetYRange.value = 0;
    
    // Reset volume controls
    if (this.bgMusicVolume) this.bgMusicVolume.value = 30;
    if (this.diceVolume) this.diceVolume.value = 50;
    if (this.moveVolume) this.moveVolume.value = 40;
    if (this.chanceVolume) this.chanceVolume.value = 50;
    if (this.tileVolume) this.tileVolume.value = 40;
    if (this.eliminatedVolume) this.eliminatedVolume.value = 60;
    if (this.winnerVolume) this.winnerVolume.value = 70;
    
    // Apply changes
    if (this.boardRenderer && this.boardRenderer.updateBoardTransform) {
      this.boardRenderer.updateBoardTransform();
    }
    
    console.log('Settings reset to defaults');
  }
}
