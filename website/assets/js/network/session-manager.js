/**
 * SessionManager.js
 * 
 * Manages game session state and identifiers.
 * Handles session ID generation, parsing from URL, and session persistence.
 * 
 * Integration:
 * const sessionManager = new SessionManager({
 *   onSessionChange: (boardId, sessionId) => { ... }
 * });
 * 
 * sessionManager.parseFromUrl();  // Auto-detect from URL params
 * sessionManager.setSession('LASTDROP-0001', 'uuid-1234');
 * const sessionUrl = sessionManager.generateSessionUrl();
 */

export class SessionManager {
  /**
   * Initialize the session manager
   * @param {Object} config - Configuration object
   * @param {Function} config.onSessionChange - Callback when session changes (boardId, sessionId)
   */
  constructor({ onSessionChange = null } = {}) {
    this.boardId = null;
    this.sessionId = null;
    this.onSessionChange = onSessionChange;
  }

  /**
   * Parse session from URL query parameters
   * Supports two formats:
   * - ?session=BOARDID_SESSIONID (e.g., ?session=LASTDROP-0001_abc-123)
   * - ?board=BOARDID (e.g., ?board=LASTDROP-0001)
   * @returns {Object} Object with boardId and sessionId
   */
  parseFromUrl() {
    const urlParams = new URLSearchParams(window.location.search);
    const sessionParam = urlParams.get('session');
    const boardParam = urlParams.get('board');
    
    if (sessionParam) {
      // Format: "BOARDID_SESSIONID"
      const parts = sessionParam.split('_');
      this.boardId = parts[0];
      this.sessionId = parts.length > 1 ? parts[1] : null;
    } else if (boardParam) {
      // Format: just board ID
      this.boardId = boardParam;
      this.sessionId = null;
    }
    
    if (this.boardId) {
      console.log('[SessionManager] Parsed from URL:', { 
        boardId: this.boardId, 
        sessionId: this.sessionId 
      });
      
      if (this.onSessionChange) {
        this.onSessionChange(this.boardId, this.sessionId);
      }
    }
    
    return {
      boardId: this.boardId,
      sessionId: this.sessionId
    };
  }

  /**
   * Set session parameters manually
   * @param {string} boardId - Board identifier
   * @param {string} sessionId - Session identifier (optional)
   */
  setSession(boardId, sessionId = null) {
    this.boardId = boardId;
    this.sessionId = sessionId;
    
    console.log('[SessionManager] Session set:', { boardId, sessionId });
    
    if (this.onSessionChange) {
      this.onSessionChange(boardId, sessionId);
    }
  }

  /**
   * Generate a new session ID
   * @returns {string} UUID-style session ID
   */
  generateSessionId() {
    // Simple UUID v4 generator
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
      const r = Math.random() * 16 | 0;
      const v = c === 'x' ? r : (r & 0x3 | 0x8);
      return v.toString(16);
    });
  }

  /**
   * Generate a shareable session URL
   * @param {string} baseUrl - Base URL (default: current page)
   * @returns {string} Complete session URL
   */
  generateSessionUrl(baseUrl = null) {
    if (!this.boardId) {
      console.warn('[SessionManager] Cannot generate URL without boardId');
      return null;
    }
    
    const base = baseUrl || window.location.origin + window.location.pathname;
    
    if (this.sessionId) {
      return `${base}?session=${this.boardId}_${this.sessionId}`;
    } else {
      return `${base}?board=${this.boardId}`;
    }
  }

  /**
   * Update URL in browser without page reload
   * @param {boolean} replaceState - Use replaceState instead of pushState (default: true)
   */
  updateBrowserUrl(replaceState = true) {
    if (!this.boardId) {
      return;
    }
    
    const sessionUrl = this.generateSessionUrl('');
    
    if (replaceState) {
      window.history.replaceState({}, '', sessionUrl);
    } else {
      window.history.pushState({}, '', sessionUrl);
    }
    
    console.log('[SessionManager] Browser URL updated:', sessionUrl);
  }

  /**
   * Get current session info
   * @returns {Object} Object with boardId and sessionId
   */
  getSession() {
    return {
      boardId: this.boardId,
      sessionId: this.sessionId
    };
  }

  /**
   * Get board ID
   * @returns {string|null} Current board ID
   */
  getBoardId() {
    return this.boardId;
  }

  /**
   * Get session ID
   * @returns {string|null} Current session ID
   */
  getSessionId() {
    return this.sessionId;
  }

  /**
   * Check if session is set
   * @returns {boolean} True if boardId is set
   */
  hasSession() {
    return this.boardId !== null;
  }

  /**
   * Clear session
   */
  clearSession() {
    this.boardId = null;
    this.sessionId = null;
    
    console.log('[SessionManager] Session cleared');
    
    if (this.onSessionChange) {
      this.onSessionChange(null, null);
    }
  }

  /**
   * Store session in localStorage for persistence
   * @param {string} key - Storage key (default: 'lastdrop_session')
   */
  saveToStorage(key = 'lastdrop_session') {
    if (!this.boardId) {
      return;
    }
    
    const sessionData = {
      boardId: this.boardId,
      sessionId: this.sessionId,
      timestamp: Date.now()
    };
    
    try {
      localStorage.setItem(key, JSON.stringify(sessionData));
      console.log('[SessionManager] Session saved to localStorage');
    } catch (err) {
      console.error('[SessionManager] Failed to save session:', err);
    }
  }

  /**
   * Load session from localStorage
   * @param {string} key - Storage key (default: 'lastdrop_session')
   * @param {number} maxAge - Maximum age in milliseconds (default: 24 hours)
   * @returns {boolean} True if session was loaded
   */
  loadFromStorage(key = 'lastdrop_session', maxAge = 24 * 60 * 60 * 1000) {
    try {
      const stored = localStorage.getItem(key);
      if (!stored) {
        return false;
      }
      
      const sessionData = JSON.parse(stored);
      
      // Check if session is too old
      if (maxAge && (Date.now() - sessionData.timestamp) > maxAge) {
        console.log('[SessionManager] Stored session expired');
        localStorage.removeItem(key);
        return false;
      }
      
      this.boardId = sessionData.boardId;
      this.sessionId = sessionData.sessionId || null;
      
      console.log('[SessionManager] Session loaded from localStorage:', {
        boardId: this.boardId,
        sessionId: this.sessionId
      });
      
      if (this.onSessionChange) {
        this.onSessionChange(this.boardId, this.sessionId);
      }
      
      return true;
      
    } catch (err) {
      console.error('[SessionManager] Failed to load session:', err);
      return false;
    }
  }

  /**
   * Generate QR code data for session sharing
   * @returns {string} Session URL suitable for QR code
   */
  getQRCodeData() {
    return this.generateSessionUrl(window.location.origin + window.location.pathname);
  }

  /**
   * Format session for display
   * @returns {string} Formatted session string
   */
  toString() {
    if (!this.boardId) {
      return 'No session';
    }
    
    if (this.sessionId) {
      return `${this.boardId} (Session: ${this.sessionId.substring(0, 8)}...)`;
    }
    
    return this.boardId;
  }
}
