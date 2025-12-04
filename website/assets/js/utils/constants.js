/**
 * Constants - Game configuration and data
 * Extracted from live.html/demo.html for reusability
 */

// Tile names for the 20-tile board
export const TILE_NAMES = [
  "START", "Sunny Patch", "Rain Dock", "Leak Lane", "Storm Zone",
  "Cloud Hill", "Oil Spill Bay", "Riverbank Road", "Marsh Land", "Drought Desert",
  "Clean Well", "Waste Dump", "Sanctuary Stop", "Sewage Drain Street", "Filter Plant",
  "Mangrove Mile", "Heatwave Road", "Spring Fountain", "Eco Garden", "Great Reservoir"
];

// Tile effects: water drop changes per tile
export const TILE_EFFECTS = {
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

// Chance card effects (water drop changes)
export const CHANCE_CARD_EFFECTS = {
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

// Board tile layout (grid positions)
export const BOARD_GRID = [
  1,2,3,4,5,6, 
  20,0,0,0,0,7, 
  19,0,0,0,0,8, 
  18,0,0,0,0,9, 
  17,0,0,0,0,10, 
  16,15,14,13,12,11
];

// Board sequence (tile order for movement)
export const BOARD_SEQUENCE = [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20];

// Player color mapping
export const PLAYER_COLORS = {
  red: '#ef4444',
  green: '#22c55e',
  blue: '#60a5fa',
  yellow: '#eab308',
  orange: '#f97316',
  black: '#1f2937'
};

// Player pawn images
export const PAWN_IMAGES = [
  "/assets/pawns/pawn-red.svg",
  "/assets/pawns/pawn-green.svg",
  "/assets/pawns/pawn-blue.svg",
  "/assets/pawns/pawn-yellow.svg"
];

// Player dot gradients for scoreboard
export const PLAYER_GRADIENTS = [
  "radial-gradient(circle at 30% 20%, #fecaca, #b91c1c)",
  "radial-gradient(circle at 30% 20%, #a7f3d0, #16a34a)",
  "radial-gradient(circle at 30% 20%, #bfdbfe, #1d4ed8)",
  "radial-gradient(circle at 30% 20%, #fed7aa, #ea580c)"
];

// Audio volume defaults
export const DEFAULT_AUDIO_VOLUMES = {
  bgMusic: 0.3,
  dice: 0.5,
  move: 0.4,
  chance: 0.5,
  tile: 0.4,
  eliminated: 0.6,
  winner: 0.7
};

// Sound frequencies for audio generator
export const SOUND_FREQUENCIES = {
  dice: 440,
  move: 523,
  chance: 659,
  tile: 392,
  eliminated: 293,
  winner: 880
};

// API configuration
export const API_CONFIG = {
  liveStateUrl: '/api/live_state.php?key=ABC123',
  livePushUrl: '/api/live_push.php',
  activeGamesUrl: '/api/active_games.php?key=ABC123',
  sessionInfoUrl: '/api/session_info.php?key=ABC123',
  pollingInterval: 2000, // ms
  maxRetries: 5,
  retryDelays: [2000, 4000, 8000, 16000, 32000] // Exponential backoff
};

// Animation durations (ms)
export const ANIMATION_DURATIONS = {
  diceRoll: 2000,
  tokenStep: 600,
  chanceCardDisplay: 4500,
  winnerOverlay: 5000
};
