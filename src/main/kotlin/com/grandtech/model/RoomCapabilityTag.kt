package com.grandtech.model

/**
 * Tags a room as having a specific physical capability required by certain subjects.
 *
 * Matched against [Subject.roomCapabilityTag] during post-solve room allocation.
 */
enum class RoomCapabilityTag {
    /** Chemistry / Biology / Physics wet lab. Used by Integrated Science. */
    SCIENCE_LAB,

    /** Pre-Technical Studies workshop for woodwork, metalwork, etc. */
    WORKSHOP,

    /** School farm or garden plot. Used by Agriculture & Nutrition. */
    GARDEN,
}