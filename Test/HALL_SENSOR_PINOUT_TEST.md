# A3144 Hall Sensor Pinout Testing

## Safe Method - Using Multimeter

### Step 1: Identify the correct pinout

Looking at the A3144 from the **FLAT FACE** side with pins pointing DOWN:
```
    ___
   |   |  <- Flat/marked side
 1 | 2 | 3
```

Standard pinout should be:
- Pin 1 (left): VCC
- Pin 2 (middle): GND  
- Pin 3 (right): OUT

### Step 2: Test with Multimeter

1. **Connect sensor to power first:**
   - Pin 1 → 3.3V
   - Pin 2 → GND
   - Pin 3 → Leave floating (not connected)

2. **Set multimeter to DC Voltage mode**

3. **Measure Pin 3 (OUT) voltage:**
   - Black probe → GND
   - Red probe → Pin 3
   - **Without magnet:** Should read ~3.3V (HIGH)
   - **With magnet (south pole):** Should read ~0V (LOW)

4. **If it works:** Pinout is correct, connect Pin 3 to GPIO27

5. **If it doesn't work:** Try flipping the sensor 180° and repeat

## Alternative: Visual Check

Some A3144 sensors have markings:
- Look for "A3144" text on the sensor
- The marking side is usually the FRONT
- Pins are typically numbered LEFT to RIGHT when viewing from front

## Why NOT to reverse connections:

Connecting OUT pin to VCC would:
- Damage the Hall sensor's output transistor
- Potentially damage ESP32 GPIO pin
- Short circuit when magnet is present
