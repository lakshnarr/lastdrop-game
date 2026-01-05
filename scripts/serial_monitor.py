import serial
import time
import sys

PORT = 'COM10'
BAUD = 115200

print(f"Serial Monitor - {PORT} @ {BAUD} baud")
print("Auto-reconnect enabled. Press Ctrl+C to exit.\n")

while True:
    try:
        ser = serial.Serial(PORT, BAUD, timeout=1)
        print(f"✓ Connected to {PORT}")
        print("=" * 60)
        
        while True:
            if ser.in_waiting > 0:
                line = ser.readline().decode('utf-8', errors='ignore').strip()
                print(line)
                sys.stdout.flush()
            time.sleep(0.01)
            
    except serial.SerialException as e:
        print(f"\n✗ Disconnected: {e}")
        print("Reconnecting in 2 seconds...")
        time.sleep(2)
    except KeyboardInterrupt:
        print("\n\nExiting...")
        if 'ser' in locals() and ser.is_open:
            ser.close()
        break
