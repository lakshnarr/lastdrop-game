/**
 * AudioManager - Handles all sound effects and audio playback
 * Extracted from live.html/demo.html for modular architecture
 */

import { DEFAULT_AUDIO_VOLUMES, SOUND_FREQUENCIES } from '../utils/constants.js';

export class AudioManager {
  constructor(options = {}) {
    // Audio state
    this.enabled = options.enabled !== undefined ? options.enabled : true;
    this.volumes = { ...DEFAULT_AUDIO_VOLUMES };
    
    // Background music
    this.bgMusicInterval = null;
    
    // Web Audio API context (created on-demand to avoid autoplay restrictions)
    this.audioContext = null;
  }
  
  /**
   * Get or create Web Audio API context
   */
  getAudioContext() {
    if (!this.audioContext) {
      this.audioContext = new (window.AudioContext || window.webkitAudioContext)();
    }
    return this.audioContext;
  }
  
  /**
   * Enable audio playback
   */
  enable() {
    this.enabled = true;
  }
  
  /**
   * Disable audio playback
   */
  disable() {
    this.enabled = false;
    this.stopBackgroundMusic();
  }
  
  /**
   * Toggle audio on/off
   */
  toggle() {
    this.enabled = !this.enabled;
    if (!this.enabled) {
      this.stopBackgroundMusic();
    }
    return this.enabled;
  }
  
  /**
   * Check if audio is enabled
   */
  isEnabled() {
    return this.enabled;
  }
  
  /**
   * Set volume for a specific sound type
   */
  setVolume(type, volume) {
    if (this.volumes.hasOwnProperty(type)) {
      this.volumes[type] = Math.max(0, Math.min(1, volume)); // Clamp 0-1
    }
  }
  
  /**
   * Get volume for a specific sound type
   */
  getVolume(type) {
    return this.volumes[type] || 0.5;
  }
  
  /**
   * Play a sound effect (using Web Audio API beep generator)
   * In production, this should load and play actual audio files
   */
  play(type) {
    if (!this.enabled) return;
    
    try {
      const ctx = this.getAudioContext();
      const oscillator = ctx.createOscillator();
      const gainNode = ctx.createGain();
      
      oscillator.connect(gainNode);
      gainNode.connect(ctx.destination);
      
      // Set frequency based on sound type
      const frequency = SOUND_FREQUENCIES[type] || 440;
      oscillator.frequency.value = frequency;
      oscillator.type = 'sine';
      
      // Set volume
      const volume = this.volumes[type] || 0.5;
      gainNode.gain.setValueAtTime(volume, ctx.currentTime);
      gainNode.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.2);
      
      // Play sound
      oscillator.start(ctx.currentTime);
      oscillator.stop(ctx.currentTime + 0.2);
    } catch (err) {
      console.warn('[AudioManager] Failed to play sound:', type, err);
    }
  }
  
  /**
   * Play dice roll sound
   */
  playDice() {
    this.play('dice');
  }
  
  /**
   * Play token movement sound
   */
  playMove() {
    this.play('move');
  }
  
  /**
   * Play chance card sound
   */
  playChance() {
    this.play('chance');
  }
  
  /**
   * Play tile landing sound
   */
  playTile() {
    this.play('tile');
  }
  
  /**
   * Play elimination sound
   */
  playEliminated() {
    this.play('eliminated');
  }
  
  /**
   * Play winner celebration sound
   */
  playWinner() {
    this.play('winner');
  }
  
  /**
   * Start background music loop
   * TODO: In production, load actual audio file and loop it
   */
  startBackgroundMusic() {
    if (this.bgMusicInterval) return;
    if (!this.enabled) return;
    
    // Placeholder - in production, use actual audio file
    // Example:
    // const audio = new Audio('/assets/sounds/bgmusic.mp3');
    // audio.loop = true;
    // audio.volume = this.volumes.bgMusic;
    // audio.play();
    
    console.log('[AudioManager] Background music placeholder (not implemented)');
  }
  
  /**
   * Stop background music
   */
  stopBackgroundMusic() {
    if (this.bgMusicInterval) {
      clearInterval(this.bgMusicInterval);
      this.bgMusicInterval = null;
    }
    
    // TODO: In production, stop actual audio file
  }
  
  /**
   * Load audio file (for production use)
   * @param {string} url - URL to audio file
   * @param {string} type - Sound type identifier
   * @returns {Promise<Audio>}
   */
  async loadAudioFile(url, type) {
    return new Promise((resolve, reject) => {
      const audio = new Audio(url);
      
      audio.addEventListener('canplaythrough', () => {
        audio.volume = this.volumes[type] || 0.5;
        resolve(audio);
      });
      
      audio.addEventListener('error', (err) => {
        console.error(`[AudioManager] Failed to load ${type}:`, url, err);
        reject(err);
      });
      
      audio.load();
    });
  }
  
  /**
   * Preload all audio files (for production use)
   * @param {Object} audioFiles - Map of type to URL
   * @returns {Promise<void>}
   */
  async preloadAll(audioFiles = {}) {
    const promises = Object.entries(audioFiles).map(([type, url]) => 
      this.loadAudioFile(url, type).catch(err => {
        console.warn(`[AudioManager] Failed to preload ${type}:`, err);
        return null;
      })
    );
    
    await Promise.all(promises);
    console.log('[AudioManager] Preload complete');
  }
  
  /**
   * Resume audio context (required after user interaction on some browsers)
   */
  async resume() {
    if (this.audioContext && this.audioContext.state === 'suspended') {
      try {
        await this.audioContext.resume();
        console.log('[AudioManager] Audio context resumed');
      } catch (err) {
        console.warn('[AudioManager] Failed to resume audio context:', err);
      }
    }
  }
}
