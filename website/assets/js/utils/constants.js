/**
 * Constants - Game configuration and data
 * Extracted from live.html/demo.html for reusability
 */

// Tile names for the 20-tile board
export const TILE_NAMES = [
  "LAUNCH PAD", "NATURE GUARDIAN", "POLLUTING FACTORY", "FLOWER GARDEN", "TREE CUTTING",
  "MARSH SWAMP", "RECYCLED WATER", "WASTED WATER", "RIVER ROBBER", "LILLY POND",
  "SANCTUARY COVE", "SHRINKING LAKE", "CRYSTAL GLACIER", "DRY CITY", "RAIN HARVEST",
  "MANGROVE TRAIL", "WASTED WELL", "EVERGREEN FOREST", "PLANT GROWER", "DIRTY WATER LANE"
];

// Tile effects: water drop changes per tile
export const TILE_EFFECTS = {
  1: 0,   // Launch Pad (START: +10 start, +5 pass)
  2: 1,   // Nature Guardian (SHIELD: +1 & Immunity)
  3: -2,  // Polluting Factory (LOSS: -2)
  4: 1,   // Flower Garden (ECO SAVE: +1)
  5: -3,  // Tree Cutting (GREAT CRISIS: -3)
  6: 'chance',  // Marsh Swamp (MYSTERY: Chance Card)
  7: 3,   // Recycled Water (MIGHTY SAVE: +3)
  8: -1,  // Wasted Water (LOSS: -1)
  9: -5,  // River Robber (GREAT CRISIS: -5)
  10: 1,  // Lilly Pond (ECO SAVE: +1)
  11: 'chance', // Sanctuary Cove (MYSTERY: Chance Card)
  12: -4, // Shrinking Lake (GREAT CRISIS: -4)
  13: 2,  // Crystal Glacier (ECO SAVE: +2)
  14: -2, // Dry City (LOSS: -2)
  15: 2,  // Rain Harvest (ECO SAVE: +2)
  16: 'chance', // Mangrove Trail (MYSTERY: Chance Card)
  17: -2, // Wasted Well (LOSS: -2)
  18: 4,  // Evergreen Forest (MIGHTY SAVE: +4)
  19: 1,  // Plant Grower (SHIELD: +1)
  20: -2  // Dirty Water Lane (LOSS: -2)
};

// Chance card effects (water drop changes)
export const CHANCE_CARD_EFFECTS = {
  "1": 2,   // Fixed tap leak
  "2": 2,   // Rain harvested
  "3": 1,   // Planted trees
  "4": 1,   // Clouds formed
  "5": 2,   // Preserved riverbank
  "6": 2,   // Cleaned well
  "7": 1,   // Saved plant
  "8": 1,   // Recycled water
  "9": 2,   // Bucket bath
  "10": 2,  // Drip irrigation
  "11": 0,  // Skip penalty (Immunity)
  "12": 0,  // Move forward 2
  "13": 0,  // Swap with next (next player plays twice)
  "14": 0,  // Water Shield (Immunity)
  "15": -1, // Left tap running
  "16": -1, // Bottle spilled
  "17": -3, // Pipe burst
  "18": -2, // Climate dries water
  "19": -2, // Sewage contamination
  "20": -3  // Wasted papers
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
