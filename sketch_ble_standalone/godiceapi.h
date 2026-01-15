/**
 * GoDice API - ESP32 Port
 * 
 * Ported from ParticulaCode/GoDiceAndroid_iOS_API
 * Original: https://github.com/ParticulaCode/GoDiceAndroid_iOS_API
 * 
 * This is a pure C implementation of the GoDice protocol for parsing
 * incoming BLE notifications and generating command packets.
 * 
 * License: See LICENSE.md in ParticulaCode repository
 */

#ifndef __GODICESDK_GODICEAPI_H
#define __GODICESDK_GODICEAPI_H

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

// ==================== ENUMERATIONS ====================

#define GODICE_ENUM_BEGIN(name) typedef enum
#define GODICE_ENUM_END(name) name

// Status codes returned by API functions
GODICE_ENUM_BEGIN(godice_status_t) {
    GODICE_OK = 0,
    GODICE_INVALID_PACKET = 1,
    GODICE_BUFFER_TOO_SMALL = 2,
    GODICE_INVALID_CALLBACK = 3,
} GODICE_ENUM_END(godice_status_t);

// LED blink modes
GODICE_ENUM_BEGIN(godice_blink_mode_t) {
    GODICE_BLINK_ONE_BY_ONE = 0,  // Blink LEDs sequentially
    GODICE_BLINK_PARALLEL = 1,    // Blink both LEDs together
} GODICE_ENUM_END(godice_blink_mode_t);

// LED selector (for mixed mode)
GODICE_ENUM_BEGIN(godice_leds_selector_t) {
    GODICE_LEDS_BOTH = 0,
    GODICE_LEDS_LED1 = 1,
    GODICE_LEDS_LED2 = 2,
} GODICE_ENUM_END(godice_leds_selector_t);

// Dice shell colors
GODICE_ENUM_BEGIN(godice_color_t) {
    GODICE_BLACK = 0,
    GODICE_RED = 1,
    GODICE_GREEN = 2,
    GODICE_BLUE = 3,
    GODICE_YELLOW = 4,
    GODICE_ORANGE = 5,
} GODICE_ENUM_END(godice_color_t);

// ==================== CONSTANTS ====================

// Default dice sensitivity (for initialization)
#define GODICE_SENSITIVITY_DEFAULT (uint8_t)30

// Special value for infinite blinking
#define GODICE_BLINKS_INFINITE (uint8_t)255

// Detection settings defaults
#define GODICE_SAMPLES_COUNT_DEFAULT (uint8_t)4
#define GODICE_MOVEMENT_COUNT_DEFAULT (uint8_t)2
#define GODICE_FACE_COUNT_DEFAULT (uint8_t)1
#define GODICE_MIN_FLAT_DEG_DEFAULT (uint8_t)10
#define GODICE_MAX_FLAT_DEG_DEFAULT (uint8_t)54
#define GODICE_WEAK_STABLE_DEFAULT (uint8_t)20
#define GODICE_MOVEMENT_DEG_DEFAULT (uint8_t)50
#define GODICE_ROLL_THRESHOLD_DEFAULT (uint8_t)30

// Packet sizes for buffer allocation
#define GODICE_INIT_PACKET_SIZE 10
#define GODICE_OPEN_LEDS_PACKET_SIZE 7
#define GODICE_TOGGLE_LEDS_PACKET_SIZE 9
#define GODICE_CLOSE_TOGGLE_LEDS_PACKET_SIZE 1
#define GODICE_GET_COLOR_PACKET_SIZE 1
#define GODICE_GET_CHARGE_LEVEL_PACKET_SIZE 1
#define GODICE_DETECTION_SETTINGS_UPDATE_PACKET_SIZE 9

// ==================== CALLBACK STRUCTURE ====================

/**
 * Callback functions for GoDice events
 * Set to NULL if you don't need a specific callback
 */
typedef struct {
    // Called when die color is received (response to getColorPacket)
    void (*on_dice_color)(void *userdata, int dice_id, godice_color_t color);
    
    // Called when die settles on a face (stable reading)
    void (*on_dice_stable)(void *userdata, int dice_id, uint8_t number);
    
    // Called when charging state changes
    void (*on_charging_state_changed)(void *userdata, int dice_id, bool charging);
    
    // Called with battery level (response to getChargeLevelPacket)
    void (*on_charge_level)(void *userdata, int dice_id, uint8_t level);
    
    // Called when die is rolling (unstable reading)
    void (*on_dice_roll)(void *userdata, int dice_id);
} godice_callbacks_t;

// ==================== LED CONFIGURATION ====================

/**
 * LED toggle configuration structure
 * Used for initialization and toggle LED commands
 */
typedef struct {
    uint8_t number_of_blinks;         // Number of blinks (255 = infinite)
    uint8_t light_on_duration_10ms;   // On duration in 10ms units (0-255 = 0-2.55s)
    uint8_t light_off_duration_10ms;  // Off duration in 10ms units
    uint8_t color_red;                // Red component (0-255)
    uint8_t color_green;              // Green component (0-255)
    uint8_t color_blue;               // Blue component (0-255)
    godice_blink_mode_t blink_mode;   // Blink mode
    godice_leds_selector_t leds;      // LED selector
} godice_toggle_leds_t;

// ==================== CORE FUNCTIONS ====================

/**
 * Parse incoming packet from die's read characteristic
 * 
 * @param cb Callback structure with event handlers
 * @param cb_userdata User data passed to callbacks
 * @param dice_id Unique identifier for this die (0-based)
 * @param dice_max Maximum die value (6 for D6, 20 for D20, etc.)
 * @param packet Raw packet data from BLE notification
 * @param size Packet length in bytes
 * @return GODICE_OK on success, error code otherwise
 */
godice_status_t godice_incoming_packet(
    const godice_callbacks_t *cb,
    void *cb_userdata,
    int dice_id,
    int dice_max,
    const uint8_t *packet,
    size_t size
);

// ==================== OUTGOING COMMAND PACKETS ====================

/**
 * Generate initialization packet
 * First command to send after connecting to a die
 * 
 * @param buffer Output buffer (must be at least GODICE_INIT_PACKET_SIZE bytes)
 * @param buffer_size Size of output buffer
 * @param written_size Actual bytes written (output parameter)
 * @param dice_sensitivity Sensitivity setting (default: GODICE_SENSITIVITY_DEFAULT)
 * @param toggle_leds LED configuration for initial blink pattern
 * @return GODICE_OK on success, GODICE_BUFFER_TOO_SMALL if buffer too small
 */
godice_status_t godice_init_packet(
    uint8_t *buffer,
    size_t buffer_size,
    size_t *written_size,
    int dice_sensitivity,
    const godice_toggle_leds_t *toggle_leds
);

/**
 * Generate packet to set static LED colors
 * LEDs stay on until changed or turned off
 * 
 * @param buffer Output buffer (must be at least GODICE_OPEN_LEDS_PACKET_SIZE bytes)
 * @param buffer_size Size of output buffer
 * @param written_size Actual bytes written
 * @param red1 Red component for LED 1 (0-255)
 * @param green1 Green component for LED 1
 * @param blue1 Blue component for LED 1
 * @param red2 Red component for LED 2
 * @param green2 Green component for LED 2
 * @param blue2 Blue component for LED 2
 * @return GODICE_OK on success
 */
godice_status_t godice_open_leds_packet(
    uint8_t *buffer,
    size_t buffer_size,
    size_t *written_size,
    uint8_t red1, uint8_t green1, uint8_t blue1,
    uint8_t red2, uint8_t green2, uint8_t blue2
);

/**
 * Generate packet to start LED blinking pattern
 * 
 * @param buffer Output buffer (must be at least GODICE_TOGGLE_LEDS_PACKET_SIZE bytes)
 * @param buffer_size Size of output buffer
 * @param written_size Actual bytes written
 * @param toggle_leds LED configuration
 * @return GODICE_OK on success
 */
godice_status_t godice_toggle_leds_packet(
    uint8_t *buffer,
    size_t buffer_size,
    size_t *written_size,
    const godice_toggle_leds_t *toggle_leds
);

/**
 * Generate packet to stop LED blinking and turn off
 * 
 * @param buffer Output buffer (must be at least GODICE_CLOSE_TOGGLE_LEDS_PACKET_SIZE bytes)
 * @param buffer_size Size of output buffer
 * @param written_size Actual bytes written
 * @return GODICE_OK on success
 */
godice_status_t godice_close_toggle_leds_packet(
    uint8_t *buffer,
    size_t buffer_size,
    size_t *written_size
);

/**
 * Generate packet to request die shell color
 * Response delivered via on_dice_color callback
 * 
 * @param buffer Output buffer (must be at least GODICE_GET_COLOR_PACKET_SIZE bytes)
 * @param buffer_size Size of output buffer
 * @param written_size Actual bytes written
 * @return GODICE_OK on success
 */
godice_status_t godice_get_color_packet(
    uint8_t *buffer,
    size_t buffer_size,
    size_t *written_size
);

/**
 * Generate packet to request battery level
 * Response delivered via on_charge_level callback
 * 
 * @param buffer Output buffer (must be at least GODICE_GET_CHARGE_LEVEL_PACKET_SIZE bytes)
 * @param buffer_size Size of output buffer
 * @param written_size Actual bytes written
 * @return GODICE_OK on success
 */
godice_status_t godice_get_charge_level_packet(
    uint8_t *buffer,
    size_t buffer_size,
    size_t *written_size
);

/**
 * Generate packet to update die detection settings
 * Advanced configuration - use defaults unless tuning needed
 * 
 * @param buffer Output buffer (must be at least GODICE_DETECTION_SETTINGS_UPDATE_PACKET_SIZE bytes)
 * @param buffer_size Size of output buffer
 * @param written_size Actual bytes written
 * @param samples_count Number of samples for stability detection
 * @param movement_count Movement threshold count
 * @param face_count Face stability count
 * @param min_flat_deg Minimum flatness angle (degrees)
 * @param max_flat_deg Maximum flatness angle (degrees)
 * @param weak_stable Weak stability threshold
 * @param movement_deg Movement angle threshold (degrees)
 * @param roll_threshold Roll detection threshold
 * @return GODICE_OK on success
 */
godice_status_t godice_detection_settings_update_packet(
    uint8_t *buffer,
    size_t buffer_size,
    size_t *written_size,
    uint8_t samples_count,
    uint8_t movement_count,
    uint8_t face_count,
    uint8_t min_flat_deg,
    uint8_t max_flat_deg,
    uint8_t weak_stable,
    uint8_t movement_deg,
    uint8_t roll_threshold
);

#ifdef __cplusplus
}
#endif

#endif // __GODICESDK_GODICEAPI_H
