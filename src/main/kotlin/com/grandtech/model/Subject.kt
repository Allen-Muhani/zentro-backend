package com.grandtech.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Represents a CBC JSS learning area / subject.
 *
 * Kenya CBC Junior Secondary curriculum constraints
 * After rationalizing from 14 to 9 learning areas, the
 * approved weekly lesson allocations per grade are:
 *
 *   English Language        – 5 lessons
 *   Kiswahili / KSL         – 4 lessons
 *   Mathematics             – 5 lessons
 *   Integrated Science      – 5 (incl. 1 double-period practical)
 *   Pre-Technical Studies   – 4 (incl. 1 double-period practical)
 *   Social Studies          – 4 lessons
 *   Religious Education     – 4 (student chooses CRE/IRE/HRE)
 *   Agriculture & Nutrition – 4 (incl. 1 double-period practical)
 *   Creative Arts & Sports  – 5 (incl. 1 double; PE before break)
 *   PPI (Pastoral Prog.)    – 1 (Mon P0; form teacher)
 *   ──────────────────────────────────────────────────────────────
 *   TOTAL = 41 lessons per week
 *
 * Practical double-period rule: subjects marked
 * [requiresDoubledPeriod] = true MUST have at least one
 * pair of consecutive teaching periods per week. Pairs must NOT
 * span Short Break (P3–P4) or Lunch Break (P6–P7).
 *
 * Room requirements are used for post-solve room assignment
 * and do not affect timetable placement unless the school has
 * only one lab or workshop room.
 */
data class Subject(
    /** Unique code, e.g. "ENG", "MATH", "SCI". */
    val id: String,

    /** The subject's symbol e.g. "MATH" for Mathematics, "ENG" for English. */
    val symbol: String,

    /** Full display name, e.g. "Integrated Science". */
    val name: String,

    /** Short description of what the subject covers, shown to end users. */
    val description: String? = null,

    /** Broad curriculum category driving solver constraints. */
    val type: SubjectType,

    /**
     * Mandatory lessons per week for every grade group.
     * Set by KICD; timetable must satisfy this exactly.
     */
    val periodsPerWeek: Int,

    /**
     * Whether at least one double period (two consecutive lessons
     * on the same day) is required per week. Applies to: Integrated
     * Science, Pre-Technical Studies, Agriculture & Nutrition,
     * Creative Arts & Sports.
     */
    val requiresDoubledPeriod: Boolean = false,

    /**
     * Whether this subject requires a specialist room (lab,
     * workshop, farm, etc.). Used during post-solve room allocation.
     */
    val requiresSpecialRoom: Boolean = false,

    /**
     * Room capability required by this subject. Null for regular classroom subjects.
     * Matched against Room.capabilityTag during post-solve room allocation.
     */
    val roomCapabilityTag: RoomCapabilityTag? = null,

    /**
     * True only for PPI — it is fixed to Monday Period 0 and
     * taught by the class form teacher, so the solver skips it.
     */
    @get:JsonProperty("isPpiFixed")
    val isPpiFixed: Boolean = false,

    /**
     * For ARTS_SPORTS, physical-activity lessons should end just
     * before a break (P3 or P6) so students can clean up. When
     * true, the solver adds a soft penalty for other slots.
     */
    val preferBeforeBreak: Boolean = false,

    /**
     * Maximum times this subject may appear on the same day for
     * a single grade. Core subjects (Eng, Math) default to 1.
     * Doubles count as 2 towards this limit.
     */
    val maxPeriodsPerDay: Int = 1,
)