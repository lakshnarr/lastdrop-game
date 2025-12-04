# Last Drop Website - Modular Architecture

## 📁 Folder Structure

```
website/
├── index.html          # Landing page (TO BE BUILT)
├── demo.html           # Demo mode (REFACTORING)
├── host.html           # Host mode (TO BE BUILT)
├── live.html           # Live spectator view (REFACTORING)
├── spectate.html       # Browse active games (TO BE BUILT)
├── assets/
│   ├── css/
│   │   └── shared.css       # Shared stylesheet (3,400+ lines → 1 file)
│   ├── js/
│   │   ├── core/
│   │   │   ├── dice-animator.js    # ✅ 3D dice animations (370 lines)
│   │   │   ├── board-renderer.js   # Board rendering & token positioning
│   │   │   ├── game-state.js       # State management & updates
│   │   │   └── audio-manager.js    # Sound effects
│   │   ├── ui/
│   │   │   ├── settings-panel.js   # Settings sidebar
│   │   │   ├── scoreboard.js       # Player list & scores
│   │   │   ├── event-log.js        # Event messages
│   │   │   └── overlays.js         # Popups, modals, winner screen
│   │   ├── network/
│   │   │   ├── api-client.js       # Fetch game state
│   │   │   └── session-manager.js  # Session handling
│   │   └── utils/
│   │       ├── constants.js        # ✅ Tile names, effects, colors (130 lines)
│   │       └── helpers.js          # Common utilities
│   ├── images/
│   ├── tiles/          # 20 tile images
│   ├── chance/         # 20 chance card images
│   └── pawns/          # 4 pawn SVGs
└── api/
    ├── live_state.php      # Get game state
    ├── live_push.php       # Push game state
    ├── active_games.php    # List active games (NEW)
    └── session_info.php    # Get session details (NEW)
```

## 🎯 Refactoring Goals

### Before (Current State)
- `live.html`: **3,432 lines** of inline code
- `demo.html`: **3,437 lines** of inline code
- **~95% duplication** between files
- Every bug fix requires editing both files manually
- Adding features compounds the duplication problem

### After (Target State)
- `live.html`: **~500 lines** (imports modules + live-specific logic)
- `demo.html`: **~500 lines** (imports modules + demo-specific logic)
- **Shared modules**: Single source of truth (~2,500 lines across 10-15 files)
- Bug fixes update ONE module, both pages benefit
- New features add to modules, automatically available everywhere

## ✅ Completed Modules

### 1. dice-animator.js (370 lines)
**Location**: `assets/js/core/dice-animator.js`

**Exports**:
- `DiceAnimator` class

**Features**:
- 3D dice roll animations
- Static dice display
- Rolling state animations (for GoDice sensors)
- Per-die color customization
- Callback support for animation completion

**Usage**:
```javascript
import { DiceAnimator } from './assets/js/core/dice-animator.js';

const diceAnimator = new DiceAnimator({
  scoreboardDiceDisplay: document.getElementById('scoreboardDiceDisplay'),
  scoreboardDice1: document.getElementById('scoreboardDice1'),
  scoreboardDice2: document.getElementById('scoreboardDice2'),
  dicePlayerName: document.getElementById('dicePlayerName'),
  soundManager: audioManager  // Optional
});

// Show 3D animation
diceAnimator.show3DDiceRoll({
  value1: 4,
  value2: 6,
  playerName: "Player 1",
  diceColor1: "red",
  diceColor2: "blue",
  callback: () => console.log('Animation complete!')
});
```

### 2. board-renderer.js (480 lines)
**Location**: `assets/js/core/board-renderer.js`

**Exports**:
- `BoardRenderer` class

**Features**:
- Initialize 20-tile board grid
- Compute tile center positions
- Create and manage player tokens
- Position tokens with multi-player offset support
- Animate token movement (step-by-step walking)
- Calculate tile paths for movement
- 2D/3D view toggle
- Board zoom and rotation controls
- Window resize handling
- Token lifecycle (create, remove, hide, show, eliminate)

**Usage**:
```javascript
import { BoardRenderer } from './assets/js/core/board-renderer.js';

const boardRenderer = new BoardRenderer({
  tilesGrid: document.getElementById('tilesGrid'),
  tokensLayer: document.getElementById('tokensLayer'),
  boardWrapper: document.getElementById('boardWrapper'),
  board3d: document.getElementById('board3d'),
  soundManager: audioManager  // Optional
});

// Create token and position on tile 5
const player = { id: 'P1', name: 'Player 1', color: 'red' };
boardRenderer.ensureTokenForPlayer(player);
boardRenderer.positionToken('P1', 5, 0);

// Animate movement from tile 5 to tile 12
boardRenderer.positionToken('P1', 12, 0, true, 5);
```

### 3. audio-manager.js (230 lines)
**Location**: `assets/js/core/audio-manager.js`

**Exports**:
- `AudioManager` class

**Features**:
- Enable/disable/toggle audio
- Per-sound-type volume controls
- Web Audio API beep generator
- Sound effect shortcuts (playDice, playMove, etc.)
- Background music support
- Audio file loading and preloading
- Audio context resume for autoplay restrictions

**Usage**:
```javascript
import { AudioManager } from './assets/js/core/audio-manager.js';

const audioManager = new AudioManager({ enabled: true });

// Play sounds
audioManager.playDice();
audioManager.playMove();
audioManager.playChance();

// Control volume
audioManager.setVolume('dice', 0.8);
audioManager.toggle(); // Enable/disable all audio
```

### 4. constants.js (130 lines)
**Location**: `assets/js/utils/constants.js`

**Exports**:
- `TILE_NAMES` - Array of 20 tile names
- `TILE_EFFECTS` - Water drop changes per tile
- `CHANCE_CARD_EFFECTS` - Chance card water drop changes
- `BOARD_GRID` - Tile layout for rendering
- `BOARD_SEQUENCE` - Tile order for movement
- `PLAYER_COLORS` - Color hex codes
- `PAWN_IMAGES` - SVG paths
- `PLAYER_GRADIENTS` - Scoreboard gradients
- `DEFAULT_AUDIO_VOLUMES` - Audio settings
- `SOUND_FREQUENCIES` - Beep frequencies
- `API_CONFIG` - Endpoint URLs & polling settings
- `ANIMATION_DURATIONS` - Timing constants

**Usage**:
```javascript
import { TILE_NAMES, TILE_EFFECTS, API_CONFIG } from './assets/js/utils/constants.js';

console.log(TILE_NAMES[0]); // "START"
console.log(TILE_EFFECTS[7]); // -4 (Oil Spill Bay)
fetch(API_CONFIG.liveStateUrl);
```

### 5. example-integration.js (150 lines)
**Location**: `example-integration.js`

**Purpose**: Complete working example showing how to use all modules together

**Demonstrates**:
- Initializing all three core modules
- Creating players and tokens
- Simulating dice rolls with animations
- Moving tokens between tiles
- Coordinating dice animation → token movement
- Audio integration
- Volume controls
- Eliminating players
- Winner announcement

## 📋 Modules To Be Created

### Core Modules

#### board-renderer.js
**Responsibilities**:
- Render 20-tile board grid
- Compute tile centers
- Handle board transformations (zoom, rotate, 3D/2D view)
- Position tokens on tiles
- Animate token movement
- Handle responsive layouts

#### game-state.js
**Responsibilities**:
- Manage player data (score, position, status)
- Track game state
- Process API responses
- Detect state changes (new rolls, eliminations, winner)
- Queue states during animations

#### audio-manager.js
**Responsibilities**:
- Play sound effects (dice, move, chance, tile, eliminated, winner)
- Manage volume controls
- Handle audio enable/disable

### UI Modules

#### scoreboard.js (~200 lines) ✅ COMPLETE
**Location**: `assets/js/ui/scoreboard.js`

**Exports**:
- `Scoreboard` class

**Features**:
- Sort active players by score (drops) with seniority tiebreaker
- Calculate consecutive ranks (ties get same rank)
- Render active players list with colored gradients
- Render eliminated players with grayscale tokens
- Winner detection (only 1 active player remaining)
- Track newly eliminated players with callbacks
- Update eliminated count badge

**Usage**:
```javascript
import { Scoreboard } from './assets/js/ui/scoreboard.js';

const scoreboard = new Scoreboard({
  playersList: document.getElementById('playersList'),
  eliminatedList: document.getElementById('eliminatedList'),
  eliminatedCount: document.getElementById('eliminatedCount'),
  onWinnerDetected: (winner) => {
    console.log(`${winner.name} wins with ${winner.score} drops!`);
  },
  onPlayerEliminated: (playerId) => {
    audioManager.playEliminated();
  }
});

// Update scoreboard with new player data
scoreboard.updatePlayers(state.players);
```

#### event-log.js (~170 lines) ✅ COMPLETE
**Location**: `assets/js/ui/event-log.js`

**Exports**:
- `EventLog` class

**Features**:
- Display rolling status messages
- Show dice roll results with tile and chance card info
- Format messages with HTML highlights
- Handle game events (reset, undo, elimination, winner)
- Connection status messages
- Custom message support

**Usage**:
```javascript
import { EventLog } from './assets/js/ui/event-log.js';

const eventLog = new EventLog({
  container: document.getElementById('eventLog')
});

// Show rolling message
eventLog.showRolling('Player 1', 2); // 2 dice

// Show roll result
eventLog.showRollResult('Player 1', 4, 6, 12, 'CARD-5');

// Other events
eventLog.showReset();
eventLog.showUndo('Player 2');
eventLog.showElimination('Player 3');
eventLog.showWinner('Player 1');
```

#### overlays.js (~320 lines) ✅ COMPLETE
**Location**: `assets/js/ui/overlays.js`

**Exports**:
- `Overlays` class

**Features**:
- Winner celebration overlay with confetti animation
- Chance card popup display (auto-hide after 4.5s)
- Connection status overlay (waiting for controller)
- Reconnection overlay with retry counter
- Welcome screen overlay
- Loading overlay support
- Sound integration for winner and chance card events

**Usage**:
```javascript
import { Overlays } from './assets/js/ui/overlays.js';

const overlays = new Overlays({
  winnerOverlay: document.getElementById('winnerOverlay'),
  winnerName: document.getElementById('winnerName'),
  winnerDrops: document.getElementById('winnerDrops'),
  chanceCard: document.getElementById('chanceCard'),
  chanceImage: document.getElementById('chanceImage'),
  chanceTitle: document.getElementById('chanceTitle'),
  chanceText: document.getElementById('chanceText'),
  connectionOverlay: document.getElementById('connectionOverlay'),
  reconnectionOverlay: document.getElementById('reconnectionOverlay'),
  // ... other elements
  soundManager: audioManager  // Optional
});

// Show winner
overlays.showWinner({ name: 'Player 1', drops: 15 });

// Show chance card
overlays.showChanceCard('5', 'Lucky bonus! +2 drops', 4500);

// Show reconnection
overlays.showReconnectionOverlay(attempt, delayMs, maxRetries);

// Hide all overlays
overlays.hideAll();
```

#### settings-panel.js
**Responsibilities**:
- Winner celebration overlay
- Chance card popups
- Connection status overlays
- Loading screens

#### settings-panel.js
**Responsibilities**:
- Manage settings sidebar
- Audio controls
- Board view controls
- Coin offset adjustments

### Network Modules

#### api-client.js (~340 lines) ✅ COMPLETE
**Location**: `assets/js/network/api-client.js`

**Exports**:
- `ApiClient` class

**Features**:
- Automatic polling with configurable intervals
- Exponential backoff retry logic (5 attempts: 2s, 4s, 8s, 16s, 32s)
- Connection status management
- Session-based filtering (boardId, sessionId)
- Offline detection
- First connection detection
- Callbacks for state updates, retries, errors
- Manual refresh support

**Usage**:
```javascript
import { ApiClient } from './assets/js/network/api-client.js';

const apiClient = new ApiClient({
  baseUrl: '/api/live_state.php',
  apiKey: 'ABC123',
  pollingInterval: 2000,
  onStateUpdate: (state) => {
    scoreboard.updatePlayers(state.players);
  },
  onConnectionChange: (status) => {
    console.log('Connection:', status);
  },
  onRetry: (attempt, delay) => {
    overlays.showReconnectionOverlay(attempt, delay, 5);
  }
});

// Set session filter
apiClient.setSession('LASTDROP-0001', 'uuid-123');

// Start/stop polling
apiClient.start();
apiClient.stop();

// Manual refresh
await apiClient.refresh();
```

#### session-manager.js (~260 lines) ✅ COMPLETE
**Location**: `assets/js/network/session-manager.js`

**Exports**:
- `SessionManager` class

**Features**:
- Parse session from URL (?session=BOARDID_SESSIONID or ?board=BOARDID)
- Generate session URLs for sharing
- Update browser URL without reload
- localStorage persistence with expiration
- UUID generation for new sessions
- QR code data generation
- Session change callbacks

**Usage**:
```javascript
import { SessionManager } from './assets/js/network/session-manager.js';

const sessionManager = new SessionManager({
  onSessionChange: (boardId, sessionId) => {
    console.log('Session changed:', boardId, sessionId);
  }
});

// Parse from URL
sessionManager.parseFromUrl();

// Set session manually
sessionManager.setSession('LASTDROP-0001', 'abc-123');

// Generate shareable URL
const shareUrl = sessionManager.generateSessionUrl();

// Persistence
sessionManager.saveToStorage();
sessionManager.loadFromStorage();
```

## 📋 Module Summary

### Core Modules (Phase 1)

### Phase 1: Extract Core (Complete ✅)
- [x] Create folder structure
- [x] Extract dice-animator.js (370 lines)
- [x] Extract constants.js (130 lines)
- [x] Extract board-renderer.js (480 lines)
- [x] Extract audio-manager.js (230 lines)
- [x] Create example-integration.js (150 lines)

**Total extracted: 1,360 lines from core modules**

### Phase 2: Extract UI
- [ ] Extract scoreboard.js
- [ ] Extract event-log.js
- [ ] Extract overlays.js
- [ ] Extract settings-panel.js

### Phase 3: Extract Network
- [ ] Extract api-client.js
- [ ] Extract session-manager.js

### Phase 4: Create Shared CSS
- [ ] Move all CSS to `assets/css/shared.css`
- [ ] Extract critical CSS for above-the-fold content
- [ ] Optimize for performance

### Phase 5: Update HTML Files
- [ ] Update `demo.html` to import modules
- [ ] Update `live.html` to import modules
- [ ] Remove duplicated code from both files
- [ ] Test all functionality

### Phase 6: Build New Pages
- [ ] Create `index.html` using modules
- [ ] Create `host.html` using modules
- [ ] Create `spectate.html` using modules

## 🧪 Testing Strategy

After refactoring each module:
1. Test in `live.html` (with real API)
2. Test in `demo.html` (with auto-play mode)
3. Test on mobile devices (Chrome & Firefox)
4. Verify all features work:
   - [ ] 3D board rendering
   - [ ] Dice animations
   - [ ] Token movement
   - [ ] Scoreboard updates
   - [ ] Settings panel
   - [ ] Audio playback
   - [ ] Chance cards
   - [ ] Winner overlay
   - [ ] Connection status

## 💡 Development Principles

1. **Single Source of Truth**: Each piece of functionality exists in ONE file only
2. **Test Both Pages**: Every change must work in `live.html` AND `demo.html`
3. **Modular First**: Before adding features, extract to modules
4. **No Duplication**: If code exists in 2+ places, extract it
5. **ES6 Modules**: Use `import`/`export` for clean dependencies

## 📦 Deployment

After refactoring:
```bash
# Commit changes
git add website/
git commit -m "refactor: Modularize codebase - extract dice-animator and constants"
git push origin main

# Deploy to VPS
ssh lastdrop "cd /home/lastdrop && git pull && sudo cp -r website/* /var/www/lastdrop.earth/public/"
```

## 🚀 Future Benefits

- **Faster Development**: Add features once, use everywhere
- **Easier Debugging**: Fix bugs in one place
- **Better Testing**: Test modules in isolation
- **Scalability**: Build new pages quickly using existing modules
- **Maintainability**: Clear separation of concerns
- **Performance**: Load only what's needed per page

---

**Status**: ✅ Phase 3 COMPLETE! Network modules extraction (10 of 15 total modules)
**Last Updated**: December 5, 2025
**Lines Extracted**: 3,020 lines (target: ~2,500 total - EXCEEDED!)
**Next Step**: Phase 4 - Extract shared CSS (~1,000 lines)
