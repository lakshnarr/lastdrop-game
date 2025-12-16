# LED Strip Troubleshooting Guide

## Step-by-Step Connection Check

### Step 1: Power Supply Check
**What to check:**
- [ ] Is the 5V power supply plugged in and turned ON?
- [ ] Measure voltage between +5V and GND with multimeter
  - Expected: 4.8V - 5.2V
  - If 0V: Power supply issue
  - If wrong voltage: Bad power supply

**How to test:** Use multimeter in DC voltage mode, red probe to +5V, black to GND

---

### Step 2: LED Strip Power Check
**What to check:**
- [ ] Is LED strip +5V connected to power supply +5V?
- [ ] Is LED strip GND connected to power supply GND?
- [ ] Measure voltage at LED strip power pads
  - Expected: 4.8V - 5.2V

**Common mistake:** Reversed polarity (swap + and -)

---

### Step 3: ESP32 Power Check
**What to check:**
- [ ] Is ESP32 powered via USB?
- [ ] Does ESP32 board LED light up?
- [ ] Serial Monitor shows output? (. .\config.ps1; Start-ESP32Monitor)

**Expected Serial Output:**
```
=== LED Strip Hardware Test ===
LED Pin: GPIO 18
Total LEDs: 80
LED strip initialized

Test 1: All LEDs WHITE
Test 2: All LEDs RED
...
```

---

### Step 4: Common Ground Check (CRITICAL!)
**What to check:**
- [ ] ESP32 GND pin connected to AHCT125 pin 7 (GND)?
- [ ] AHCT125 pin 7 (GND) connected to LED strip GND?
- [ ] LED strip GND connected to power supply GND?
- [ ] ALL grounds must be connected together!

**Test:** Continuity mode on multimeter
- Touch one probe to ESP32 GND pin
- Touch other probe to LED strip GND pad
- Multimeter should beep (continuity exists)

**Common mistake:** Forgetting to connect ESP32 GND to the power supply ground

---

### Step 5: AHCT125 Level Shifter Power
**What to check:**
- [ ] AHCT125 pin 14 (VCC) connected to +5V?
- [ ] AHCT125 pin 7 (GND) connected to common GND?
- [ ] AHCT125 pin 1 (1OE) connected to GND? (enables output)
- [ ] 0.1µF ceramic capacitor between pin 14 and pin 7?

**Test:** Measure voltage between AHCT125 pin 14 and pin 7
- Expected: 4.8V - 5.2V

---

### Step 6: AHCT125 Input Signal
**What to check:**
- [ ] ESP32 GPIO 18 connected to AHCT125 pin 2 (1A)?
- [ ] Connection is secure (no loose wires)?

**Test:** While ESP32 is running test sketch, measure voltage at AHCT125 pin 2
- Should fluctuate between 0V - 3.3V (data signal)
- If stuck at 0V or 3.3V: No data from ESP32

---

### Step 7: AHCT125 Output Signal
**What to check:**
- [ ] AHCT125 pin 3 (1Y) connected to 330Ω resistor?
- [ ] 330Ω resistor connected to LED strip DIN?

**Test:** Measure voltage at AHCT125 pin 3 (1Y) while test running
- Should fluctuate between 0V - 5V (shifted signal)
- If 0V always: Level shifter not working or 1OE not grounded
- If 5V always: No input signal

---

### Step 8: LED Strip Data Line
**What to check:**
- [ ] LED strip has 3 wires: +5V, GND, DIN (Data In)
- [ ] 330Ω resistor output connected to DIN?
- [ ] Is this the FIRST LED's DIN, not DOUT?

**Common mistake:** WS2812B strips have direction arrows → data flows in one direction only

---

### Step 9: Capacitor Check
**What to check:**
- [ ] 1000µF capacitor: Long leg (+) to +5V, Short leg with stripe (-) to GND
- [ ] Capacitor is physically near LED strip power input
- [ ] 0.1µF ceramic capacitor near AHCT125 chip

**Common mistake:** Reversed electrolytic capacitor polarity (will not work or explode!)

---

## Quick Visual Inspection

### Check these common mistakes:
1. **Wrong GPIO pin on ESP32**
   - Should be GPIO 18 (not GPIO 5 or others)
   - Check pin label on ESP32 board

2. **Level shifter chip orientation**
   - Pin 1 has a dot or notch on chip
   - Pin 1 = 1OE (should be grounded)
   - Count pins correctly!

3. **Loose breadboard connections**
   - Push all wires firmly into breadboard
   - Check for broken/bent breadboard springs

4. **Wire breaks**
   - Gently tug each wire to check connection
   - Look for frayed or damaged wire insulation

5. **Power supply capacity**
   - 80 LEDs at full brightness = 80 × 60mA = 4.8A
   - Need at least 5A power supply
   - Test with low brightness (50%) first

---

## Simplified Test Setup

If nothing works, try this minimal setup:

### Ultra-Simple Test (No Level Shifter):
**WARNING:** This may damage ESP32 or LEDs long-term, use only for testing!

1. Connect ONLY FIRST 10 LEDs (cut strip or use smaller strip)
2. ESP32 GPIO 18 → 330Ω resistor → LED strip DIN **directly** (no AHCT125)
3. LED strip +5V → Power supply +5V
4. LED strip GND → Power supply GND **AND** ESP32 GND
5. Upload test sketch
6. Set brightness to 20% in code: `strip.setBrightness(20);`

**If this works:** Problem is with level shifter wiring
**If this doesn't work:** Check power supply and ground connection

---

## Multimeter Test Values

| Test Point | Expected Voltage | What it Means |
|------------|-----------------|---------------|
| Power +5V to GND | 4.8V - 5.2V | Power good |
| LED strip +5V to GND | 4.8V - 5.2V | LED power good |
| AHCT125 pin 14 to pin 7 | 4.8V - 5.2V | Shifter powered |
| ESP32 GPIO 18 (active) | 0V - 3.3V fluctuating | Signal present |
| AHCT125 pin 3 (active) | 0V - 5V fluctuating | Shifted signal OK |

---

## Serial Monitor Output

Run this command to see ESP32 debug output:
```powershell
. .\config.ps1; Start-ESP32Monitor
```

**Expected output every 15 seconds:**
```
Test 1: All LEDs WHITE
Test 2: All LEDs RED
Test 3: All LEDs GREEN
Test 4: All LEDs BLUE
Test 5: Running dot
Test 6: Rainbow
Clearing...
```

**If you see this output:** ESP32 is working correctly, problem is in wiring to LEDs

---

## Next Steps

1. Start with **Step 1** and check each item
2. Use multimeter to verify voltages
3. Take a photo of your wiring and check against this list
4. If still not working after all checks, try the **Ultra-Simple Test**
