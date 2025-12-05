
    // Parse URL parameters for session-specific viewing
    const urlParams = new URLSearchParams(window.location.search);
    const sessionParam = urlParams.get('session');  // Format: "LASTDROP-0001_uuid"
    const boardIdParam = urlParams.get('board');     // Alternative: just board ID
    
    // Extract boardId and sessionId from URL
    let activeBoardId = null;
    let activeSessionId = null;
    
    if (sessionParam) {
      const parts = sessionParam.split('_');
      activeBoardId = parts[0];
      activeSessionId = parts.length > 1 ? parts[1] : null;
    } else if (boardIdParam) {
      activeBoardId = boardIdParam;
    }
    
    // Build API URL with session parameters
    let LIVE_STATE_URL = "/api/live_state.php?key=ABC123";
    if (activeBoardId) {
      LIVE_STATE_URL += `&boardId=${encodeURIComponent(activeBoardId)}`;
    }
    if (activeSessionId) {
      LIVE_STATE_URL += `&sessionId=${encodeURIComponent(activeSessionId)}`;
    }
    
    console.log('[live] Session parameters:', { boardId: activeBoardId, sessionId: activeSessionId });
    console.log('[live] API URL:', LIVE_STATE_URL);
    
    // Initialize Replay Recorder
    const replayRecorder = new ReplayRecorder();
    let gameStarted = false;
    let previousPlayerPositions = new Map();
    let lastWinner = null;
    
    const tileNames = ["START","Tile 2","Tile 3","Tile 4","Tile 5","Tile 6","Tile 7","Tile 8","Tile 9","Tile 10","Tile 11","Tile 12","Tile 13","Tile 14","Tile 15","Tile 16","Tile 17","Tile 18","Tile 19","Tile 20"];
    let demoMode = false;

    // Audio System
    const audioEnabled = { value: true };
    const audioVolumes = {
      bgMusic: 0.3,
      dice: 0.5,
      move: 0.4,
      chance: 0.5,
      tile: 0.4,
      eliminated: 0.6,
      winner: 0.7
    };

    // Audio players (using Web Audio API compatible approach)
    const audioPlayers = {};
    
    function playSound(type) {
      if (!audioEnabled.value) return;
      
      // For now, we'll use a simple beep generator as placeholder
      // In production, you'd load actual audio files
      const ctx = new (window.AudioContext || window.webkitAudioContext)();
      const oscillator = ctx.createOscillator();
      const gainNode = ctx.createGain();
      
      oscillator.connect(gainNode);
      gainNode.connect(ctx.destination);
      
      // Different frequencies for different sounds
      const frequencies = {
        dice: 440,
        move: 523,
        chance: 659,
        tile: 392,
        eliminated: 293,
        winner: 880
      };
      
      oscillator.frequency.value = frequencies[type] || 440;
      oscillator.type = 'sine';
      
      const volume = audioVolumes[type] || 0.5;
      gainNode.gain.setValueAtTime(volume, ctx.currentTime);
      gainNode.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.2);
      
      oscillator.start(ctx.currentTime);
      oscillator.stop(ctx.currentTime + 0.2);
    }

    // Background music loop
    let bgMusicInterval;
    function startBackgroundMusic() {
      if (bgMusicInterval) return;
      if (!audioEnabled.value) return;
      // Placeholder - in production, use actual audio file
      // bgMusicInterval = setInterval(() => {}, 30000);
    }

    function stopBackgroundMusic() {
      if (bgMusicInterval) {
        clearInterval(bgMusicInterval);
        bgMusicInterval = null;
      }
    }

    const boardWrapper = document.getElementById("boardWrapper");
    const board3d = document.getElementById("board3d");
    const tokensLayer = document.getElementById("tokensLayer");
    const tilesGrid = document.getElementById("tilesGrid");
    const zoomRange = document.getElementById("zoomRange");
    const rotateRange = document.getElementById("rotateRange");
    const rotateXRange = document.getElementById("rotateXRange");
    const rotateYRange = document.getElementById("rotateYRange");
    const playersList = document.getElementById("playersList");
    const eliminatedList = document.getElementById("eliminatedList");
    const eliminatedCount = document.getElementById("eliminatedCount");
    const winnerOverlay = document.getElementById("winnerOverlay");
    const winnerName = document.getElementById("winnerName");
    const winnerDrops = document.getElementById("winnerDrops");
    const sessionInfoEl = document.getElementById("sessionInfo");
    
    // Display session info in header if present
    if (activeBoardId || activeSessionId) {
      let sessionText = "Viewing: ";
      if (activeBoardId) {
        sessionText += `Board ${activeBoardId}`;
      }
      if (activeSessionId) {
        const shortSession = activeSessionId.substring(0, 8);
        sessionText += ` (Session: ${shortSession}...)`;
      }
      sessionInfoEl.textContent = sessionText;
      console.log('[live] Session info displayed:', sessionText);
    }
    
    // Track previously eliminated players to detect new eliminations
    let previouslyEliminatedIds = new Set();
    
    // Track last event to detect new dice rolls
    let lastProcessedEvent = null;
    let currentDiceColor = "#60a5fa"; // Default blue, updated from API
    // Guard to prevent token moves while dice animation plays
    let isDiceAnimationPlaying = false;
    // Store the latest polled state while animation is playing (only keep newest)
    let queuedLiveState = null;
    // Timer to delay token movement after receiving dice roll from API
    let tokenMoveTimer = null;
    
    const eventLog = document.getElementById("eventLog");
    const connStatus = document.getElementById("connStatus");
    const modeStatus = document.getElementById("modeStatus");
    const demoToggle = document.getElementById("demoToggle");
    const viewToggle = document.getElementById("viewToggle");
    const offsetXRange = document.getElementById("offsetXRange");
    const offsetYRange = document.getElementById("offsetYRange");
    const viewStatus = document.getElementById("viewStatus");
    let is3DView = false;
    const demoStatus = document.getElementById("demoStatus");
    const chanceCard = document.getElementById("chanceCard");
    const chanceImage = document.getElementById("chanceImage");
    const chanceTitle = document.getElementById("chanceTitle");
    const chanceText = document.getElementById("chanceText");

    // Connection popup elements - moved inside load event to ensure DOM is ready
    let connectionOverlay, connectionIndicator, connectionText, skipConnection;
    let reconnectionOverlay, reconnectionText, retryAttemptEl, retryDelayEl, reconnectionInfo;
    let loadingOverlay;
    let firstFetchComplete = false;
    let lastUpdatedEl;

    // Dice elements
    let diceDisplay, dice1El, dice2El;
    let scoreboardDiceDisplay, scoreboardDice1, scoreboardDice2, dicePlayerName;

    const tileCenters = {};
    const tokensByPlayerId = {};
    let lastChanceTimeout = null;
    let connectionEstablished = false;

    function initTiles() {
      const grid = [1,2,3,4,5,6, 20,0,0,0,0,7, 19,0,0,0,0,8, 18,0,0,0,0,9, 17,0,0,0,0,10, 16,15,14,13,12,11];
      grid.forEach((tileNum,idx) => {
        const tile = document.createElement("div");
        if (tileNum === 0) {
          tile.className = "tile empty";
        } else {
          tile.className = "tile";
          tile.dataset.tile = tileNum;
          const inner = document.createElement("div");
          inner.className = "tile-inner";
          inner.style.backgroundImage = `url('/assets/tiles/tile-${tileNum}.jpg')`;
          const label = document.createElement("div");
          label.className = "tile-label";
          label.textContent = tileNames[tileNum-1] || ("Tile "+tileNum);
          inner.appendChild(label);
          tile.appendChild(inner);
        }
        tilesGrid.appendChild(tile);
      });
    }

    function computeTileCenters() {
      const tokensLayerRect = tokensLayer.getBoundingClientRect();
      tilesGrid.querySelectorAll(".tile[data-tile]").forEach(tile => {
        const rect = tile.getBoundingClientRect();
        const tileIndex = tile.dataset.tile;
        tileCenters[tileIndex] = {
          x: rect.left - tokensLayerRect.left + rect.width / 2,
          y: rect.top - tokensLayerRect.top + rect.height / 2
        };
      });
    }

    window.addEventListener("resize", () => {
      computeTileCenters();
      positionExistingTokens();
    });

    function updateBoardTransform() {
      const zoom = parseInt(zoomRange.value, 10) / 100;
      if (!is3DView) {
        boardWrapper.style.transform = `scale(${zoom})`;
      } else {
        // 3D view - use slider values
        const rotX = parseInt(rotateXRange.value, 10);
        const rotY = parseInt(rotateYRange.value, 10);
        const rotZ = parseInt(rotateRange.value, 10);
        boardWrapper.style.transform = `scale(${zoom}) rotateX(${rotX}deg) rotateY(${rotY}deg) rotateZ(${rotZ}deg)`;
      }
    }

    zoomRange.addEventListener("input", updateBoardTransform);
    rotateRange.addEventListener("input", updateBoardTransform);
    rotateXRange.addEventListener("input", updateBoardTransform);
    rotateYRange.addEventListener("input", updateBoardTransform);
    
    function updateCoinOffsetDisplay() {
      document.getElementById("offsetXValue").textContent = offsetXRange.value;
      document.getElementById("offsetYValue").textContent = offsetYRange.value;
    }
    
    offsetXRange.addEventListener("input", updateCoinOffsetDisplay);
    offsetYRange.addEventListener("input", updateCoinOffsetDisplay);
    
    document.getElementById("applyCoinOffset").addEventListener("click", () => {
      positionExistingTokens();
    });

    viewToggle.addEventListener("click", () => {
      is3DView = !is3DView;
      viewStatus.textContent = is3DView ? "3D" : "Top";
      if (is3DView) {
        // 3D view - restore slider values
        updateBoardTransform();
        board3d.style.transform = "";
      } else {
        // Top view - completely flat
        const zoom = parseInt(zoomRange.value, 10) / 100;
        boardWrapper.style.transform = `scale(${zoom})`;
        board3d.style.transform = "rotateX(0deg) rotateY(0deg) rotateZ(0deg)";
      }
      // Recalculate tile centers and reposition tokens after view change
      setTimeout(() => {
        computeTileCenters();
        positionExistingTokens();
      }, 100);
    });

    demoToggle.addEventListener("click", () => {
      demoMode = !demoMode;
      demoStatus.textContent = demoMode ? "ON" : "OFF";
      modeStatus.textContent = demoMode ? "DEMO" : "LIVE";
      if (!demoMode) fetchAndUpdate();
    });

    // Audio controls
    const audioToggle = document.getElementById("audioToggle");
    const audioStatus = document.getElementById("audioStatus");
    const bgMusicVolume = document.getElementById("bgMusicVolume");
    const diceVolume = document.getElementById("diceVolume");
    const moveVolume = document.getElementById("moveVolume");
    const chanceVolume = document.getElementById("chanceVolume");
    const tileVolume = document.getElementById("tileVolume");
    const eliminatedVolume = document.getElementById("eliminatedVolume");
    const winnerVolume = document.getElementById("winnerVolume");

    audioToggle?.addEventListener("click", () => {
      audioEnabled.value = !audioEnabled.value;
      audioStatus.textContent = audioEnabled.value ? "ON" : "OFF";
      if (audioEnabled.value) {
        startBackgroundMusic();
      } else {
        stopBackgroundMusic();
      }
    });

    bgMusicVolume?.addEventListener("input", (e) => {
      const val = parseInt(e.target.value);
      audioVolumes.bgMusic = val / 100;
      document.getElementById("bgMusicValue").textContent = `${val}%`;
    });

    diceVolume?.addEventListener("input", (e) => {
      const val = parseInt(e.target.value);
      audioVolumes.dice = val / 100;
      document.getElementById("diceValue").textContent = `${val}%`;
    });

    moveVolume?.addEventListener("input", (e) => {
      const val = parseInt(e.target.value);
      audioVolumes.move = val / 100;
      document.getElementById("moveValue").textContent = `${val}%`;
    });

    chanceVolume?.addEventListener("input", (e) => {
      const val = parseInt(e.target.value);
      audioVolumes.chance = val / 100;
      document.getElementById("chanceValue").textContent = `${val}%`;
    });

    tileVolume?.addEventListener("input", (e) => {
      const val = parseInt(e.target.value);
      audioVolumes.tile = val / 100;
      document.getElementById("tileValue").textContent = `${val}%`;
    });

    eliminatedVolume?.addEventListener("input", (e) => {
      const val = parseInt(e.target.value);
      audioVolumes.eliminated = val / 100;
      document.getElementById("eliminatedValue").textContent = `${val}%`;
    });

    winnerVolume?.addEventListener("input", (e) => {
      const val = parseInt(e.target.value);
      audioVolumes.winner = val / 100;
      document.getElementById("winnerValue").textContent = `${val}%`;
    });

    function ensureTokenForPlayer(player) {
      const existingToken = tokensByPlayerId[player.id];
      
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
      tokensLayer.appendChild(token);
      tokensByPlayerId[player.id] = token;
      return token;
    }
    function positionToken(playerId, tileIndex, offsetIndex, animate = false, fromTile = null) {
      const token = tokensByPlayerId[playerId];
      if (!token) return;

      // Prevent immediate repositioning while a dice animation is playing.
      // If the client is animating dice and someone requests a non-animated
      // move to a different tile, ignore it â€” the queued state will be
      // applied after the dice animation completes.
      if (isDiceAnimationPlaying && !animate) {
        const currentIdx = (token.dataset.tileIndex !== undefined && token.dataset.tileIndex !== '') ? parseInt(token.dataset.tileIndex) : null;
        if (currentIdx !== null && currentIdx !== tileIndex) {
          console.debug('[live] positionToken deferred due to dice animation', playerId, 'from', currentIdx, 'to', tileIndex);
          return;
        }
      }

      // Find the target tile element
      const tileElem = tilesGrid.querySelector(`.tile[data-tile="${tileIndex}"]`);
      if (!tileElem) return;

      // If animating, calculate path and animate
      if (animate && fromTile !== null && fromTile !== tileIndex) {
        animateTokenWalk(playerId, fromTile, tileIndex, offsetIndex);
        return;
      }

      // Immediate positioning (no animation)
      if (token.parentElement !== tileElem) tileElem.appendChild(token);

      // Compute per-player offsets from spinner values (arranged 2x2 around base)
      const baseOffsetX = parseInt(offsetXRange.value, 10) || 0;
      const baseOffsetY = parseInt(offsetYRange.value, 10) || 0;
      const offsets = [
        { dx: baseOffsetX - 8, dy: baseOffsetY - 8 },
        { dx: baseOffsetX + 8, dy: baseOffsetY - 8 },
        { dx: baseOffsetX - 8, dy: baseOffsetY + 8 },
        { dx: baseOffsetX + 8, dy: baseOffsetY + 8 }
      ];
      const off = offsets[offsetIndex % 4] || { dx: 0, dy: 0 };

      // Center token within tile and apply offsets (relative to tile)
      token.style.left = `calc(50% + ${off.dx}px)`;
      token.style.top = `calc(50% + ${off.dy}px)`;
      token.style.transform = is3DView
        ? "translate(-50%, -50%) translateZ(20px)"
        : "translate(-50%, -50%)";

      // Update label to show tile number
      const label = token.querySelector(".player-token-label");
      if (label) label.textContent = `Tile ${tileIndex}`;
    }

    // Animate token walking from one tile to another
    function animateTokenWalk(playerId, fromTileIndex, toTileIndex, offsetIndex, onComplete) {
      const token = tokensByPlayerId[playerId];
      if (!token) return;

      console.debug('[live] animateTokenWalk START', playerId, 'from', fromTileIndex, 'to', toTileIndex, 'offset', offsetIndex);

      // Calculate path (tiles to walk through)
      const path = calculateTilePath(fromTileIndex, toTileIndex);
      if (path.length === 0) return;

      let currentStep = 0;
      const stepDuration = 600; // ms per tile (slower for visibility)

      function walkNextStep() {
        if (currentStep >= path.length) {
          // Animation complete - position at final tile
          positionToken(playerId, toTileIndex, offsetIndex, false);
          playSound('tile'); // Play tile landing sound
          if (typeof onComplete === 'function') {
            try { onComplete(); } catch (err) { console.error(err); }
          }
          console.debug('[live] animateTokenWalk END', playerId, 'arrived', toTileIndex);
          return;
        }

        const currentTile = path[currentStep];
        const tileElem = tilesGrid.querySelector(`.tile[data-tile="${currentTile}"]`);
        
        if (tileElem && token.parentElement !== tileElem) {
          tileElem.appendChild(token);
        }

        // Play movement sound for each step
        if (currentStep > 0) playSound('move');

        // Apply position with transition
        const baseOffsetX = parseInt(offsetXRange.value, 10) || 0;
        const baseOffsetY = parseInt(offsetYRange.value, 10) || 0;
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
      }

      walkNextStep();
    }

    // Calculate the path of tiles from start to end
    function calculateTilePath(fromTile, toTile) {
      const boardSequence = [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20];
      
      const fromIndex = boardSequence.indexOf(fromTile);
      const toIndex = boardSequence.indexOf(toTile);
      
      if (fromIndex === -1 || toIndex === -1) return [];
      
      const path = [];
      if (fromIndex < toIndex) {
        // Moving forward
        for (let i = fromIndex + 1; i <= toIndex; i++) {
          path.push(boardSequence[i]);
        }
      } else {
        // Moving backward or wrapping around
        for (let i = fromIndex + 1; i < boardSequence.length; i++) {
          path.push(boardSequence[i]);
        }
        for (let i = 0; i <= toIndex; i++) {
          path.push(boardSequence[i]);
        }
      }
      
      return path;
    }
    function positionExistingTokens() {
      Object.values(tokensByPlayerId).forEach((token, index) => {
        const tileIndex = token.dataset.tileIndex;
        if (tileIndex) positionToken(token.dataset.player, tileIndex, index);
      });
    }

    function updateUIFromState(state) {
      if (!state) return;
      
      // Start recording when game begins (first time we receive valid state)
      if (!gameStarted && state.players && state.players.length > 0) {
        const sessionId = activeSessionId || `session-${Date.now()}`;
        const boardId = activeBoardId || 'unknown';
        replayRecorder.startRecording(sessionId, boardId, state.players);
        gameStarted = true;
        console.log('[Replay] Game started, recording begun');
      }
      
      playersList.innerHTML = "";
      eliminatedList.innerHTML = "";
      
      const players = (state.players || []).map(p => ({
        ...p,
        drops: p.score !== undefined ? p.score : (p.drops !== undefined ? p.drops : 10)  // Use score from API as drops, fallback to drops field or 10
      }));
      
      // Sort active players by drops (highest first), with original index as tiebreaker
      const activePlayers = players
        .map((p, originalIndex) => ({ ...p, originalIndex }))
        .filter(p => !p.eliminated)
        .sort((a, b) => {
          if (b.drops !== a.drops) {
            return b.drops - a.drops; // Higher drops first
          }
          return a.originalIndex - b.originalIndex; // Same drops: earlier player first (seniority)
        });
      
      const eliminatedPlayers = players.filter(p => p.eliminated);
      
      // Detect newly eliminated players and play sound
      eliminatedPlayers.forEach(p => {
        if (!previouslyEliminatedIds.has(p.id)) {
          playSound('eliminated');
          previouslyEliminatedIds.add(p.id);
          
          // Record elimination event
          if (replayRecorder.isRecording()) {
            replayRecorder.recordElimination(p.id, p.name || p.id);
          }
        }
      });
      
      // Calculate ranks for active players (consecutive ranking)
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
      
      // Update active players list with ranks
      activePlayers.forEach((p) => {
        const row = document.createElement("div");
        row.className = "player-row";
        const main = document.createElement("div");
        main.className = "player-main";
        
        // Add rank badge
        const rank = document.createElement("div");
        rank.className = "player-rank";
        rank.textContent = `Rank ${p.rank}`;
        
        const dot = document.createElement("div");
        dot.className = "player-dot";
        const gradients = [
          "radial-gradient(circle at 30% 20%, #fecaca, #b91c1c)",
          "radial-gradient(circle at 30% 20%, #a7f3d0, #16a34a)",
          "radial-gradient(circle at 30% 20%, #bfdbfe, #1d4ed8)",
          "radial-gradient(circle at 30% 20%, #fed7aa, #ea580c)"
        ];
        dot.style.background = gradients[p.originalIndex % 4];
        const name = document.createElement("div");
        name.className = "player-name";
        name.textContent = p.name || p.id;
        const meta = document.createElement("div");
        meta.className = "player-meta";
        meta.textContent = `Pos: ${p.pos ?? "-"}`;
        main.appendChild(rank);
        main.appendChild(dot);
        main.appendChild(name);
        main.appendChild(meta);
        const pos = document.createElement("div");
        pos.className = "player-pos";
        pos.textContent = `ðŸ’§ ${p.drops ?? 0}`;
        row.appendChild(main);
        row.appendChild(pos);
        playersList.appendChild(row);
      });
      
      // Update eliminated players list with last rank
      const lastRank = activePlayers.length + eliminatedPlayers.length;
      eliminatedPlayers.forEach((p) => {
        const item = document.createElement("div");
        item.className = "eliminated-item";
        
        const rank = document.createElement("div");
        rank.className = "player-rank eliminated-rank";
        rank.textContent = `Rank ${lastRank}`;
        
        const tokenIcon = document.createElement("div");
        tokenIcon.className = "eliminated-token";
        const playerIndex = players.indexOf(p);
        const pawnImages = [
          "/assets/pawns/pawn-red.svg",
          "/assets/pawns/pawn-green.svg",
          "/assets/pawns/pawn-blue.svg",
          "/assets/pawns/pawn-yellow.svg"
        ];
        tokenIcon.style.backgroundImage = `url('${pawnImages[playerIndex % 4]}')`;
        
        const name = document.createElement("span");
        name.textContent = p.name || p.id;
        
        item.appendChild(rank);
        item.appendChild(tokenIcon);
        item.appendChild(name);
        eliminatedList.appendChild(item);
      });
      
      // Update eliminated count badge
      eliminatedCount.textContent = eliminatedPlayers.length;
      
      // Check for winner (only one player remaining)
      if (activePlayers.length === 1 && eliminatedPlayers.length > 0) {
        const winner = activePlayers[0];
        
        // Only record and save once
        if (!lastWinner || lastWinner.id !== winner.id) {
          lastWinner = winner;
          showWinner(winner);
          
          // Record winner and auto-save replay
          if (replayRecorder.isRecording()) {
            replayRecorder.recordWinner(winner.id, winner.name || winner.id, winner.drops);
            
            // Collect final scores
            const finalScores = players.map(p => p.drops || 0);
            
            // Auto-save replay
            replayRecorder.autoSave('completed', winner, finalScores)
              .then(result => {
                if (result && result.shareUrl) {
                  console.log('[Replay] Game replay saved! Share at:', result.shareUrl);
                  // Optionally show share URL to user
                  showReplaySharePopup(result.shareUrl, result.replayId);
                }
              })
              .catch(err => {
                console.error('[Replay] Failed to save replay:', err);
              });
          }
        }
      }

      // If a dice animation is currently playing on the client, queue the latest
      // polled state and DO NOT touch tokens at all (they are animating).
      if (isDiceAnimationPlaying) {
        console.debug('[live] updateUIFromState â€” animation playing, queuing latest state');
        queuedLiveState = state; // keep only newest state
        // Keep a minimal event message while animation plays
        if (state.lastEvent && state.lastEvent.rolling) {
          eventLog.innerHTML = `<span class='event-highlight'>${state.lastEvent.playerName || state.lastEvent.playerId} is rollingâ€¦</span>`;
        }
        return;
      }

      // Clear tokens for inactive players
      Object.keys(tokensByPlayerId).forEach(playerId => {
        const token = tokensByPlayerId[playerId];
        if (token && !players.find(p => p.id === playerId)) {
          token.style.display = 'none';
        }
      });

      // Check if dice is currently rolling BEFORE updating positions
      const isRolling = state.lastEvent && state.lastEvent.rolling === true;
      
      // Store position update function to call after dice animation
      const updatePlayerPositions = () => {
        // If a dice animation is playing, queue this state and do not move tokens now
        if (isDiceAnimationPlaying) {
          queuedLiveState = state;
          return;
        }
        players.forEach((p, idx) => {
          ensureTokenForPlayer(p);
          const token = tokensByPlayerId[p.id];
          
          // Mark eliminated players with special styling
          if (p.eliminated) {
            token.classList.add('eliminated');
          } else {
            token.classList.remove('eliminated');
            token.style.display = 'block';
          }
          
          // Get stored and new positions
          const previousPos = (token.dataset.tileIndex !== undefined && token.dataset.tileIndex !== '') ? parseInt(token.dataset.tileIndex) : null;
          const newPos = p.pos;

          // If position changed and we should animate, run the walking animation
          if (!p.eliminated && previousPos !== null && previousPos !== newPos) {
            // Use animateTokenWalk directly and update dataset only when animation completes
            animateTokenWalk(p.id, previousPos, newPos, idx, () => {
              const t = tokensByPlayerId[p.id];
              if (t) t.dataset.tileIndex = newPos;
            });
          } else {
            // No animation required â€” position immediately and update dataset
            positionToken(p.id, newPos, idx, false);
            token.dataset.tileIndex = newPos;
          }
        });
      };

      // If rolling, don't touch tokens at all - they will be positioned after dice animation
      if (isRolling) {
        // Just ensure tokens exist, no positioning yet
        players.forEach((p, idx) => {
          ensureTokenForPlayer(p);
        });
      }

      if (state.lastEvent) {
        const e = state.lastEvent;
        
        // CRITICAL GUARD: If this event has dice values and we haven't set the animation flag yet,
        // or if animation is already playing, do NOT process anything
        if (e.dice1 && e.dice1 !== null && !e.rolling && !e.reset && !e.undo) {
          const eventId = `${e.playerId}-${e.dice1}-${e.dice2 || 0}-${e.tileIndex}`;
          const isNewEvent = !lastProcessedEvent || lastProcessedEvent !== eventId;
          
          // If this is a new dice roll event, set the flag IMMEDIATELY before anything else
          if (isNewEvent && !isDiceAnimationPlaying) {
            isDiceAnimationPlaying = true;
            console.debug('[live] EARLY GUARD: New dice event detected, flag set immediately');
          }
        }
        
        // Skip processing if lastEvent has empty playerId (happens after reset)
        if (!e.playerId || e.playerId === "") {
          eventLog.innerHTML = "Waiting for first roll...";
          updatePlayerPositions();
          return;
        }
        
        const playerName = e.playerName || e.playerId || "Player";
        const diceColor1 = e.diceColor1 || e.diceColor || null;
        const diceColor2 = e.diceColor2 || null;
        
        // Check if dice is currently rolling (from Android sensors)
        if (e.rolling === true) {
          // DICE IS PHYSICALLY ROLLING - Show rolling message and animate
          const rollingCount = e.rollingDiceCount || 1;
          const rollingText = rollingCount > 1 
            ? `${playerName} is rolling both dice...` 
            : `${playerName} is rolling the dice...`;
          eventLog.innerHTML = `<span class='event-highlight'>${rollingText}</span>`;
          
          // Trigger continuous rolling animation until we get rolling: false
          showRollingDice(playerName, diceColor1, diceColor2, e.dice1Rolling, e.dice2Rolling, e.dice1, e.dice2);
          // Note: Continue processing to keep tokens visible on board
        }
        
        // Dice has stopped - process the result (only if not currently rolling)
        if (!e.rolling) {
          // Skip dice animation for reset and undo events
          if (e.reset === true) {
            eventLog.innerHTML = "Game reset - Ready to play!";
            // Clear last processed event to allow new rolls
            lastProcessedEvent = null;
            // Cancel any ongoing dice animation and queued states
            isDiceAnimationPlaying = false;
            queuedLiveState = null;
            try {
              if (scoreboardDice1) scoreboardDice1.style.animation = 'none';
              if (scoreboardDice2) scoreboardDice2.style.animation = 'none';
            } catch (err) { /* ignore */ }
            updatePlayerPositions(); // Update positions immediately for reset
            return;
          }
          
          if (e.undo === true) {
            eventLog.innerHTML = `<span class='event-highlight'>Undo</span> - ${playerName} returns to previous position`;
            updatePlayerPositions(); // Update positions immediately for undo
            return;
          }
          
          // Skip if no dice values (e.g., initial state)
          if (!e.dice1) {
            eventLog.innerHTML = "Waiting for first roll...";
            updatePlayerPositions(); // Update positions immediately
            return;
          }
          
          // Ensure dice values are present before trying to animate
          if (e.dice1 === null || e.dice1 === undefined) {
            eventLog.innerHTML = "Waiting for first roll...";
            updatePlayerPositions(); // Update positions immediately
            return;
          }

          // Create a unique identifier for this event
          const eventId = `${e.playerId}-${e.dice1}-${e.dice2 || 0}-${e.tileIndex}`;
          const isNewEvent = !lastProcessedEvent || lastProcessedEvent !== eventId;
          
          if (isNewEvent) {
            // NEW ROLL - Flag already set by early guard above
            lastProcessedEvent = eventId;
            console.debug('[live] Processing new event, flag already set');
            
            eventLog.innerHTML = `<span class='event-highlight'>${playerName}</span> is rolling...`;
            
            // Clear any pending token movement timer from previous roll
            if (tokenMoveTimer) {
              clearTimeout(tokenMoveTimer);
              tokenMoveTimer = null;
            }
            
            // Ensure all tokens exist and are visible at CURRENT positions before dice animation
            players.forEach((p, idx) => {
              ensureTokenForPlayer(p);
              const token = tokensByPlayerId[p.id];
              if (p.eliminated) {
                token.style.display = 'none';
              } else {
                token.style.display = 'block';
              }
            });
            
            // Show 3D dice animation in scoreboard
            if (e.dice2 !== null && e.dice2 !== undefined) {
              // Double dice mode
              show3DDiceRoll(e.dice1, e.dice2, playerName, () => {
                // Update event log AFTER dice animation completes
                eventLog.innerHTML = `<span class='event-highlight'>${playerName}</span> rolled <span class='event-highlight'>${e.dice1}</span> and <span class='event-highlight'>${e.dice2}</span>, moved to <span class='event-highlight'>Tile ${e.tileIndex}</span>${e.chanceCardId ? ` and drew card <span class='event-highlight'>${e.chanceCardId}</span>.` : "."}`;
              }, diceColor1, diceColor2);
              
              // Record dice roll event
              if (replayRecorder.isRecording()) {
                replayRecorder.recordRoll(e.playerId, playerName, e.dice1, e.dice2, diceColor1, diceColor2);
              }
              
              // DELAYED TOKEN MOVEMENT: Wait 3 seconds after receiving API data before moving tokens
              // This ensures dice animation (2s) completes first with 1s buffer
              console.debug('[live] Starting 3s timer before token movement');
              tokenMoveTimer = setTimeout(() => {
                console.debug('[live] Timer complete - clearing animation flag and moving tokens now');
                isDiceAnimationPlaying = false;
                updatePlayerPositions();
                
                // Record move event after positions update
                if (replayRecorder.isRecording()) {
                  const player = players.find(p => p.id === e.playerId);
                  if (player) {
                    const fromPos = previousPlayerPositions.get(e.playerId) || 0;
                    const toPos = player.pos;
                    const tileName = tileNames[toPos] || `Tile ${toPos}`;
                    replayRecorder.recordMove(e.playerId, fromPos, toPos, tileName);
                    previousPlayerPositions.set(e.playerId, toPos);
                  }
                }
                
                tokenMoveTimer = null;
              }, 3000);
            } else {
              // Single dice mode
              show3DDiceRoll(e.dice1, null, playerName, () => {
                // Update event log AFTER dice animation completes
                eventLog.innerHTML = `<span class='event-highlight'>${playerName}</span> rolled <span class='event-highlight'>${e.dice1}</span>, moved to <span class='event-highlight'>Tile ${e.tileIndex}</span>${e.chanceCardId ? ` and drew card <span class='event-highlight'>${e.chanceCardId}</span>.` : "."}`;
              }, diceColor1, diceColor2);
              
              // Record dice roll event
              if (replayRecorder.isRecording()) {
                replayRecorder.recordRoll(e.playerId, playerName, e.dice1, null, diceColor1, null);
              }
              
              // DELAYED TOKEN MOVEMENT: Wait 3 seconds after receiving API data before moving tokens
              // This ensures dice animation (2s) completes first with 1s buffer
              console.debug('[live] Starting 3s timer before token movement');
              tokenMoveTimer = setTimeout(() => {
                console.debug('[live] Timer complete - clearing animation flag and moving tokens now');
                isDiceAnimationPlaying = false;
                updatePlayerPositions();
                
                // Record move event after positions update
                if (replayRecorder.isRecording()) {
                  const player = players.find(p => p.id === e.playerId);
                  if (player) {
                    const fromPos = previousPlayerPositions.get(e.playerId) || 0;
                    const toPos = player.pos;
                    const tileName = tileNames[toPos] || `Tile ${toPos}`;
                    replayRecorder.recordMove(e.playerId, fromPos, toPos, tileName);
                    previousPlayerPositions.set(e.playerId, toPos);
                  }
                }
                
                tokenMoveTimer = null;
              }, 3000);
            }
          } else {
            // SAME EVENT - Just show the static result (no animation)
            showStaticDice(e.dice1, e.dice2, playerName, diceColor1, diceColor2);
            eventLog.innerHTML = `<span class='event-highlight'>${playerName}</span> rolled <span class='event-highlight'>${e.dice1}</span>${e.dice2 !== null && e.dice2 !== undefined ? ` and <span class='event-highlight'>${e.dice2}</span>` : ''}, moved to <span class='event-highlight'>Tile ${e.tileIndex}</span>${e.chanceCardId ? ` and drew card <span class='event-highlight'>${e.chanceCardId}</span>.` : "."}`;
            // Positions already set, no need to animate again
          }
        }
      } else {
        eventLog.textContent = "Waiting for first dropâ€¦";
      }

      if (state.lastEvent && state.lastEvent.chanceCardId) {
        const id = state.lastEvent.chanceCardId;
        playSound('chance'); // Play chance card sound
        chanceImage.style.backgroundImage = `url('/assets/chance/${id}.jpg')`;
        chanceTitle.textContent = `Chance Card â€“ ${id}`;
        chanceText.textContent = (state.chanceDescriptions && state.chanceDescriptions[id]) || "Special event triggered by the board.";
        chanceCard.classList.add("visible");
        if (lastChanceTimeout) clearTimeout(lastChanceTimeout);
        lastChanceTimeout = setTimeout(() => chanceCard.classList.remove("visible"), 4500);
      } else {
        chanceCard.classList.remove("visible");
      }
    }

    async function fetchAndUpdate() {
      if (demoMode) return;
      if (!navigator.onLine) {
        console.log('Skipping fetch - offline');
        return;
      }
      
      let retryAttempt = 0;
      const maxRetries = 5;
      const retryDelays = [2000, 4000, 8000, 16000, 32000];  // Exponential backoff
      
      async function attemptFetch() {
        try {
          const res = await fetch(LIVE_STATE_URL, { cache: "no-store" });
          if (!res.ok) throw new Error("HTTP " + res.status);
          const state = await res.json();

          // Check if this is the first successful connection
          if (!connectionEstablished && state.players && state.players.length > 0) {
            connectionEstablished = true;
            onControllerConnected();
          }

          connStatus.textContent = "ONLINE";
          connStatus.classList.remove("badge-red");
          connStatus.classList.add("badge-green");
          modeStatus.textContent = "LIVE";
          
          // Hide reconnection overlay if visible
          hideReconnectionOverlay();
          
          // Hide loading overlay on first successful fetch
          if (!firstFetchComplete) {
            firstFetchComplete = true;
            loadingOverlay.classList.add('hidden');
          }
          
          // Reset retry counter on success
          retryAttempt = 0;
          
          // Update "Last Updated" timestamp
          const now = new Date();
          const timeStr = now.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
          lastUpdatedEl.textContent = `Last updated: ${timeStr}`;
          
          updateUIFromState(state);
        } catch (err) {
          console.error("Live state error (attempt " + (retryAttempt + 1) + "/" + maxRetries + "):", err);
          
          if (retryAttempt < maxRetries) {
            // Show reconnection overlay
            showReconnectionOverlay(retryAttempt + 1, retryDelays[retryAttempt]);
            
            // Wait and retry
            await new Promise(resolve => setTimeout(resolve, retryDelays[retryAttempt]));
            retryAttempt++;
            return attemptFetch();  // Retry
          } else {
            // Max retries reached
            connStatus.textContent = "OFFLINE";
            connStatus.classList.remove("badge-green");
            connStatus.classList.add("badge-red");
            modeStatus.textContent = "DEMO";
            showReconnectionOverlay(maxRetries, null);  // Show permanent error
          }
        }
      }
      
      await attemptFetch();
    }

    // Start live polling interval only if not in demo mode
    let livePollingInterval;
    function startLivePolling() {
      if (!livePollingInterval) {
        livePollingInterval = setInterval(() => { 
          if (!demoMode) fetchAndUpdate(); 
        }, 2000);
      }
    }

    // Connection popup functions
    function showConnectionPopup() {
      connectionOverlay.classList.remove('hidden');
      // Start polling for live connection when popup is shown
      startLivePolling();
    }

    function hideConnectionPopup() {
      connectionOverlay.classList.add('hidden');
    }

    function onControllerConnected() {
      // Update popup status
      connectionIndicator.querySelector('.status-dot').classList.add('connected');
      connectionText.textContent = 'Controller Connected! ðŸŽ‰';

      // Hide popup after 2 seconds
      setTimeout(() => {
        hideConnectionPopup();
      }, 2000);
    }
    
    // Reconnection overlay functions
    function showReconnectionOverlay(attempt, delayMs) {
      reconnectionOverlay.classList.remove('hidden');
      
      if (delayMs === null) {
        // Max retries reached
        reconnectionText.textContent = 'Connection lost - Max retries reached';
        reconnectionInfo.innerHTML = '<p style="color: var(--danger);">Unable to reach server. Please check your network connection.</p>';
      } else {
        reconnectionText.textContent = 'Attempting to reconnect...';
        retryAttemptEl.textContent = attempt;
        retryDelayEl.textContent = (delayMs / 1000).toFixed(0);
      }
    }
    
    function hideReconnectionOverlay() {
      reconnectionOverlay.classList.add('hidden');
    }

    // Dice animation function for board overlay (kept for compatibility)
    function showDiceRoll(value1, value2 = null) {
      if (!diceDisplay || !dice1El || !dice2El) return;
      
      // Single dice mode
      if (value2 === null) {
        dice1El.setAttribute('data-value', value1.toString());
        dice1El.classList.add('single');
        dice2El.style.display = 'none';
      } else {
        // Double dice mode
        dice1El.setAttribute('data-value', value1.toString());
        dice2El.setAttribute('data-value', value2.toString());
        dice1El.classList.remove('single');
        dice2El.style.display = 'flex';
      }
      
      // Show dice with animation
      diceDisplay.classList.remove('hidden');
      
      // Reset animation by removing and re-adding class
      dice1El.style.animation = 'none';
      if (value2 !== null) dice2El.style.animation = 'none';
      setTimeout(() => {
        dice1El.style.animation = '';
        if (value2 !== null) dice2El.style.animation = '';
      }, 10);
      
      // Hide after 2 seconds
      setTimeout(() => {
        diceDisplay.classList.add('hidden');
      }, 2000);
    }

    // Show winner celebration
    function showWinner(player) {
      playSound('winner');
      winnerName.textContent = player.name || player.id;
      winnerDrops.textContent = `ðŸ’§ ${player.drops} drops`;
      winnerOverlay.classList.remove('hidden');
    }

    // Helper function to set dice dots color (all dots same color)
    function setDiceDotsColor(colorName) {
      const colorMap = {
        'red': '#ef4444',
        'green': '#22c55e',
        'blue': '#60a5fa',
        'yellow': '#eab308',
        'orange': '#f97316',
        'black': '#1f2937'
      };
      
      const hexColor = colorMap[colorName] || colorName;
      currentDiceColor = hexColor;
      
      // Update all dice dots
      const allDots = document.querySelectorAll('.dice-dot');
      allDots.forEach(dot => {
        dot.style.backgroundColor = hexColor;
      });
    }
    
    // Helper function to set individual colors for each die
    function setIndividualDiceColors(color1, color2 = null) {
      const colorMap = {
        'red': '#ef4444',
        'green': '#22c55e',
        'blue': '#60a5fa',
        'yellow': '#eab308',
        'orange': '#f97316',
        'black': '#1f2937'
      };
      
      const hexColor1 = colorMap[color1] || color1 || '#60a5fa';
      const hexColor2 = color2 ? (colorMap[color2] || color2) : hexColor1;
      
      // Update dice 1 dots
      const dice1Dots = scoreboardDice1.querySelectorAll('.dice-dot');
      dice1Dots.forEach(dot => {
        dot.style.backgroundColor = hexColor1;
      });
      
      // Update dice 2 dots
      const dice2Dots = scoreboardDice2.querySelectorAll('.dice-dot');
      dice2Dots.forEach(dot => {
        dot.style.backgroundColor = hexColor2;
      });
    }

    // Show rolling dice animation (continuous while Android sends rolling: true)
    // Show rolling dice animation (continuous while Android sends rolling: true)
function showRollingDice(
  playerName = "Player",
  diceColor1 = null,
  diceColor2 = null,
  dice1Rolling = true,
  dice2Rolling = true,
  value1 = null,
  value2 = null
) {
  if (!scoreboardDiceDisplay || !scoreboardDice1 || !scoreboardDice2 || !dicePlayerName) return;

  // Update dice colors if provided
  if (diceColor1 && diceColor2) {
    // Two different colors for 2-dice mode
    setIndividualDiceColors(diceColor1, diceColor2);
  } else if (diceColor1) {
    // Single color for both dice
    setDiceDotsColor(diceColor1);
  }

  // Update player name text based on which dice are rolling
  if (dice1Rolling && dice2Rolling) {
    dicePlayerName.textContent = `${playerName} is rolling both dice...`;
  } else if (dice1Rolling) {
    dicePlayerName.textContent = `${playerName} - die 1 rolling...`;
  } else if (dice2Rolling) {
    dicePlayerName.textContent = `${playerName} - die 2 rolling...`;
  } else {
    dicePlayerName.textContent = `${playerName} is rolling...`;
  }

  // Helper: same rotation map as in showStaticDice
  const getRotation = (value) => {
    const rotations = {
      1: 'rotateX(0deg) rotateY(0deg)',
      2: 'rotateX(-90deg) rotateY(0deg)',
      3: 'rotateX(0deg) rotateY(90deg)',
      4: 'rotateX(0deg) rotateY(-90deg)',
      5: 'rotateX(90deg) rotateY(0deg)',
      6: 'rotateX(180deg) rotateY(0deg)'
    };
    return rotations[value] || rotations[1];
  };

  // Make both dice visible during rolling
  scoreboardDiceDisplay.style.display = 'block';
  scoreboardDice1.classList.remove('single');
  scoreboardDice2.classList.remove('single');
  scoreboardDice2.style.display = 'block';

  // ---- Die 1 ----
  if (dice1Rolling === false && value1 != null) {
    // Die 1 has stopped: show its final face, no animation
    const faces1 = scoreboardDice1.querySelectorAll('.dice-face');
    faces1.forEach(face => {
      face.setAttribute('data-value', value1.toString());
    });
    scoreboardDice1.style.animation = 'none';
    scoreboardDice1.style.transform = getRotation(value1);
  } else if (dice1Rolling !== false) {
    // Die 1 still rolling
    scoreboardDice1.style.animation = 'dice-roll 0.5s linear infinite';
  } else {
    // Not rolling and no value â€“ keep idle
    scoreboardDice1.style.animation = 'none';
  }

  // ---- Die 2 ----
  if (value2 == null && !dice2Rolling) {
    // Single-die mode: hide second die
    scoreboardDice2.style.display = 'none';
    scoreboardDice2.style.animation = 'none';
  } else if (dice2Rolling === false && value2 != null) {
    // Die 2 has stopped: show its final face, no animation
    const faces2 = scoreboardDice2.querySelectorAll('.dice-face');
    faces2.forEach(face => {
      face.setAttribute('data-value', value2.toString());
    });
    scoreboardDice2.style.animation = 'none';
    scoreboardDice2.style.transform = getRotation(value2);
  } else if (dice2Rolling !== false) {
    // Die 2 still rolling
    scoreboardDice2.style.animation = 'dice-roll 0.5s linear infinite';
  } else {
    scoreboardDice2.style.animation = 'none';
  }
}

function showStaticDice(value1, value2 = null, playerName = "Player", diceColor1 = null, diceColor2 = null) {
      if (!scoreboardDiceDisplay || !scoreboardDice1 || !scoreboardDice2 || !dicePlayerName) return;
      
      // Update dice colors if provided
      if (diceColor1 && diceColor2) {
        setIndividualDiceColors(diceColor1, diceColor2);
      } else if (diceColor1) {
        setDiceDotsColor(diceColor1);
      }
      
      // Update player name text
      dicePlayerName.textContent = `${playerName} rolled ${value2 !== null ? value1 + ' & ' + value2 : value1}`;
      
      // Calculate rotation to show correct face
      const getRotation = (value) => {
        const rotations = {
          1: 'rotateX(0deg) rotateY(0deg)',
          2: 'rotateX(-90deg) rotateY(0deg)',
          3: 'rotateX(0deg) rotateY(90deg)',
          4: 'rotateX(0deg) rotateY(-90deg)',
          5: 'rotateX(90deg) rotateY(0deg)',
          6: 'rotateX(180deg) rotateY(0deg)'
        };
        return rotations[value] || rotations[1];
      };
      
      // Set dice faces to show final values
      const faces1 = scoreboardDice1.querySelectorAll('.dice-face');
      faces1.forEach(face => {
        face.setAttribute('data-value', value1.toString());
      });
      
      // Single or double dice mode
      if (value2 === null) {
        scoreboardDice1.classList.add('single');
        scoreboardDice2.style.display = 'none';
      } else {
        scoreboardDice1.classList.remove('single');
        scoreboardDice2.classList.remove('single');
        scoreboardDice2.style.display = 'block';
        
        // Set dice 2 faces to show final value
        const faces2 = scoreboardDice2.querySelectorAll('.dice-face');
        faces2.forEach(face => {
          face.setAttribute('data-value', value2.toString());
        });
      }
      
      // Remove any animation and set final rotation immediately
      scoreboardDice1.style.animation = 'none';
      scoreboardDice1.style.transform = getRotation(value1);
      if (value2 !== null) {
        scoreboardDice2.style.animation = 'none';
        scoreboardDice2.style.transform = getRotation(value2);
      }
    }

    // 3D Dice animation for scoreboard
    function show3DDiceRoll(value1, value2 = null, playerName = "Player", callback = null, diceColor1 = null, diceColor2 = null) {
      if (!scoreboardDiceDisplay || !scoreboardDice1 || !scoreboardDice2 || !dicePlayerName) return;
      // Defensive: if no dice value provided, do not play the animation
      if (value1 === null || value1 === undefined) {
        if (typeof callback === 'function') callback();
        return;
      }
      // Mark that the client is playing a dice animation so incoming polled
      // states won't move tokens until this animation completes.
      console.debug('[live] show3DDiceRoll START â€” setting isDiceAnimationPlaying = true', value1, value2, playerName);
      isDiceAnimationPlaying = true;
      
      // Update dice colors if provided
      if (diceColor1 && diceColor2) {
        setIndividualDiceColors(diceColor1, diceColor2);
      } else if (diceColor1) {
        setDiceDotsColor(diceColor1);
      }
      
      // Play dice roll sound
      playSound('dice');
      
      // Update player name text
      dicePlayerName.textContent = `${playerName} rolling dice...`;
      
      // Calculate rotation to show correct face
      const getRotation = (value) => {
        const rotations = {
          1: 'rotateX(0deg) rotateY(0deg)',
          2: 'rotateX(-90deg) rotateY(0deg)',
          3: 'rotateX(0deg) rotateY(90deg)',
          4: 'rotateX(0deg) rotateY(-90deg)',
          5: 'rotateX(90deg) rotateY(0deg)',
          6: 'rotateX(180deg) rotateY(0deg)'
        };
        return rotations[value] || rotations[1];
      };
      
      // Reset dice faces to default 1-6 pattern for realistic rolling animation
      const defaultFaces1 = ['1', '6', '3', '4', '5', '2']; // front, back, right, left, top, bottom
      const faces1 = scoreboardDice1.querySelectorAll('.dice-face');
      faces1.forEach((face, idx) => {
        face.setAttribute('data-value', defaultFaces1[idx]);
      });
      
      // Single or double dice mode
      if (value2 === null) {
        scoreboardDice1.classList.add('single');
        scoreboardDice2.style.display = 'none';
      } else {
        scoreboardDice1.classList.remove('single');
        scoreboardDice2.classList.remove('single');
        scoreboardDice2.style.display = 'block';
        
        // Reset dice 2 faces to default pattern
        const faces2 = scoreboardDice2.querySelectorAll('.dice-face');
        faces2.forEach((face, idx) => {
          face.setAttribute('data-value', defaultFaces1[idx]);
        });
      }
      
      // Reset and trigger animation
      scoreboardDice1.style.animation = 'none';
      if (value2 !== null) scoreboardDice2.style.animation = 'none';
      
      setTimeout(() => {
        scoreboardDice1.style.animation = '';
        if (value2 !== null) scoreboardDice2.style.animation = '';
        
        // Just before animation ends, update all faces to show the correct rolled value
        setTimeout(() => {
          // Update all faces of dice 1 to show rolled value
          const faces1 = scoreboardDice1.querySelectorAll('.dice-face');
          faces1.forEach(face => {
            face.setAttribute('data-value', value1.toString());
          });
          
          // Update all faces of dice 2 if double dice mode
          if (value2 !== null) {
            const faces2 = scoreboardDice2.querySelectorAll('.dice-face');
            faces2.forEach(face => {
              face.setAttribute('data-value', value2.toString());
            });
          }
        }, 1800); // Update faces 200ms before animation ends (at 1.8s of 2s animation)
        
        // After animation, set final rotation to show correct face and keep it there
        setTimeout(() => {
          scoreboardDice1.style.transform = getRotation(value1);
          if (value2 !== null) {
            scoreboardDice2.style.transform = getRotation(value2);
          }
          // Animation finished, dice stays at rolled number
          dicePlayerName.textContent = `${playerName} rolled ${value2 !== null ? value1 + ' & ' + value2 : value1}`;

          // Clear animation guard, then run callback and apply any queued state
          console.debug('[live] show3DDiceRoll END â€” clearing isDiceAnimationPlaying and applying queued state');
          isDiceAnimationPlaying = false;

          if (callback) callback();

          if (queuedLiveState) {
            const queued = queuedLiveState;
            queuedLiveState = null;
            // Apply queued state asynchronously to avoid re-entrancy issues
            setTimeout(() => updateUIFromState(queued), 20);
          }
        }, 2000); // Matches 2s animation
      }, 10);
    }

    let demoTick = 0;
    let currentPlayerIndex = 0; // Track which player is moving
    const demoPlayers = [
      { id: "P1", name: "Player 1", pos: 1, drops: 10, eliminated: false },
      { id: "P2", name: "Player 2", pos: 1, drops: 10, eliminated: false },
      { id: "P3", name: "Player 3", pos: 1, drops: 10, eliminated: false },
      { id: "P4", name: "Player 4", pos: 1, drops: 10, eliminated: false }
    ];
    // Updated with actual chance card IDs (1-20)
    const demoChanceIds = [null, "1", null, "6", null, "12", null, "17", null, "20", null, "3", null, "14"];

    // Tile effects: water drop changes per tile
    const tileEffects = {
      1: 0,   // Start Point
      2: -1,  // Sunny Patch
      3: 3,   // Rain Dock
      4: -1,  // Leak Lane
      5: -3,  // Storm Zone
      6: 1,   // Cloud Hill
      7: -4,  // Oil Spill Bay
      8: 0,   // Riverbank Road
      9: 'chance',  // Marsh Land
      10: -3, // Drought Desert
      11: 2,  // Clean Well
      12: -2, // Waste Dump
      13: 'chance', // Sanctuary Stop
      14: -2, // Sewage Drain Street
      15: 1,  // Filter Plant
      16: 'chance', // Mangrove Mile
      17: -2, // Heatwave Road
      18: 4,  // Spring Fountain
      19: 0,  // Eco Garden
      20: 0   // Great Reservoir
    };

    // Chance card effects
    const chanceCardEffects = {
      "1": 2,   // Fixed tap leak
      "2": 2,   // Rainwater harvested
      "3": 1,   // Planted trees
      "4": 1,   // Cool clouds
      "5": 1,   // Cleaned riverbank
      "6": 3,   // Discovered spring
      "7": 1,   // Saved wetland animal
      "8": 1,   // Reused RO water
      "9": 2,   // Bucket instead of shower
      "10": 2,  // Drip irrigation
      "11": 0,  // Skip next penalty
      "12": 0,  // Move forward 2 tiles
      "13": 0,  // Swap positions
      "14": 0,  // Water Shield
      "15": -1, // Left tap running
      "16": -1, // Bottle spilled
      "17": -3, // Pipe burst
      "18": -2, // Heat wave
      "19": -2, // Sewage contamination
      "20": -3  // Flood
    };

    function runDemoStep() {
      if (!demoMode) return;
      demoTick++;
      
      // Get current player, skip if eliminated
      let attempts = 0;
      while (attempts < demoPlayers.length) {
        const currentPlayer = demoPlayers[currentPlayerIndex];
        if (!currentPlayer.eliminated) break;
        currentPlayerIndex = (currentPlayerIndex + 1) % demoPlayers.length;
        attempts++;
      }
      
      // All players eliminated
      if (attempts >= demoPlayers.length) return;
      
      const currentPlayer = demoPlayers[currentPlayerIndex];
      
      // Roll single dice (1-6)
      const diceValue = 1 + Math.floor(Math.random() * 6);
      
      // Store old position to detect lap completion
      const oldPos = currentPlayer.pos;
      
      // Move current player
      const newPos = ((currentPlayer.pos - 1 + diceValue) % 20) + 1;
      currentPlayer.pos = newPos;
      
      // Check if player completed a lap (passed tile 1)
      if (oldPos > newPos || (oldPos + diceValue > 20)) {
        currentPlayer.drops += 5; // Lap bonus
      }
      
      // Show 3D dice animation (no callback needed in demo mode)
      show3DDiceRoll(diceValue, null, currentPlayer.name);
      
      // Apply tile effect
      const tileEffect = tileEffects[newPos];
      let chanceCardId = null;
      
      if (tileEffect === 'chance') {
        // Chance tile - draw random card
        chanceCardId = String(1 + Math.floor(Math.random() * 20));
        const cardEffect = chanceCardEffects[chanceCardId] || 0;
        currentPlayer.drops += cardEffect;
      } else if (typeof tileEffect === 'number') {
        currentPlayer.drops += tileEffect;
      }
      
      // Check for elimination
      if (currentPlayer.drops <= 0) {
        currentPlayer.drops = 0;
        currentPlayer.eliminated = true;
      }
      
      const state = {
        players: demoPlayers,
        lastEvent: {
          playerId: currentPlayer.id,
          playerName: currentPlayer.name,
          dice1: diceValue,
          dice2: null, // Single dice mode
          avg: diceValue,
          tileIndex: newPos,
          chanceCardId
        },
        chanceDescriptions: {
          "1": "You fixed a tap leak (+2 drops)",
          "2": "Rainwater harvested (+2 drops)",
          "3": "You planted two trees (+1 drop)",
          "4": "Cool clouds formed (+1 drop)",
          "5": "You cleaned a riverbank (+1 drop)",
          "6": "Discovered a tiny spring (+3 drops)",
          "7": "You saved a wetland animal (+1 drop)",
          "8": "You reused RO water (+1 drop)",
          "9": "Used bucket instead of shower (+2 drops)",
          "10": "Drip irrigation success (+2 drops)",
          "11": "Skip next penalty",
          "12": "Move forward 2 tiles",
          "13": "Swap positions with next player",
          "14": "Water Shield (next damage = 0)",
          "15": "You left tap running (-1 drop)",
          "16": "Your bottle spilled (-1 drop)",
          "17": "Pipe burst nearby (-3 drops)",
          "18": "Heat wave dries water (-2 drops)",
          "19": "Sewage contamination (-2 drops)",
          "20": "Flood washed away water (-3 drops)"
        }
      };
      
      connStatus.textContent = "OFFLINE";
      connStatus.classList.remove("badge-green");
      connStatus.classList.add("badge-red");
      updateUIFromState(state);
      
      // Move to next player
      currentPlayerIndex = (currentPlayerIndex + 1) % demoPlayers.length;
    }

    setInterval(runDemoStep, 5000); // Increased to 5 seconds for walking animation visibility

    window.addEventListener("load", () => {
      // Initialize connection popup elements
      connectionOverlay = document.getElementById("connectionOverlay");
      connectionIndicator = document.getElementById("connectionIndicator");
      connectionText = document.getElementById("connectionText");
      skipConnection = document.getElementById("skipConnection");
      
      // Initialize reconnection overlay elements
      reconnectionOverlay = document.getElementById("reconnectionOverlay");
      reconnectionText = document.getElementById("reconnectionText");
      retryAttemptEl = document.getElementById("retryAttempt");
      retryDelayEl = document.getElementById("retryDelay");
      reconnectionInfo = document.getElementById("reconnectionInfo");
      
      // Initialize loading overlay
      loadingOverlay = document.getElementById("loadingOverlay");
      
      // Initialize last updated element
      lastUpdatedEl = document.getElementById("lastUpdated");

      // Initialize disclaimer elements
      const smallScreenDisclaimer = document.getElementById("smallScreenDisclaimer");
      const dismissDisclaimer = document.getElementById("dismissDisclaimer");

      // Initialize dice elements
      diceDisplay = document.getElementById("diceDisplay");
      dice1El = document.getElementById("dice1");
      dice2El = document.getElementById("dice2");
      
      // Initialize scoreboard 3D dice elements
      scoreboardDiceDisplay = document.getElementById("scoreboardDiceDisplay");
      scoreboardDice1 = document.getElementById("scoreboardDice1");
      scoreboardDice2 = document.getElementById("scoreboardDice2");
      dicePlayerName = document.getElementById("dicePlayerName");

      // Show disclaimer first on small screens, then connection popup
      if (window.innerWidth <= 768) {
        smallScreenDisclaimer.classList.remove("hidden");
        
        dismissDisclaimer?.addEventListener('click', () => {
          smallScreenDisclaimer.classList.add("hidden");
          showConnectionPopup();
        });
      } else {
        showConnectionPopup();
      }

      skipConnection?.addEventListener('click', () => {
        demoMode = true;
        hideConnectionPopup();
        // Don't call fetchAndUpdate in demo mode, demo is already running
      });
      
      initTiles();
      computeTileCenters();
      // Start in top view
      const zoom = parseInt(zoomRange.value, 10) / 100;
      boardWrapper.style.transform = `scale(${zoom})`;
      board3d.style.transform = "rotateX(0deg) rotateY(0deg) rotateZ(0deg)";
      // Demo will start via interval, don't call runDemoStep() here
      
      // Network health monitoring
      let isOnline = navigator.onLine;
      
      window.addEventListener('online', () => {
        isOnline = true;
        console.log('Network connection restored');
        connStatus.textContent = 'ONLINE';
        connStatus.classList.remove('badge-red');
        connStatus.classList.add('badge-green');
        
        // Resume polling if not in demo mode
        if (!demoMode) {
          startLivePolling();
          fetchAndUpdate();  // Immediate fetch on reconnect
        }
      });
      
      window.addEventListener('offline', () => {
        isOnline = false;
        console.log('Network connection lost');
        connStatus.textContent = 'OFFLINE';
        connStatus.classList.remove('badge-green');
        connStatus.classList.add('badge-red');
        
        // Show reconnection overlay
        showReconnectionOverlay(0, null);
      });
    });
  </script>
</body>
</html>
</script>
<script>
  // Mode toggles and helpers
  const kidToggle = document.getElementById('kidToggle');
  const devToggle = document.getElementById('devToggle');
  const helpBtn   = document.getElementById('helpBtn');
  const kidStatus = document.getElementById('kidStatus');
  const devStatus = document.getElementById('devStatus');
  const tutorialOverlay = document.getElementById('tutorialOverlay');
  const closeTutorial = document.getElementById('closeTutorial');
  const toastContainer = document.getElementById('toastContainer');

  function showToast(msg) {
    const el = document.createElement('div');
    el.className = 'toast';
    el.textContent = msg;
    toastContainer.appendChild(el);
    setTimeout(() => el.remove(), 2500);
  }

  kidToggle?.addEventListener('click', () => {
    document.body.classList.toggle('kid-mode');
    const on = document.body.classList.contains('kid-mode');
    kidStatus.textContent = on ? 'ON' : 'OFF';
    showToast(on ? 'Kid Mode enabled' : 'Kid Mode disabled');
  });

  devToggle?.addEventListener('click', () => {
    document.body.classList.toggle('dev-mode');
    const on = document.body.classList.contains('dev-mode');
    devStatus.textContent = on ? 'ON' : 'OFF';
    showToast(on ? 'Dev Controls visible' : 'Dev Controls hidden');
  });

  helpBtn?.addEventListener('click', () => {
    tutorialOverlay.classList.remove('hidden');
  });
  closeTutorial?.addEventListener('click', () => {
    tutorialOverlay.classList.add('hidden');
  });

  // Snap animation hook: wrap existing positionToken
  const originalPositionToken = window.positionToken;
  if (typeof originalPositionToken === 'function') {
    window.positionToken = function(playerId, tileIndex, offsetIndex) {
      originalPositionToken(playerId, tileIndex, offsetIndex);
      const token = tokensByPlayerId[playerId];
      if (token) {
        token.classList.add('token-moving');
        setTimeout(() => token.classList.remove('token-moving'), 250);
      }
    };
  }
</script>
<script>
  const settingsBtn = document.getElementById('settingsBtn');
  const settingsPanel = document.getElementById('settingsPanel');
  const settingsBackdrop = document.getElementById('settingsBackdrop');
  const closeSettings = document.getElementById('closeSettings');
  
  settingsBtn?.addEventListener('click', () => {
    settingsPanel.classList.toggle('hidden');
    settingsBackdrop.classList.toggle('hidden');
  });
  
  closeSettings?.addEventListener('click', () => {
    settingsPanel.classList.add('hidden');
    settingsBackdrop.classList.add('hidden');
  });
  
  // Close when clicking on backdrop
  settingsBackdrop?.addEventListener('click', () => {
    settingsPanel.classList.add('hidden');
    settingsBackdrop.classList.add('hidden');
  });

  // Replay Share Popup Functions
  function showReplaySharePopup(shareUrl, replayId) {
    let popup = document.getElementById('replaySharePopup');
    if (!popup) {
      popup = document.createElement('div');
      popup.id = 'replaySharePopup';
      popup.style.cssText = 'position:fixed;top:50%;left:50%;transform:translate(-50%,-50%);background:var(--card-bg);padding:30px;border-radius:16px;box-shadow:0 8px 32px rgba(0,0,0,0.6);z-index:10000;display:none;max-width:500px;width:90%;';
      popup.innerHTML = `
        <h3 style="color:var(--accent);margin:0 0 15px 0;">🎮 Game Replay Saved!</h3>
        <p style="color:var(--text-main);margin-bottom:15px;">Watch this game again or share it with friends:</p>
        <input type="text" readonly value="${shareUrl}" id="replayShareUrl" style="width:100%;padding:12px;background:var(--tile-bg);border:1px solid var(--tile-border);color:var(--text-main);border-radius:8px;margin-bottom:15px;font-family:monospace;" />
        <div style="display:flex;gap:10px;justify-content:center;">
          <button onclick="copyReplayUrl()" style="flex:1;padding:12px;background:var(--accent);color:#000;border:none;border-radius:8px;cursor:pointer;font-weight:600;">📋 Copy Link</button>
          <button onclick="openReplay()" style="flex:1;padding:12px;background:var(--success);color:#000;border:none;border-radius:8px;cursor:pointer;font-weight:600;">👁️ Watch Now</button>
          <button onclick="closeReplayPopup()" style="flex:0.5;padding:12px;background:var(--danger);color:#fff;border:none;border-radius:8px;cursor:pointer;">✖</button>
        </div>
      `;
      document.body.appendChild(popup);
    }
    const urlInput = popup.querySelector('#replayShareUrl');
    if (urlInput) urlInput.value = shareUrl;
    popup.style.display = 'block';
  }

  window.copyReplayUrl = function() {
    const urlInput = document.getElementById('replayShareUrl');
    if (urlInput) {
      urlInput.select();
      document.execCommand('copy');
      const copyBtn = document.querySelector('button[onclick="copyReplayUrl()"]');
      if (copyBtn) {
        const orig = copyBtn.textContent;
        copyBtn.textContent = '✓ Copied!';
        setTimeout(() => { copyBtn.textContent = orig; }, 2000);
      }
    }
  };

  window.openReplay = function() {
    const urlInput = document.getElementById('replayShareUrl');
    if (urlInput) window.open(urlInput.value, '_blank');
  };

  window.closeReplayPopup = function() {
    const popup = document.getElementById('replaySharePopup');
    if (popup) popup.style.display = 'none';
  };

