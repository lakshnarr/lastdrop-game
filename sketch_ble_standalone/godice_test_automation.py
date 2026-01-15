#!/usr/bin/env python3
"""
GoDice ESP32 Test Automation Script
Monitors Serial output and validates test results automatically
"""

import serial
import time
import sys
import re
from datetime import datetime

class GoDiceTestValidator:
    def __init__(self, port, baudrate=115200):
        self.port = port
        self.baudrate = baudrate
        self.serial = None
        self.test_results = {}
        self.dice_info = {}
        
    def connect(self):
        """Connect to ESP32 serial port"""
        try:
            self.serial = serial.Serial(self.port, self.baudrate, timeout=1)
            print(f"✓ Connected to {self.port} at {self.baudrate} baud")
            time.sleep(2)  # Wait for ESP32 boot
            return True
        except serial.SerialException as e:
            print(f"✗ Failed to connect: {e}")
            return False
    
    def read_line(self):
        """Read one line from serial"""
        try:
            line = self.serial.readline().decode('utf-8', errors='ignore').strip()
            return line
        except:
            return ""
    
    def wait_for_pattern(self, pattern, timeout=10):
        """Wait for specific pattern in serial output"""
        start_time = time.time()
        while time.time() - start_time < timeout:
            line = self.read_line()
            if line:
                print(f"  {line}")
                if re.search(pattern, line):
                    return line
        return None
    
    def test_boot(self):
        """TEST 1: Verify boot banner appears"""
        print("\n" + "="*60)
        print("TEST 1: Boot Test")
        print("="*60)
        
        result = self.wait_for_pattern(r"GoDice ESP32 Integration Test", timeout=5)
        if result:
            print("✓ Boot banner detected")
            self.test_results['boot'] = 'PASS'
            return True
        else:
            print("✗ Boot banner not detected")
            self.test_results['boot'] = 'FAIL'
            return False
    
    def test_scan(self):
        """TEST 2: Verify BLE scanning starts"""
        print("\n" + "="*60)
        print("TEST 2: BLE Scan Test")
        print("="*60)
        
        result = self.wait_for_pattern(r"Starting scan for GoDice", timeout=5)
        if result:
            print("✓ Scan started")
            self.test_results['scan'] = 'PASS'
            return True
        else:
            print("✗ Scan did not start")
            self.test_results['scan'] = 'FAIL'
            return False
    
    def test_connection(self):
        """TEST 3: Verify dice connection"""
        print("\n" + "="*60)
        print("TEST 3: Connection Test")
        print("="*60)
        print("⏳ Waiting for dice connection (up to 30 seconds)...")
        
        # Wait for "Found GoDice" message
        found = self.wait_for_pattern(r"Found GoDice", timeout=30)
        if not found:
            print("✗ No dice found during scan")
            self.test_results['connection'] = 'FAIL'
            return False
        
        # Extract MAC address
        mac_match = re.search(r'([0-9A-F]{2}:[0-9A-F]{2}:[0-9A-F]{2}:[0-9A-F]{2}:[0-9A-F]{2}:[0-9A-F]{2})', found, re.IGNORECASE)
        if mac_match:
            self.dice_info['mac'] = mac_match.group(1)
            print(f"✓ Dice found: {self.dice_info['mac']}")
        
        # Wait for connection confirmation
        connected = self.wait_for_pattern(r"DICE CONNECTED - Slot", timeout=15)
        if connected:
            print("✓ Dice connected successfully")
            self.test_results['connection'] = 'PASS'
            return True
        else:
            print("✗ Connection failed")
            self.test_results['connection'] = 'FAIL'
            return False
    
    def test_color_detection(self):
        """TEST 4: Verify color detection"""
        print("\n" + "="*60)
        print("TEST 4: Color Detection Test")
        print("="*60)
        
        result = self.wait_for_pattern(r"DICE COLOR DETECTED.*Shell Color: (\w+)", timeout=10)
        if result:
            color_match = re.search(r"Shell Color: (\w+)", result)
            if color_match:
                self.dice_info['color'] = color_match.group(1)
                print(f"✓ Color detected: {self.dice_info['color']}")
            self.test_results['color'] = 'PASS'
            return True
        else:
            print("✗ Color not detected")
            self.test_results['color'] = 'FAIL'
            return False
    
    def test_battery(self):
        """TEST 5: Verify battery detection"""
        print("\n" + "="*60)
        print("TEST 5: Battery Detection Test")
        print("="*60)
        
        result = self.wait_for_pattern(r"BATTERY LEVEL.*Level: (\d+)%", timeout=10)
        if result:
            battery_match = re.search(r"Level: (\d+)%", result)
            if battery_match:
                self.dice_info['battery'] = int(battery_match.group(1))
                print(f"✓ Battery detected: {self.dice_info['battery']}%")
                
                if self.dice_info['battery'] < 20:
                    print("⚠️  WARNING: Low battery detected")
            
            self.test_results['battery'] = 'PASS'
            return True
        else:
            print("✗ Battery not detected")
            self.test_results['battery'] = 'FAIL'
            return False
    
    def test_roll_detection(self):
        """TEST 6: Test roll detection (interactive)"""
        print("\n" + "="*60)
        print("TEST 6: Roll Detection Test")
        print("="*60)
        print("📣 PLEASE ROLL THE DIE NOW")
        print("⏳ Waiting for roll (30 seconds)...")
        
        # Wait for rolling state
        rolling = self.wait_for_pattern(r"Rolling\.\.\.", timeout=30)
        if not rolling:
            print("✗ Rolling state not detected")
            self.test_results['roll'] = 'FAIL'
            return False
        
        print("✓ Rolling state detected")
        
        # Wait for stable state
        stable = self.wait_for_pattern(r"DICE STABLE.*Roll Value: (\d+)", timeout=10)
        if stable:
            value_match = re.search(r"Roll Value: (\d+)", stable)
            if value_match:
                roll_value = int(value_match.group(1))
                print(f"✓ Stable state detected - Roll value: {roll_value}")
                
                if 1 <= roll_value <= 6:
                    print("✓ Roll value in valid range (1-6)")
                    self.test_results['roll'] = 'PASS'
                    return True
                else:
                    print(f"✗ Roll value out of range: {roll_value}")
                    self.test_results['roll'] = 'FAIL'
                    return False
        else:
            print("✗ Stable state not detected")
            self.test_results['roll'] = 'FAIL'
            return False
    
    def test_led_control(self):
        """TEST 7: Test LED control"""
        print("\n" + "="*60)
        print("TEST 7: LED Control Test")
        print("="*60)
        print("📣 Sending LED ON command...")
        
        # Send 'l' command
        self.serial.write(b'l')
        time.sleep(0.5)
        
        print("✓ LED command sent")
        print("❓ Do the dice LEDs turn RED? (visual check)")
        
        time.sleep(2)
        
        print("📣 Sending LED OFF command...")
        self.serial.write(b'o')
        time.sleep(0.5)
        
        print("✓ LED OFF command sent")
        print("❓ Did the LEDs turn off? (visual check)")
        
        # This test requires manual verification
        self.test_results['led'] = 'MANUAL'
        return True
    
    def test_stability(self):
        """TEST 8: Test connection stability"""
        print("\n" + "="*60)
        print("TEST 8: Connection Stability Test (60 seconds)")
        print("="*60)
        
        disconnects = 0
        start_time = time.time()
        
        print("⏳ Monitoring connection for 60 seconds...")
        while time.time() - start_time < 60:
            line = self.read_line()
            if line:
                if "DISCONNECTED" in line:
                    disconnects += 1
                    print(f"⚠️  Disconnect detected (count: {disconnects})")
                
                # Show periodic status
                if "Connected Dice:" in line:
                    print(f"  Status: {line}")
            
            time.sleep(0.1)
        
        if disconnects == 0:
            print("✓ No disconnections during 60 second test")
            self.test_results['stability'] = 'PASS'
            return True
        else:
            print(f"✗ {disconnects} disconnection(s) detected")
            self.test_results['stability'] = 'FAIL'
            return False
    
    def generate_report(self):
        """Generate test report"""
        print("\n" + "="*60)
        print("TEST SUMMARY REPORT")
        print("="*60)
        print(f"Test Date: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        print(f"Serial Port: {self.port}")
        print()
        
        # Dice Information
        if self.dice_info:
            print("Dice Information:")
            print("-" * 40)
            if 'mac' in self.dice_info:
                print(f"  MAC Address: {self.dice_info['mac']}")
            if 'color' in self.dice_info:
                print(f"  Shell Color: {self.dice_info['color']}")
            if 'battery' in self.dice_info:
                print(f"  Battery Level: {self.dice_info['battery']}%")
            print()
        
        # Test Results
        print("Test Results:")
        print("-" * 40)
        
        passed = 0
        failed = 0
        manual = 0
        
        for test, result in self.test_results.items():
            symbol = "✓" if result == "PASS" else ("✗" if result == "FAIL" else "❓")
            print(f"  {symbol} {test.upper()}: {result}")
            
            if result == "PASS":
                passed += 1
            elif result == "FAIL":
                failed += 1
            elif result == "MANUAL":
                manual += 1
        
        print()
        print(f"Total Tests: {len(self.test_results)}")
        print(f"  Passed: {passed}")
        print(f"  Failed: {failed}")
        print(f"  Manual: {manual}")
        print()
        
        # Overall Status
        if failed == 0 and passed > 5:
            print("🎉 OVERALL STATUS: ✓ PASS - Library is working correctly!")
        elif failed <= 2:
            print("⚠️  OVERALL STATUS: PARTIAL PASS - Some issues found")
        else:
            print("❌ OVERALL STATUS: FAIL - Major issues detected")
        
        print("="*60)
    
    def run_all_tests(self):
        """Run complete test suite"""
        print("\n" + "="*70)
        print(" "*10 + "GoDice ESP32 Automated Test Suite")
        print("="*70)
        
        if not self.connect():
            print("❌ Cannot connect to ESP32. Exiting.")
            return False
        
        # Run tests in sequence
        tests = [
            self.test_boot,
            self.test_scan,
            self.test_connection,
            self.test_color_detection,
            self.test_battery,
            self.test_roll_detection,
            self.test_led_control,
            self.test_stability
        ]
        
        for test_func in tests:
            try:
                test_func()
            except Exception as e:
                print(f"❌ Test error: {e}")
                self.test_results[test_func.__name__] = 'ERROR'
            
            time.sleep(1)  # Pause between tests
        
        # Generate report
        self.generate_report()
        
        return len([r for r in self.test_results.values() if r == 'FAIL']) == 0

def main():
    """Main entry point"""
    if len(sys.argv) < 2:
        print("Usage: python godice_test_automation.py <COM_PORT>")
        print("Example: python godice_test_automation.py COM3")
        print("         python godice_test_automation.py /dev/ttyUSB0")
        sys.exit(1)
    
    port = sys.argv[1]
    
    validator = GoDiceTestValidator(port)
    success = validator.run_all_tests()
    
    sys.exit(0 if success else 1)

if __name__ == "__main__":
    main()
