/**
 * ApiClient.js
 * 
 * Handles API communication for fetching live game state.
 * Features exponential backoff retry logic, connection status management,
 * and automatic polling with configurable intervals.
 * 
 * Integration:
 * const apiClient = new ApiClient({
 *   baseUrl: '/api/live_state.php',
 *   apiKey: 'ABC123',
 *   pollingInterval: 2000,
 *   onStateUpdate: (state) => { ... },
 *   onConnectionChange: (status) => { ... },
 *   onRetry: (attempt, delay) => { ... },
 *   onError: (error, isFatal) => { ... }
 * });
 * 
 * apiClient.start();  // Start polling
 * apiClient.stop();   // Stop polling
 */

export class ApiClient {
  /**
   * Initialize the API client
   * @param {Object} config - Configuration object
   * @param {string} config.baseUrl - Base API URL
   * @param {string} config.apiKey - API key for authentication
   * @param {number} config.pollingInterval - Polling interval in ms (default: 2000)
   * @param {number} config.maxRetries - Maximum retry attempts (default: 5)
   * @param {Array<number>} config.retryDelays - Retry delays in ms (default: exponential backoff)
   * @param {Function} config.onStateUpdate - Callback when state is fetched (state)
   * @param {Function} config.onConnectionChange - Callback on connection status change (status)
   * @param {Function} config.onRetry - Callback on retry attempt (attempt, delay)
   * @param {Function} config.onError - Callback on error (error, isFatal)
   * @param {Function} config.onFirstConnection - Callback on first successful connection
   */
  constructor({
    baseUrl,
    apiKey,
    pollingInterval = 2000,
    maxRetries = 5,
    retryDelays = [2000, 4000, 8000, 16000, 32000],
    onStateUpdate = null,
    onConnectionChange = null,
    onRetry = null,
    onError = null,
    onFirstConnection = null
  }) {
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
    this.pollingInterval = pollingInterval;
    this.maxRetries = maxRetries;
    this.retryDelays = retryDelays;
    
    // Callbacks
    this.onStateUpdate = onStateUpdate;
    this.onConnectionChange = onConnectionChange;
    this.onRetry = onRetry;
    this.onError = onError;
    this.onFirstConnection = onFirstConnection;
    
    // State
    this.isPolling = false;
    this.pollingIntervalId = null;
    this.connectionEstablished = false;
    this.firstFetchComplete = false;
    this.isOnline = true;
    
    // Session parameters
    this.boardId = null;
    this.sessionId = null;
    
    // Last update timestamp
    this.lastUpdateTime = null;
  }

  /**
   * Build full API URL with parameters
   * @returns {string} Complete API URL
   */
  buildUrl() {
    let url = `${this.baseUrl}?key=${this.apiKey}`;
    
    if (this.boardId) {
      url += `&boardId=${encodeURIComponent(this.boardId)}`;
    }
    
    if (this.sessionId) {
      url += `&sessionId=${encodeURIComponent(this.sessionId)}`;
    }
    
    return url;
  }

  /**
   * Set session parameters for filtering game state
   * @param {string} boardId - Board ID to filter by
   * @param {string} sessionId - Session ID to filter by (optional)
   */
  setSession(boardId, sessionId = null) {
    this.boardId = boardId;
    this.sessionId = sessionId;
    console.log('[ApiClient] Session set:', { boardId, sessionId });
  }

  /**
   * Parse session from URL query parameters
   * Supports formats: ?session=BOARDID_SESSIONID or ?board=BOARDID
   */
  parseSessionFromUrl() {
    const urlParams = new URLSearchParams(window.location.search);
    const sessionParam = urlParams.get('session');  // Format: "LASTDROP-0001_uuid"
    const boardParam = urlParams.get('board');      // Alternative: just board ID
    
    if (sessionParam) {
      const parts = sessionParam.split('_');
      this.boardId = parts[0];
      this.sessionId = parts.length > 1 ? parts[1] : null;
    } else if (boardParam) {
      this.boardId = boardParam;
    }
    
    if (this.boardId) {
      console.log('[ApiClient] Session parsed from URL:', { 
        boardId: this.boardId, 
        sessionId: this.sessionId 
      });
    }
  }

  /**
   * Fetch game state from API with retry logic
   * @returns {Promise<Object>} Game state object
   */
  async fetch() {
    if (!navigator.onLine) {
      console.log('[ApiClient] Skipping fetch - offline');
      this.updateConnectionStatus('offline');
      return null;
    }
    
    let retryAttempt = 0;
    
    const attemptFetch = async () => {
      try {
        const url = this.buildUrl();
        const res = await fetch(url, { cache: "no-store" });
        
        if (!res.ok) {
          throw new Error(`HTTP ${res.status}`);
        }
        
        const state = await res.json();
        
        // Check if this is the first successful connection
        if (!this.connectionEstablished && state.players && state.players.length > 0) {
          this.connectionEstablished = true;
          this.updateConnectionStatus('connected');
          
          if (this.onFirstConnection) {
            this.onFirstConnection();
          }
        }
        
        // Mark first fetch as complete
        if (!this.firstFetchComplete) {
          this.firstFetchComplete = true;
        }
        
        // Update last update time
        this.lastUpdateTime = new Date();
        
        // Reset retry counter on success
        retryAttempt = 0;
        
        // Update connection status
        this.updateConnectionStatus('online');
        
        // Trigger state update callback
        if (this.onStateUpdate) {
          this.onStateUpdate(state);
        }
        
        return state;
        
      } catch (err) {
        console.error(`[ApiClient] Fetch error (attempt ${retryAttempt + 1}/${this.maxRetries}):`, err);
        
        if (retryAttempt < this.maxRetries) {
          const delay = this.retryDelays[retryAttempt];
          
          // Trigger retry callback
          if (this.onRetry) {
            this.onRetry(retryAttempt + 1, delay);
          }
          
          // Wait and retry
          await new Promise(resolve => setTimeout(resolve, delay));
          retryAttempt++;
          return attemptFetch();  // Recursive retry
          
        } else {
          // Max retries reached
          this.updateConnectionStatus('offline');
          
          if (this.onError) {
            this.onError(err, true);  // Fatal error
          }
          
          return null;
        }
      }
    };
    
    return await attemptFetch();
  }

  /**
   * Update connection status and trigger callback
   * @param {string} status - 'online', 'offline', 'connected', 'reconnecting'
   */
  updateConnectionStatus(status) {
    this.isOnline = (status === 'online' || status === 'connected');
    
    if (this.onConnectionChange) {
      this.onConnectionChange(status);
    }
  }

  /**
   * Start polling for game state updates
   */
  start() {
    if (this.isPolling) {
      console.warn('[ApiClient] Already polling');
      return;
    }
    
    this.isPolling = true;
    
    // Immediate first fetch
    this.fetch();
    
    // Start polling interval
    this.pollingIntervalId = setInterval(() => {
      this.fetch();
    }, this.pollingInterval);
    
    console.log(`[ApiClient] Started polling every ${this.pollingInterval}ms`);
  }

  /**
   * Stop polling
   */
  stop() {
    if (!this.isPolling) {
      console.warn('[ApiClient] Not currently polling');
      return;
    }
    
    this.isPolling = false;
    
    if (this.pollingIntervalId) {
      clearInterval(this.pollingIntervalId);
      this.pollingIntervalId = null;
    }
    
    console.log('[ApiClient] Stopped polling');
  }

  /**
   * Get last update timestamp
   * @returns {Date|null} Last update time
   */
  getLastUpdateTime() {
    return this.lastUpdateTime;
  }

  /**
   * Get formatted last update time
   * @returns {string} Formatted time string
   */
  getLastUpdateTimeString() {
    if (!this.lastUpdateTime) {
      return 'Never';
    }
    
    return this.lastUpdateTime.toLocaleTimeString('en-US', { 
      hour: '2-digit', 
      minute: '2-digit', 
      second: '2-digit' 
    });
  }

  /**
   * Check if currently polling
   * @returns {boolean} True if polling is active
   */
  isActive() {
    return this.isPolling;
  }

  /**
   * Force a manual fetch (outside of polling cycle)
   * @returns {Promise<Object>} Game state
   */
  async refresh() {
    console.log('[ApiClient] Manual refresh triggered');
    return await this.fetch();
  }

  /**
   * Update polling interval (stops and restarts polling if active)
   * @param {number} intervalMs - New polling interval in milliseconds
   */
  setPollingInterval(intervalMs) {
    const wasPolling = this.isPolling;
    
    if (wasPolling) {
      this.stop();
    }
    
    this.pollingInterval = intervalMs;
    console.log(`[ApiClient] Polling interval updated to ${intervalMs}ms`);
    
    if (wasPolling) {
      this.start();
    }
  }
}
