/**
 * DiceAnimator - Handles all 3D dice animations and rendering
 * Extracted from live.html/demo.html to create modular, reusable code
 */

export class DiceAnimator {
  constructor(options = {}) {
    // DOM elements
    this.scoreboardDiceDisplay = options.scoreboardDiceDisplay;
    this.scoreboardDice1 = options.scoreboardDice1;
    this.scoreboardDice2 = options.scoreboardDice2;
    this.dicePlayerName = options.dicePlayerName;
    this.soundManager = options.soundManager; // Optional audio manager
    
    // Animation state
    this.isDiceAnimationPlaying = false;
    this.currentDiceColor = '#60a5fa'; // Default blue
    
    // Color mapping
    this.colorMap = {
      'red': '#ef4444',
      'green': '#22c55e',
      'blue': '#60a5fa',
      'yellow': '#eab308',
      'orange': '#f97316',
      'black': '#1f2937'
    };
    
    // Rotation mapping for dice faces
    this.rotations = {
      1: 'rotateX(0deg) rotateY(0deg)',
      2: 'rotateX(-90deg) rotateY(0deg)',
      3: 'rotateX(0deg) rotateY(90deg)',
      4: 'rotateX(0deg) rotateY(-90deg)',
      5: 'rotateX(90deg) rotateY(0deg)',
      6: 'rotateX(180deg) rotateY(0deg)'
    };
  }
  
  /**
   * Check if dice animation is currently playing
   */
  isAnimating() {
    return this.isDiceAnimationPlaying;
  }
  
  /**
   * Set dice dots to a single color
   */
  setDiceDotsColor(colorName) {
    const hexColor = this.colorMap[colorName] || colorName;
    this.currentDiceColor = hexColor;
    
    const allDots = document.querySelectorAll('.dice-dot');
    allDots.forEach(dot => {
      dot.style.backgroundColor = hexColor;
    });
  }
  
  /**
   * Set individual colors for each die (for 2-dice mode)
   */
  setIndividualDiceColors(color1, color2 = null) {
    const hexColor1 = this.colorMap[color1] || color1 || '#60a5fa';
    const hexColor2 = color2 ? (this.colorMap[color2] || color2) : hexColor1;
    
    // Update dice 1 dots
    if (this.scoreboardDice1) {
      const dice1Dots = this.scoreboardDice1.querySelectorAll('.dice-dot');
      dice1Dots.forEach(dot => {
        dot.style.backgroundColor = hexColor1;
      });
    }
    
    // Update dice 2 dots
    if (this.scoreboardDice2) {
      const dice2Dots = this.scoreboardDice2.querySelectorAll('.dice-dot');
      dice2Dots.forEach(dot => {
        dot.style.backgroundColor = hexColor2;
      });
    }
  }
  
  /**
   * Get rotation transform for a given dice value
   */
  getRotation(value) {
    return this.rotations[value] || this.rotations[1];
  }
  
  /**
   * Show rolling dice animation (for continuous rolling state)
   */
  showRollingDice({
    playerName = "Player",
    diceColor1 = null,
    diceColor2 = null,
    dice1Rolling = true,
    dice2Rolling = true,
    value1 = null,
    value2 = null
  }) {
    if (!this.scoreboardDiceDisplay || !this.scoreboardDice1 || !this.scoreboardDice2 || !this.dicePlayerName) {
      return;
    }

    // Update dice colors if provided
    if (diceColor1 && diceColor2) {
      this.setIndividualDiceColors(diceColor1, diceColor2);
    } else if (diceColor1) {
      this.setDiceDotsColor(diceColor1);
    }

    // Update player name text based on which dice are rolling
    if (dice1Rolling && dice2Rolling) {
      this.dicePlayerName.textContent = `${playerName} is rolling both dice...`;
    } else if (dice1Rolling) {
      this.dicePlayerName.textContent = `${playerName} - die 1 rolling...`;
    } else if (dice2Rolling) {
      this.dicePlayerName.textContent = `${playerName} - die 2 rolling...`;
    } else {
      this.dicePlayerName.textContent = `${playerName} is rolling...`;
    }

    // Make both dice visible during rolling
    this.scoreboardDiceDisplay.style.display = 'block';
    this.scoreboardDice1.classList.remove('single');
    this.scoreboardDice2.classList.remove('single');
    this.scoreboardDice2.style.display = 'block';

    // Handle Die 1
    if (dice1Rolling === false && value1 != null) {
      // Die 1 has stopped: show its final face, no animation
      const faces1 = this.scoreboardDice1.querySelectorAll('.dice-face');
      faces1.forEach(face => {
        face.setAttribute('data-value', value1.toString());
      });
      this.scoreboardDice1.style.animation = 'none';
      this.scoreboardDice1.style.transform = this.getRotation(value1);
    } else if (dice1Rolling !== false) {
      // Die 1 still rolling
      this.scoreboardDice1.style.animation = 'dice-roll 0.5s linear infinite';
    } else {
      this.scoreboardDice1.style.animation = 'none';
    }

    // Handle Die 2
    if (value2 == null && !dice2Rolling) {
      // Single-die mode: hide second die
      this.scoreboardDice2.style.display = 'none';
      this.scoreboardDice2.style.animation = 'none';
    } else if (dice2Rolling === false && value2 != null) {
      // Die 2 has stopped: show its final face, no animation
      const faces2 = this.scoreboardDice2.querySelectorAll('.dice-face');
      faces2.forEach(face => {
        face.setAttribute('data-value', value2.toString());
      });
      this.scoreboardDice2.style.animation = 'none';
      this.scoreboardDice2.style.transform = this.getRotation(value2);
    } else if (dice2Rolling !== false) {
      // Die 2 still rolling
      this.scoreboardDice2.style.animation = 'dice-roll 0.5s linear infinite';
    } else {
      this.scoreboardDice2.style.animation = 'none';
    }
  }
  
  /**
   * Show static dice (no animation, just display final values)
   */
  showStaticDice({
    value1,
    value2 = null,
    playerName = "Player",
    diceColor1 = null,
    diceColor2 = null
  }) {
    if (!this.scoreboardDiceDisplay || !this.scoreboardDice1 || !this.scoreboardDice2 || !this.dicePlayerName) {
      return;
    }
    
    // Update dice colors if provided
    if (diceColor1 && diceColor2) {
      this.setIndividualDiceColors(diceColor1, diceColor2);
    } else if (diceColor1) {
      this.setDiceDotsColor(diceColor1);
    }
    
    // Update player name text
    this.dicePlayerName.textContent = `${playerName} rolled ${value2 !== null ? value1 + ' & ' + value2 : value1}`;
    
    // Set dice faces to show final values
    const faces1 = this.scoreboardDice1.querySelectorAll('.dice-face');
    faces1.forEach(face => {
      face.setAttribute('data-value', value1.toString());
    });
    
    // Single or double dice mode
    if (value2 === null) {
      this.scoreboardDice1.classList.add('single');
      this.scoreboardDice2.style.display = 'none';
    } else {
      this.scoreboardDice1.classList.remove('single');
      this.scoreboardDice2.classList.remove('single');
      this.scoreboardDice2.style.display = 'block';
      
      // Set dice 2 faces to show final value
      const faces2 = this.scoreboardDice2.querySelectorAll('.dice-face');
      faces2.forEach(face => {
        face.setAttribute('data-value', value2.toString());
      });
    }
    
    // Remove any animation and set final rotation immediately
    this.scoreboardDice1.style.animation = 'none';
    this.scoreboardDice1.style.transform = this.getRotation(value1);
    if (value2 !== null) {
      this.scoreboardDice2.style.animation = 'none';
      this.scoreboardDice2.style.transform = this.getRotation(value2);
    }
  }
  
  /**
   * Show 3D dice roll animation with callback
   */
  show3DDiceRoll({
    value1,
    value2 = null,
    playerName = "Player",
    callback = null,
    diceColor1 = null,
    diceColor2 = null
  }) {
    if (!this.scoreboardDiceDisplay || !this.scoreboardDice1 || !this.scoreboardDice2 || !this.dicePlayerName) {
      if (typeof callback === 'function') callback();
      return;
    }
    
    // Defensive: if no dice value provided, do not play the animation
    if (value1 === null || value1 === undefined) {
      if (typeof callback === 'function') callback();
      return;
    }
    
    // Mark that animation is playing
    console.debug('[DiceAnimator] show3DDiceRoll START — animation playing', value1, value2, playerName);
    this.isDiceAnimationPlaying = true;
    
    // Update dice colors if provided
    if (diceColor1 && diceColor2) {
      this.setIndividualDiceColors(diceColor1, diceColor2);
    } else if (diceColor1) {
      this.setDiceDotsColor(diceColor1);
    }
    
    // Play dice roll sound
    if (this.soundManager && typeof this.soundManager.play === 'function') {
      this.soundManager.play('dice');
    }
    
    // Update player name text
    this.dicePlayerName.textContent = `${playerName} rolling dice...`;
    
    // Reset dice faces to default 1-6 pattern for realistic rolling animation
    const defaultFaces1 = ['1', '6', '3', '4', '5', '2']; // front, back, right, left, top, bottom
    const faces1 = this.scoreboardDice1.querySelectorAll('.dice-face');
    faces1.forEach((face, idx) => {
      face.setAttribute('data-value', defaultFaces1[idx]);
    });
    
    // Single or double dice mode
    if (value2 === null) {
      this.scoreboardDice1.classList.add('single');
      this.scoreboardDice2.style.display = 'none';
    } else {
      this.scoreboardDice1.classList.remove('single');
      this.scoreboardDice2.classList.remove('single');
      this.scoreboardDice2.style.display = 'block';
      
      // Reset dice 2 faces to default pattern
      const faces2 = this.scoreboardDice2.querySelectorAll('.dice-face');
      faces2.forEach((face, idx) => {
        face.setAttribute('data-value', defaultFaces1[idx]);
      });
    }
    
    // Reset and trigger animation
    this.scoreboardDice1.style.animation = 'none';
    if (value2 !== null) this.scoreboardDice2.style.animation = 'none';
    
    setTimeout(() => {
      this.scoreboardDice1.style.animation = '';
      if (value2 !== null) this.scoreboardDice2.style.animation = '';
      
      // Just before animation ends, update all faces to show the correct rolled value
      setTimeout(() => {
        // Update all faces of dice 1 to show rolled value
        const faces1 = this.scoreboardDice1.querySelectorAll('.dice-face');
        faces1.forEach(face => {
          face.setAttribute('data-value', value1.toString());
        });
        
        // Update all faces of dice 2 if double dice mode
        if (value2 !== null) {
          const faces2 = this.scoreboardDice2.querySelectorAll('.dice-face');
          faces2.forEach(face => {
            face.setAttribute('data-value', value2.toString());
          });
        }
      }, 1800); // Update faces 200ms before animation ends (at 1.8s of 2s animation)
      
      // After animation, set final rotation to show correct face and keep it there
      setTimeout(() => {
        this.scoreboardDice1.style.transform = this.getRotation(value1);
        if (value2 !== null) {
          this.scoreboardDice2.style.transform = this.getRotation(value2);
        }
        
        // Animation finished, dice stays at rolled number
        this.dicePlayerName.textContent = `${playerName} rolled ${value2 !== null ? value1 + ' & ' + value2 : value1}`;

        // Clear animation guard
        console.debug('[DiceAnimator] show3DDiceRoll END — animation complete');
        this.isDiceAnimationPlaying = false;

        // Execute callback if provided
        if (typeof callback === 'function') {
          callback();
        }
      }, 2000); // Total animation duration
    }, 10);
  }
  
  /**
   * Hide dice display
   */
  hide() {
    if (this.scoreboardDiceDisplay) {
      this.scoreboardDiceDisplay.style.display = 'none';
    }
  }
  
  /**
   * Show dice display
   */
  show() {
    if (this.scoreboardDiceDisplay) {
      this.scoreboardDiceDisplay.style.display = 'block';
    }
  }
}
