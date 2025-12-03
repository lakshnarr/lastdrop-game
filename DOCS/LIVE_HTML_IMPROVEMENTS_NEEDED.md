# Live.html - Critical Improvements Needed

## Priority 1: Connection Resilience

### 1.1 Add Automatic API Retry with Exponential Backoff

**Problem**: Single failed API call shows "OFFLINE" with no retry (line 2246).

**Current State**:
```javascript
.catch(err => {
  console.error("Live state error:", err);
  connStatus.textContent = "OFFLINE";
  connStatus.className = "badge-red";
});
```

**Fix**: Replace with retry logic:
```javascript
let retryCount = 0;
const MAX_RETRIES = 5;
const BASE_DELAY = 2000;  // 2 seconds

async function fetchWithRetry(url, options = {}, attempt = 0) {
  try {
    const response = await fetch(url, options);
    
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }
    
    // Reset retry counter on success
    retryCount = 0;
    updateConnectionStatus("CONNECTED", true);
    
    return response;
    
  } catch (error) {
    retryCount++;
    
    if (retryCount >= MAX_RETRIES) {
      console.error("Max retries reached:", error);
      updateConnectionStatus("OFFLINE", false);
      
      // Show reconnection UI
      showReconnectionOverlay();
      throw error;
    }
    
    // Exponential backoff: 2s, 4s, 8s, 16s, 32s
    const delay = BASE_DELAY * Math.pow(2, attempt);
    console.warn(`Retry ${retryCount}/${MAX_RETRIES} in ${delay/1000}s...`);
    
    updateConnectionStatus(`RETRYING (${retryCount}/${MAX_RETRIES})`, false);
    
    await new Promise(resolve => setTimeout(resolve, delay));
    return fetchWithRetry(url, options, attempt + 1);
  }
}

function updateConnectionStatus(text, isConnected) {
  const connStatus = document.getElementById("connStatus");
  connStatus.textContent = text;
  connStatus.className = isConnected ? "badge-green" : "badge-red";
}

function showReconnectionOverlay() {
  // Create overlay if not exists
  let overlay = document.getElementById("reconnectionOverlay");
  if (!overlay) {
    overlay = document.createElement("div");
    overlay.id = "reconnectionOverlay";
    overlay.style.cssText = `
      position: fixed;
      inset: 0;
      background: rgba(5, 7, 17, 0.95);
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      z-index: 10000;
      color: #e5e7eb;
    `;
    
    overlay.innerHTML = `
      <div style="text-align: center; max-width: 500px; padding: 40px;">
        <h2 style="color: #f97373; margin-bottom: 20px;">⚠️ Connection Lost</h2>
        <p style="margin-bottom: 30px; color: #9ca3af;">
          Unable to reach server. Please check:
        </p>
        <ul style="text-align: left; margin-bottom: 30px; line-height: 2;">
          <li>Internet connection active</li>
          <li>Server is online (lastdrop.earth)</li>
          <li>Android app is running and sending updates</li>
        </ul>
        <button id="btnManualRetry" style="
          padding: 12px 24px;
          background: #40e0d0;
          border: none;
          border-radius: 8px;
          color: #050711;
          font-weight: bold;
          cursor: pointer;
        ">🔄 Retry Now</button>
      </div>
    `;
    
    document.body.appendChild(overlay);
    
    // Manual retry button
    document.getElementById("btnManualRetry").onclick = () => {
      retryCount = 0;
      overlay.remove();
      fetchLiveState();  // Immediate retry
    };
  }
  
  overlay.style.display = "flex";
}

// Modify fetchLiveState() to use retry logic
async function fetchLiveState() {
  try {
    const response = await fetchWithRetry(
      "https://lastdrop.earth/api/live_state.php?format=json",
      { method: "GET" }
    );
    
    const data = await response.json();
    updateGameState(data);
    
  } catch (error) {
    console.error("Failed to fetch live state:", error);
  }
}
```

---

### 1.2 Add Polling Health Check

**Problem**: Page polls every 2 seconds but doesn't detect network issues until API call fails.

**Fix**: Add network connectivity check:
```javascript
let isOnline = navigator.onLine;

// Listen for browser online/offline events
window.addEventListener('online', () => {
  console.log("✅ Browser back online");
  isOnline = true;
  updateConnectionStatus("RECONNECTING...", false);
  
  // Remove overlay if showing
  const overlay = document.getElementById("reconnectionOverlay");
  if (overlay) overlay.remove();
  
  // Immediate state fetch
  fetchLiveState();
});

window.addEventListener('offline', () => {
  console.warn("⚠️ Browser went offline");
  isOnline = false;
  updateConnectionStatus("NO INTERNET", false);
  showReconnectionOverlay();
});

// Modify polling to skip if offline
function startPolling() {
  setInterval(() => {
    if (isOnline) {
      fetchLiveState();
    } else {
      console.warn("Skipping poll - browser offline");
    }
  }, 2000);
}
```

---

### 1.3 Add WebSocket Support (Future Enhancement)

**Problem**: Polling every 2s wastes bandwidth and has latency.

**Better Solution**: WebSocket for real-time updates.

**Implementation** (requires server-side WebSocket endpoint):
```javascript
let ws = null;
let wsReconnectTimer = null;

function connectWebSocket() {
  // Close existing connection
  if (ws) ws.close();
  
  ws = new WebSocket("wss://lastdrop.earth/ws/live");
  
  ws.onopen = () => {
    console.log("✅ WebSocket connected");
    updateConnectionStatus("CONNECTED (LIVE)", true);
    clearTimeout(wsReconnectTimer);
  };
  
  ws.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data);
      updateGameState(data);
    } catch (err) {
      console.error("WebSocket message parse error:", err);
    }
  };
  
  ws.onerror = (error) => {
    console.error("WebSocket error:", error);
    updateConnectionStatus("ERROR", false);
  };
  
  ws.onclose = () => {
    console.warn("WebSocket closed. Reconnecting in 5s...");
    updateConnectionStatus("RECONNECTING...", false);
    
    // Auto-reconnect after 5 seconds
    wsReconnectTimer = setTimeout(connectWebSocket, 5000);
  };
}

// Fallback to polling if WebSocket not available
function initConnection() {
  if (typeof WebSocket !== 'undefined') {
    connectWebSocket();
  } else {
    console.warn("WebSocket not supported, using polling");
    startPolling();
  }
}
```

**Note**: Requires backend implementation at `wss://lastdrop.earth/ws/live` (not currently available).

---

## Priority 2: User Experience

### 2.1 Add Loading States

**Problem**: No visual feedback during initial page load or data fetch.

**Fix**:
```html
<!-- Add to HTML near top of body -->
<div id="loadingOverlay" style="
  position: fixed;
  inset: 0;
  background: radial-gradient(circle at top, #111827 0, #050711 55%);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  z-index: 9999;
">
  <div class="spinner"></div>
  <p style="margin-top: 20px; color: #9ca3af;">Loading game state...</p>
</div>

<style>
.spinner {
  width: 50px;
  height: 50px;
  border: 4px solid rgba(64, 224, 208, 0.2);
  border-top-color: #40e0d0;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
```

```javascript
// Remove loading overlay after first successful fetch
function hideLoadingOverlay() {
  const overlay = document.getElementById("loadingOverlay");
  if (overlay) {
    overlay.style.opacity = "0";
    overlay.style.transition = "opacity 0.5s";
    setTimeout(() => overlay.remove(), 500);
  }
}

// Call in fetchLiveState() after successful data update
async function fetchLiveState() {
  try {
    const response = await fetchWithRetry(...);
    const data = await response.json();
    updateGameState(data);
    
    hideLoadingOverlay();  // ✅ Hide on first success
    
  } catch (error) {
    // Keep overlay if initial load fails
    console.error("Failed to fetch live state:", error);
  }
}
```

---

### 2.2 Add Last Updated Timestamp

**Problem**: Users don't know when data was last refreshed.

**Fix**:
```html
<!-- Add to header section -->
<div class="subtitle">
  Live Game Display • Last updated: <span id="lastUpdated">Never</span>
</div>
```

```javascript
function updateLastUpdatedTime() {
  const now = new Date();
  const time = now.toLocaleTimeString('en-US', { 
    hour: '2-digit', 
    minute: '2-digit', 
    second: '2-digit' 
  });
  
  document.getElementById("lastUpdated").textContent = time;
}

// Call in updateGameState()
function updateGameState(data) {
  // ... existing code
  
  updateLastUpdatedTime();  // ✅ Show refresh time
}
```

---

### 2.3 Add Player Elimination Visual

**Problem**: Eliminated players still show on board (just greyed out).

**Enhancement**: Add skull icon and cross-out effect:
```css
.player-token.eliminated {
  opacity: 0.3;
  filter: grayscale(100%) blur(2px);
  position: relative;
}

.player-token.eliminated::after {
  content: "💀";
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 30px;
  filter: none;
}
```

```javascript
function updatePlayerToken(playerKey, data) {
  const token = document.querySelector(`.player-token[data-player="${playerKey}"]`);
  if (!token) return;
  
  // Check if player eliminated
  if (data.score <= 0 || !data.alive) {
    token.classList.add("eliminated");
  } else {
    token.classList.remove("eliminated");
  }
  
  // ... rest of existing code
}
```

---

### 2.4 Add Winner Celebration

**Problem**: No special visual for winner.

**Fix**:
```javascript
function checkForWinner(players) {
  const alivePlayers = Object.values(players).filter(p => p.alive && p.score > 0);
  
  if (alivePlayers.length === 1) {
    const winner = alivePlayers[0];
    showWinnerCelebration(winner);
  }
}

function showWinnerCelebration(winner) {
  // Create confetti overlay
  const overlay = document.createElement("div");
  overlay.id = "winnerOverlay";
  overlay.style.cssText = `
    position: fixed;
    inset: 0;
    background: radial-gradient(circle, rgba(64, 224, 208, 0.2), transparent);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 9998;
    animation: pulseGlow 2s infinite;
  `;
  
  overlay.innerHTML = `
    <div style="
      background: linear-gradient(135deg, #111827, #0b1020);
      border: 3px solid #40e0d0;
      border-radius: 20px;
      padding: 60px;
      text-align: center;
      box-shadow: 0 0 50px rgba(64, 224, 208, 0.5);
    ">
      <h1 style="
        font-size: 3rem;
        color: #40e0d0;
        margin-bottom: 20px;
        text-shadow: 0 0 20px rgba(64, 224, 208, 0.8);
      ">🏆 WINNER! 🏆</h1>
      
      <p style="font-size: 1.5rem; color: #e5e7eb; margin-bottom: 10px;">
        ${winner.name}
      </p>
      
      <p style="font-size: 1rem; color: #9ca3af;">
        Final Score: ${winner.score} drops
      </p>
      
      <button onclick="this.parentElement.parentElement.remove()" style="
        margin-top: 30px;
        padding: 12px 30px;
        background: #40e0d0;
        border: none;
        border-radius: 8px;
        color: #050711;
        font-weight: bold;
        cursor: pointer;
        font-size: 1rem;
      ">Close</button>
    </div>
  `;
  
  document.body.appendChild(overlay);
  
  // Add confetti animation
  createConfetti();
}

function createConfetti() {
  const colors = ['#40e0d0', '#4ade80', '#f97373', '#fbbf24'];
  const confettiCount = 50;
  
  for (let i = 0; i < confettiCount; i++) {
    const confetti = document.createElement('div');
    confetti.style.cssText = `
      position: fixed;
      width: 10px;
      height: 10px;
      background: ${colors[Math.floor(Math.random() * colors.length)]};
      top: -10px;
      left: ${Math.random() * 100}vw;
      opacity: ${Math.random()};
      animation: fall ${2 + Math.random() * 3}s linear infinite;
      animation-delay: ${Math.random() * 2}s;
    `;
    document.body.appendChild(confetti);
    
    // Remove after animation
    setTimeout(() => confetti.remove(), 5000);
  }
}

// Add CSS animation
const style = document.createElement('style');
style.textContent = `
@keyframes fall {
  to {
    transform: translateY(100vh) rotate(360deg);
  }
}

@keyframes pulseGlow {
  0%, 100% { opacity: 0.6; }
  50% { opacity: 1; }
}
`;
document.head.appendChild(style);

// Call in updateGameState()
function updateGameState(data) {
  // ... existing code
  
  checkForWinner(data.players);  // ✅ Check for winner
}
```

---

## Priority 3: Performance

### 3.1 Debounce Rapid State Updates

**Problem**: If Android app sends updates faster than 2s polling, animations might conflict.

**Fix**:
```javascript
let updateQueue = [];
let isProcessingUpdate = false;

async function queueUpdate(data) {
  updateQueue.push(data);
  processNextUpdate();
}

async function processNextUpdate() {
  if (isProcessingUpdate || updateQueue.length === 0) return;
  
  isProcessingUpdate = true;
  const data = updateQueue.shift();
  
  await updateGameState(data);
  
  // Wait for animations to complete before next update
  await new Promise(resolve => setTimeout(resolve, 500));
  
  isProcessingUpdate = false;
  processNextUpdate();  // Process next queued update
}
```

---

### 3.2 Optimize Animation Performance

**Problem**: Complex animations might lag on slower devices.

**Fix**: Use CSS transforms instead of position changes:
```css
.player-token {
  /* Replace transition on all properties */
  transition: transform 0.4s ease-out, opacity 0.4s;
  will-change: transform;  /* GPU acceleration hint */
}

/* Use transform instead of left/top */
.player-token[data-position="5"] {
  transform: translate(var(--tile-5-x), var(--tile-5-y));
}
```

```javascript
// Precompute tile positions as CSS variables
function computeTilePositions() {
  const root = document.documentElement;
  
  tilePositions.forEach((pos, tile) => {
    root.style.setProperty(`--tile-${tile}-x`, `${pos.x}px`);
    root.style.setProperty(`--tile-${tile}-y`, `${pos.y}px`);
  });
}

// Call on page load
window.addEventListener('DOMContentLoaded', computeTilePositions);
```

---

## Priority 4: Accessibility & Usability

### 4.1 Add Keyboard Controls

**Problem**: No keyboard navigation (accessibility issue).

**Fix**:
```javascript
document.addEventListener('keydown', (e) => {
  switch(e.key) {
    case 'r':
    case 'R':
      // Refresh state
      fetchLiveState();
      break;
      
    case 'f':
    case 'F':
      // Toggle fullscreen
      if (!document.fullscreenElement) {
        document.documentElement.requestFullscreen();
      } else {
        document.exitFullscreen();
      }
      break;
      
    case 'Escape':
      // Close any overlays
      document.querySelectorAll('#winnerOverlay, #reconnectionOverlay').forEach(el => {
        el.remove();
      });
      break;
  }
});
```

---

### 4.2 Add Settings Panel

**Problem**: No way to configure polling interval or other preferences.

**Fix**:
```html
<!-- Add settings button to header -->
<button id="btnSettings" style="
  position: absolute;
  top: 12px;
  right: 20px;
  background: transparent;
  border: 1px solid #40e0d0;
  color: #40e0d0;
  padding: 8px 16px;
  border-radius: 6px;
  cursor: pointer;
">⚙️ Settings</button>

<!-- Settings modal -->
<div id="settingsModal" style="display: none; position: fixed; inset: 0; background: rgba(0,0,0,0.8); z-index: 10001; align-items: center; justify-content: center;">
  <div style="background: #0b1020; padding: 40px; border-radius: 12px; max-width: 400px;">
    <h3 style="color: #40e0d0; margin-bottom: 20px;">Display Settings</h3>
    
    <label style="display: block; margin-bottom: 10px; color: #e5e7eb;">
      Polling Interval (seconds):
      <input id="inputPollingInterval" type="number" min="1" max="10" value="2" style="
        display: block;
        width: 100%;
        padding: 8px;
        margin-top: 5px;
        background: #111827;
        border: 1px solid #1f2937;
        color: #e5e7eb;
        border-radius: 4px;
      ">
    </label>
    
    <label style="display: block; margin-bottom: 20px; color: #e5e7eb;">
      <input id="checkboxAnimations" type="checkbox" checked style="margin-right: 8px;">
      Enable Animations
    </label>
    
    <div style="display: flex; gap: 10px; justify-content: flex-end;">
      <button id="btnCancelSettings" style="padding: 10px 20px; background: #374151; border: none; color: #e5e7eb; border-radius: 6px; cursor: pointer;">Cancel</button>
      <button id="btnSaveSettings" style="padding: 10px 20px; background: #40e0d0; border: none; color: #050711; font-weight: bold; border-radius: 6px; cursor: pointer;">Save</button>
    </div>
  </div>
</div>
```

```javascript
// Settings handling
document.getElementById('btnSettings').onclick = () => {
  document.getElementById('settingsModal').style.display = 'flex';
};

document.getElementById('btnCancelSettings').onclick = () => {
  document.getElementById('settingsModal').style.display = 'none';
};

document.getElementById('btnSaveSettings').onclick = () => {
  const interval = parseInt(document.getElementById('inputPollingInterval').value);
  const animationsEnabled = document.getElementById('checkboxAnimations').checked;
  
  // Apply settings
  localStorage.setItem('pollingInterval', interval);
  localStorage.setItem('animationsEnabled', animationsEnabled);
  
  // Restart polling with new interval
  clearInterval(pollingTimer);
  pollingTimer = setInterval(fetchLiveState, interval * 1000);
  
  document.getElementById('settingsModal').style.display = 'none';
  
  console.log(`Settings saved: ${interval}s polling, animations ${animationsEnabled ? 'ON' : 'OFF'}`);
};

// Load saved settings on page load
function loadSettings() {
  const interval = parseInt(localStorage.getItem('pollingInterval')) || 2;
  const animationsEnabled = localStorage.getItem('animationsEnabled') !== 'false';
  
  document.getElementById('inputPollingInterval').value = interval;
  document.getElementById('checkboxAnimations').checked = animationsEnabled;
  
  if (!animationsEnabled) {
    document.body.classList.add('no-animations');
  }
}

window.addEventListener('DOMContentLoaded', loadSettings);
```

---

## Summary Table

| Priority | Issue | Impact | Difficulty | Estimated Lines |
|----------|-------|--------|------------|-----------------|
| 🔴 P1 | Retry with backoff | Connection drops | Medium | ~80 |
| 🔴 P1 | Network health check | Offline detection | Easy | ~30 |
| 🟡 P2 | Loading states | UX confusion | Easy | ~40 |
| 🟡 P2 | Last updated time | Data freshness | Easy | ~15 |
| 🟡 P2 | Elimination visual | Gameplay clarity | Easy | ~25 |
| 🟡 P2 | Winner celebration | Game polish | Medium | ~100 |
| 🟢 P3 | Update queue | Animation conflicts | Medium | ~30 |
| 🟢 P3 | Animation optimization | Performance | Medium | ~40 |
| 🟢 P4 | Keyboard controls | Accessibility | Easy | ~30 |
| 🟢 P4 | Settings panel | Customization | Medium | ~80 |

**Total Estimated Changes**: ~470 lines

**Recommended Implementation Order**:
1. ✅ Retry with exponential backoff (reliability critical)
2. ✅ Loading states (immediate UX improvement)
3. ✅ Last updated timestamp (data transparency)
4. ✅ Winner celebration (game polish)
5. ⏭️ Settings panel (user control)
6. ⏭️ Remaining features as needed

**Note**: WebSocket support requires backend changes and should be considered for future iteration (not immediate priority).
