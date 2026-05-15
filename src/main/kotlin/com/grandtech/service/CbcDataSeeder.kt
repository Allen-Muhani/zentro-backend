package com.grandtech.service

import com.grandtech.model.Subject
import com.grandtech.model.SubjectType
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.inject.Inject

/**
 * Seeds the Neo4j graph with CBC Junior Secondary School (JSS) curriculum data at startup.
 *
 * On each application start this bean checks whether any `Subject` nodes exist.
 * If the graph is empty, it creates `SubjectType` nodes and all associated learning
 * areas defined by the Kenya Institute of Curriculum Development (KICD).
 *
 * MERGE is used for every write, so repeated startups are fully idempotent.
 */
@ApplicationScoped
class CbcDataSeeder {

    /** Repository that executes Neo4j reads and writes for subject nodes. */
    @Inject
    lateinit var subjectRepository: SubjectRepository

    /**
     * Entry point called by the CDI container once the application is ready.
     *
     * Skips seeding when subjects already exist so repeated restarts are safe.
     *
     * @param event the Quarkus startup event injected by CDI
     */
    fun onStart(@Observes event: StartupEvent) {
        if (subjectRepository.countAll() > 0L) return
        seed()
    }

    /**
     * Creates all JSS curriculum groups and their learning areas.
     *
     * Persists two [SubjectType] nodes (CORE and OPTIONAL) followed by every
     * [Subject] in the CBC JSS timetable structure.
     */
    private fun seed() {
        val core = SubjectType(
            name = "CORE",
            description = "Compulsory learning areas — all JSS students must study these subjects.",
        )
        val optional = SubjectType(
            name = "OPTIONAL",
            description = "Elective learning areas — students must choose at least two.",
        )
        subjectRepository.saveSubjectType(core)
        subjectRepository.saveSubjectType(optional)

        // ── Core learning areas ───────────────────────────────────────────────
        save(core, "ENG", "English and Literature",
            "Language arts covering reading, writing, speaking, listening, and literary appreciation.")
        save(core, "KIS", "Kiswahili na Fasihi",
            "National language covering communication, grammar, and Swahili literature.")
        save(core, "MAT", "Mathematics",
            "Number sense, algebra, geometry, measurement, and data handling.")
        save(core, "ISC", "Integrated Science",
            "Biology, chemistry, and physics concepts taught as a single interdisciplinary learning area.")
        save(core, "SST", "Social Studies",
            "History, geography, and citizenship education blended into one learning area.")
        save(core, "RE", "Religious Education",
            "Values and ethical formation; offered as CRE, IRE, or HRE depending on the student's faith.")
        save(core, "BUS", "Business Studies",
            "Entrepreneurship, financial literacy, and foundational business concepts.")
        save(core, "AGR", "Agriculture",
            "Crop production, animal husbandry, and environmental stewardship.")
        save(core, "LSV", "Life Skills and Values",
            "Personal development, mental health, peer relationships, and ethical decision-making.")
        save(core, "PHE", "Physical and Health Education",
            "Physical fitness, sport skills, and health promotion.")

        // ── Optional learning areas ───────────────────────────────────────────
        save(optional, "HOS", "Home Science",
            "Nutrition, food preparation, textiles, and household management.")
        save(optional, "CPS", "Computer Science",
            "Digital literacy, programming fundamentals, and computational thinking.")
        save(optional, "VAS", "Visual Arts",
            "Drawing, painting, sculpture, and design across traditional and digital media.")
        save(optional, "MUS", "Music",
            "Vocal and instrumental performance, music theory, and appreciation.")
        save(optional, "PAR", "Performing Arts",
            "Drama, dance, and theatre production.")
        save(optional, "FRE", "French",
            "French language, culture, and francophone literature.")
        save(optional, "GER", "German",
            "German language, culture, and literature.")
        save(optional, "ARA", "Arabic",
            "Arabic language, script, and Islamic heritage studies.")
        save(optional, "KSL", "Kenyan Sign Language",
            "Communication system for students with hearing impairments; fulfils the Kiswahili requirement.")
    }

    /**
     * Persists a single [Subject] linked to the given [SubjectType].
     *
     * @param type        the curriculum group this subject belongs to
     * @param symbol      short timetable code, e.g. `"ENG"`, `"MAT"`
     * @param name        full official name of the learning area
     * @param description brief summary of what the learning area covers
     */
    private fun save(type: SubjectType, symbol: String, name: String, description: String) {
        subjectRepository.saveSubject(
            Subject(symbol = symbol, name = name, description = description),
            type.name,
        )
    }
}