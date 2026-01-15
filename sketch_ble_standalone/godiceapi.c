/**
 * GoDice API - ESP32 Port Implementation
 * 
 * Ported from ParticulaCode/GoDiceAndroid_iOS_API
 * Original: https://github.com/ParticulaCode/GoDiceAndroid_iOS_API
 */

#include "godiceapi.h"
#include <string.h>
#include <stdbool.h>
#include <float.h>
#include <math.h>

// Optional logging (enable for debugging)
// #define GODICE_LOGGING

#ifdef GODICE_LOGGING
    #include <Arduino.h>
    #define log(FORMAT, ...) Serial.printf("GoDice: " FORMAT "\n", __VA_ARGS__)
#else
    #define log(FORMAT, ...)
#endif

#define countof(array) (sizeof(array) / sizeof(array[0]))

// ==================== EVENT KEY PREFIXES ====================
// These identify incoming packet types from the die

static const char EK_Battery[] = "Bat";
static const char EK_Roll[] = "R";
static const char EK_Stable[] = "S";
static const char EK_FakeStable[] = "FS";
static const char EK_MoveStable[] = "MS";
static const char EK_TiltStable[] = "TS";
static const char EK_Tap[] = "Tap";
static const char EK_DoubleTap[] = "DTap";
static const char EK_Charging[] = "Charg";
static const char EK_Color[] = "Color";

// ==================== 3D AXIS STRUCTURE ====================

typedef struct __attribute__((__packed__)) {
    int8_t x;
    int8_t y;
    int8_t z;
} axis_t;

// ==================== DICE TYPE DEFINITIONS ====================

typedef struct {
    int max;           // Maximum die value (6 for D6, 20 for D20, etc.)
    axis_t *values;    // Array of axis values for each face
    size_t values_num; // Number of faces
    int (*transform)(int raw_value);  // Transform function (e.g., D10X returns 0-90 in 10s)
} diceType_t;

// Identity transform (most dice)
static int identity_transform(int value) {
    return value;
}

// D10X transform (returns 0, 10, 20, ..., 90, 00)
static int d10x_transform(int value) {
    return (value % 10) * 10;
}

// Face axis values for different die types
// These map 3D accelerometer readings to face numbers

static axis_t D6Values[] = {
    {0, 63, 0}, {0, 0, 63}, {-63, 0, 0}, {63, 0, 0}, {0, 0, -63}, {0, -63, 0}
};

static axis_t D20Values[] = {
    {0, 55, 30}, {0, 55, -30}, {52, 17, 30}, {-52, 17, 30},
    {32, -45, 30}, {-32, -45, 30}, {0, -55, -30}, {52, 17, -30},
    {32, -45, -30}, {-52, 17, -30}, {-32, -45, -30}, {0, -55, 30},
    {32, 45, 30}, {-32, 45, 30}, {0, 55, 30}, {-52, -17, 30},
    {52, -17, 30}, {32, 45, -30}, {-32, 45, -30}, {52, -17, -30}
};

static axis_t D4Values[] = {
    {0, -35, -52}, {-45, 31, -26}, {0, 31, 52}, {45, 31, -26}
};

static axis_t D8Values[] = {
    {0, -63, 0}, {45, 0, -45}, {0, 0, -63}, {-45, 0, -45},
    {-45, 0, 45}, {0, 0, 63}, {45, 0, 45}, {0, 63, 0}
};

static axis_t D10Values[] = {
    {0, 61, -20}, {58, 19, -20}, {36, -50, -20}, {-36, -50, -20},
    {-58, 19, -20}, {-58, -19, 20}, {-36, 50, 20}, {36, 50, 20},
    {58, -19, 20}, {0, -61, 20}
};

static axis_t D12Values[] = {
    {0, -33, -54}, {0, -33, 54}, {-47, -33, -16}, {-47, -33, 16},
    {-29, 54, -16}, {-29, 54, 16}, {29, 54, 16}, {29, 54, -16},
    {47, -33, 16}, {47, -33, -16}, {0, 54, -33}, {0, -54, 33}
};

// Dice type registry
static diceType_t DiceTypes[] = {
    {6, D6Values, countof(D6Values), identity_transform},
    {20, D20Values, countof(D20Values), identity_transform},
    {4, D4Values, countof(D4Values), identity_transform},
    {8, D8Values, countof(D8Values), identity_transform},
    {10, D10Values, countof(D10Values), identity_transform},
    {100, D10Values, countof(D10Values), d10x_transform},
    {12, D12Values, countof(D12Values), identity_transform},
};

// ==================== HELPER FUNCTIONS ====================

/**
 * Check if packet starts with given event key prefix
 */
static bool is_event_prefix(const uint8_t *packet, size_t size, const char *event_key) {
    size_t key_len = strlen(event_key);
    if (size < key_len) {
        return false;
    }
    return memcmp(packet, event_key, key_len) == 0;
}

/**
 * Convert axis to die face value using nearest neighbor search
 */
static int axis_to_value(const axis_t *values, size_t values_num, const axis_t *sample) {
    float min_distance = FLT_MAX;
    int result_index = 0;
    
    for (size_t i = 0; i < values_num; i++) {
        int dx = values[i].x - sample->x;
        int dy = values[i].y - sample->y;
        int dz = values[i].z - sample->z;
        float distance = sqrtf(dx * dx + dy * dy + dz * dz);
        
        if (distance < min_distance) {
            min_distance = distance;
            result_index = i;
        }
    }
    
    return result_index + 1;  // Face numbers are 1-based
}

// ==================== INCOMING PACKET PARSERS ====================

/**
 * Parse rolling event (die is tumbling)
 */
static godice_status_t incoming_roll_packet(
    const godice_callbacks_t *cb,
    void *cb_userdata,
    int dice_id,
    const uint8_t *packet,
    size_t size
) {
    if (cb->on_dice_roll == NULL) {
        return GODICE_INVALID_CALLBACK;
    }
    if (size == 0) {
        return GODICE_INVALID_PACKET;
    }
    
    cb->on_dice_roll(cb_userdata, dice_id);
    return GODICE_OK;
}

/**
 * Stable packet structure
 */
typedef struct __attribute__((__packed__)) {
    uint8_t key;
    axis_t axis;
} stablePacket_t;

/**
 * Parse stable event (die settled on a face)
 */
static godice_status_t incoming_stable_packet(
    const godice_callbacks_t *cb,
    void *cb_userdata,
    int dice_id,
    int dice_max,
    const uint8_t *raw_packet,
    size_t size,
    const char *stable_type
) {
    if (cb->on_dice_stable == NULL) {
        return GODICE_INVALID_CALLBACK;
    }
    if (size != sizeof(stablePacket_t)) {
        return GODICE_INVALID_PACKET;
    }
    
    stablePacket_t *packet = (stablePacket_t*)raw_packet;
    
    log("%cS (%d, %d, %d)",
        stable_type == NULL ? ' ' : *stable_type,
        (int)packet->axis.x,
        (int)packet->axis.y,
        (int)packet->axis.z);
    
    // Find matching die type and convert axis to face value
    for (int i = 0; i < countof(DiceTypes); i++) {
        diceType_t *dice_type = &DiceTypes[i];
        if (dice_max == dice_type->max) {
            int raw_roll = axis_to_value(dice_type->values, dice_type->values_num, &packet->axis);
            int transformed_roll = dice_type->transform(raw_roll);
            cb->on_dice_stable(cb_userdata, dice_id, transformed_roll);
            break;
        }
    }
    
    return GODICE_OK;
}

/**
 * Parse battery level event
 */
static godice_status_t incoming_battery_packet(
    const godice_callbacks_t *cb,
    void *cb_userdata,
    int dice_id,
    const uint8_t *packet,
    size_t size
) {
    if (cb->on_charge_level == NULL) {
        return GODICE_INVALID_CALLBACK;
    }
    if (size != 1 || packet[0] > 100) {
        return GODICE_INVALID_PACKET;
    }
    
    cb->on_charge_level(cb_userdata, dice_id, packet[0]);
    return GODICE_OK;
}

/**
 * Parse charging state event
 */
static godice_status_t incoming_charging_packet(
    const godice_callbacks_t *cb,
    void *cb_userdata,
    int dice_id,
    const uint8_t *packet,
    size_t size
) {
    if (cb->on_charging_state_changed == NULL) {
        return GODICE_INVALID_CALLBACK;
    }
    if (size != 1 || (packet[0] != 0 && packet[0] != 1)) {
        return GODICE_INVALID_PACKET;
    }
    
    cb->on_charging_state_changed(cb_userdata, dice_id, packet[0]);
    return GODICE_OK;
}

/**
 * Parse die color event
 */
static godice_status_t incoming_color_packet(
    const godice_callbacks_t *cb,
    void *cb_userdata,
    int dice_id,
    const uint8_t *packet,
    size_t size
) {
    if (cb->on_dice_color == NULL) {
        return GODICE_INVALID_CALLBACK;
    }
    if (size != 1) {
        return GODICE_INVALID_PACKET;
    }
    
    // Validate color value
    switch (packet[0]) {
        case GODICE_BLACK:
        case GODICE_RED:
        case GODICE_GREEN:
        case GODICE_BLUE:
        case GODICE_YELLOW:
        case GODICE_ORANGE:
            cb->on_dice_color(cb_userdata, dice_id, packet[0]);
            return GODICE_OK;
        default:
            return GODICE_INVALID_PACKET;
    }
}

// ==================== MAIN INCOMING PACKET HANDLER ====================

godice_status_t godice_incoming_packet(
    const godice_callbacks_t *cb,
    void *cb_userdata,
    int dice_id,
    int dice_max,
    const uint8_t *packet,
    size_t size
) {
    if (cb == NULL) {
        return GODICE_INVALID_CALLBACK;
    }
    
    // Check for each event type
    if (is_event_prefix(packet, size, EK_Roll)) {
        return incoming_roll_packet(cb, cb_userdata, dice_id, packet, size);
    }
    
    if (is_event_prefix(packet, size, EK_Tap)) {
        // Tap events not used in this implementation
        return GODICE_OK;
    }
    
    if (is_event_prefix(packet, size, EK_DoubleTap)) {
        // Double tap events not used
        return GODICE_OK;
    }
    
    if (is_event_prefix(packet, size, EK_Battery)) {
        return incoming_battery_packet(cb, cb_userdata, dice_id,
            packet + sizeof(EK_Battery) - 1,
            size - sizeof(EK_Battery) + 1);
    }
    
    if (is_event_prefix(packet, size, EK_Charging)) {
        return incoming_charging_packet(cb, cb_userdata, dice_id,
            packet + sizeof(EK_Charging) - 1,
            size - sizeof(EK_Charging) + 1);
    }
    
    if (is_event_prefix(packet, size, EK_Color)) {
        return incoming_color_packet(cb, cb_userdata, dice_id,
            packet + sizeof(EK_Color) - 1,
            size - sizeof(EK_Color) + 1);
    }
    
    if (is_event_prefix(packet, size, EK_Stable)) {
        return incoming_stable_packet(cb, cb_userdata, dice_id, dice_max,
            packet, size, NULL);
    }
    
    if (is_event_prefix(packet, size, EK_FakeStable)) {
        return incoming_stable_packet(cb, cb_userdata, dice_id, dice_max,
            packet + sizeof(EK_FakeStable) - 1,
            size - sizeof(EK_FakeStable) + 1, "F");
    }
    
    if (is_event_prefix(packet, size, EK_MoveStable)) {
        return incoming_stable_packet(cb, cb_userdata, dice_id, dice_max,
            packet + sizeof(EK_MoveStable) - 1,
            size - sizeof(EK_MoveStable) + 1, "M");
    }
    
    if (is_event_prefix(packet, size, EK_TiltStable)) {
        return incoming_stable_packet(cb, cb_userdata, dice_id, dice_max,
            packet + sizeof(EK_TiltStable) - 1,
            size - sizeof(EK_TiltStable) + 1, "T");
    }
    
    // Unknown packet type
    return GODICE_INVALID_PACKET;
}

// ==================== OUTGOING COMMAND GENERATORS ====================

godice_status_t godice_init_packet(
    uint8_t *buffer,
    size_t buffer_size,
    size_t *written_size,
    int dice_sensitivity,
    const godice_toggle_leds_t *toggle_leds
) {
    if (buffer_size < GODICE_INIT_PACKET_SIZE) {
        return GODICE_BUFFER_TOO_SMALL;
    }
    
    buffer[0] = 0x19;  // Init command
    buffer[1] = dice_sensitivity;
    buffer[2] = toggle_leds->number_of_blinks;
    buffer[3] = toggle_leds->light_on_duration_10ms;
    buffer[4] = toggle_leds->light_off_duration_10ms;
    buffer[5] = toggle_leds->color_red;
    buffer[6] = toggle_leds->color_green;
    buffer[7] = toggle_leds->color_blue;
    buffer[8] = (uint8_t)toggle_leds->blink_mode;
    buffer[9] = (uint8_t)toggle_leds->leds;
    
    *written_size = GODICE_INIT_PACKET_SIZE;
    return GODICE_OK;
}

godice_status_t godice_open_leds_packet(
    uint8_t *buffer,
    size_t buffer_size,
    size_t *written_size,
    uint8_t red1, uint8_t green1, uint8_t blue1,
    uint8_t red2, uint8_t green2, uint8_t blue2
) {
    if (buffer_size < GODICE_OPEN_LEDS_PACKET_SIZE) {
        return GODICE_BUFFER_TOO_SMALL;
    }
    
    buffer[0] = 0x08;  // Open LEDs command
    buffer[1] = red1;
    buffer[2] = green1;
    buffer[3] = blue1;
    buffer[4] = red2;
    buffer[5] = green2;
    buffer[6] = blue2;
    
    *written_size = GODICE_OPEN_LEDS_PACKET_SIZE;
    return GODICE_OK;
}

godice_status_t godice_toggle_leds_packet(
    uint8_t *buffer,
    size_t buffer_size,
    size_t *written_size,
    const godice_toggle_leds_t *toggle_leds
) {
    if (buffer_size < GODICE_TOGGLE_LEDS_PACKET_SIZE) {
        return GODICE_BUFFER_TOO_SMALL;
    }
    
    buffer[0] = 0x0C;  // Toggle LEDs command
    buffer[1] = toggle_leds->number_of_blinks;
    buffer[2] = toggle_leds->light_on_duration_10ms;
    buffer[3] = toggle_leds->light_off_duration_10ms;
    buffer[4] = toggle_leds->color_red;
    buffer[5] = toggle_leds->color_green;
    buffer[6] = toggle_leds->color_blue;
    buffer[7] = (uint8_t)toggle_leds->blink_mode;
    buffer[8] = (uint8_t)toggle_leds->leds;
    
    *written_size = GODICE_TOGGLE_LEDS_PACKET_SIZE;
    return GODICE_OK;
}

godice_status_t godice_close_toggle_leds_packet(
    uint8_t *buffer,
    size_t buffer_size,
    size_t *written_size
) {
    if (buffer_size < GODICE_CLOSE_TOGGLE_LEDS_PACKET_SIZE) {
        return GODICE_BUFFER_TOO_SMALL;
    }
    
    buffer[0] = 0x0D;  // Close toggle LEDs command
    *written_size = GODICE_CLOSE_TOGGLE_LEDS_PACKET_SIZE;
    return GODICE_OK;
}

godice_status_t godice_get_color_packet(
    uint8_t *buffer,
    size_t buffer_size,
    size_t *written_size
) {
    if (buffer_size < GODICE_GET_COLOR_PACKET_SIZE) {
        return GODICE_BUFFER_TOO_SMALL;
    }
    
    buffer[0] = 0x17;  // Get color command
    *written_size = GODICE_GET_COLOR_PACKET_SIZE;
    return GODICE_OK;
}

godice_status_t godice_get_charge_level_packet(
    uint8_t *buffer,
    size_t buffer_size,
    size_t *written_size
) {
    if (buffer_size < GODICE_GET_CHARGE_LEVEL_PACKET_SIZE) {
        return GODICE_BUFFER_TOO_SMALL;
    }
    
    buffer[0] = 0x03;  // Get charge level command
    *written_size = GODICE_GET_CHARGE_LEVEL_PACKET_SIZE;
    return GODICE_OK;
}

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
) {
    if (buffer_size < GODICE_DETECTION_SETTINGS_UPDATE_PACKET_SIZE) {
        return GODICE_BUFFER_TOO_SMALL;
    }
    
    buffer[0] = 0x18;  // Detection settings command
    buffer[1] = samples_count;
    buffer[2] = movement_count;
    buffer[3] = face_count;
    buffer[4] = min_flat_deg;
    buffer[5] = max_flat_deg;
    buffer[6] = weak_stable;
    buffer[7] = movement_deg;
    buffer[8] = roll_threshold;
    
    *written_size = GODICE_DETECTION_SETTINGS_UPDATE_PACKET_SIZE;
    return GODICE_OK;
}
