package com.grandtech.model

/**
 * Broad categorisation of the 10 CBC JSS learning areas.
 *
 * Used by the solver to apply category-specific soft constraints:
 *
 *  * LANGUAGE    – language-specialist teacher; prefer morning.
 *  * MATHEMATICS – maths teacher; prefer morning slots.
 *  * SCIENCE     – double period required; lab room required.
 *  * TECHNICAL   – double period required; workshop required.
 *  * SOCIAL      – standard classroom; spread across week.
 *  * RELIGIOUS   – standard classroom; CRE/IRE/HRE strand.
 *  * AGRICULTURE – double period required; farm/garden room.
 *  * ARTS_SPORTS – physical component must be before break.
 *  * PASTORAL    – fixed Monday PPI slot; form teacher.
 */
enum class SubjectType {
    /** Language subjects (English, Kiswahili). */
    LANGUAGE,

    /** Mathematics. */
    MATHEMATICS,

    /** Integrated Science — lab practicals required. */
    SCIENCE,

    /** Pre-Technical Studies — workshop practicals required. */
    TECHNICAL,

    /** Social Studies. */
    SOCIAL,

    /** Religious Education (CRE / IRE / HRE). */
    RELIGIOUS,

    /** Agriculture and Nutrition — farm practicals required. */
    AGRICULTURE,

    /** Creative Arts and Sports — PE before break. */
    ARTS_SPORTS,

    /** Pastoral Programme of Instruction — fixed Monday slot. */
    PASTORAL,
}