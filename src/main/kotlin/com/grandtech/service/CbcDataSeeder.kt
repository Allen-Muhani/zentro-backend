package com.grandtech.service

import com.grandtech.model.RoomCapabilityTag
import com.grandtech.model.Subject
import com.grandtech.model.SubjectType
import com.grandtech.repository.SubjectRepository
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.inject.Inject

/**
 * Seeds the Neo4j graph with all CBC JSS learning areas on startup.
 *
 * Runs once per application start. If Subject nodes already exist the seed
 * is skipped entirely, so repeated restarts are safe. Every write uses MERGE,
 * making the seed idempotent even if it runs concurrently.
 *
 * Curriculum source: KICD rationalized JSS timetable (9 learning areas + PPI,
 * totalling 41 periods per week).
 */
@ApplicationScoped
class CbcDataSeeder {

    /** Repository used to persist each [Subject] node during seeding. */
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
     * Persists every entry in [SUBJECTS] to the graph.
     *
     * Delegates each write to [SubjectRepository.saveSubject], which uses MERGE
     * so the operation is idempotent.
     */
    private fun seed() {
        SUBJECTS.forEach(subjectRepository::saveSubject)
    }

    companion object {
        /** Canonical list of all 10 rationalized CBC JSS learning areas to seed into the graph. */
        val SUBJECTS = listOf(
            Subject(
                id = "ENG",
                symbol = "ENG",
                name = "English",
                type = SubjectType.LANGUAGE,
                periodsPerWeek = 5,
                maxPeriodsPerDay = 1,
            ),
            Subject(
                id = "KIS",
                symbol = "KIS",
                name = "Kiswahili",
                type = SubjectType.LANGUAGE,
                periodsPerWeek = 4,
                maxPeriodsPerDay = 1,
            ),
            Subject(
                id = "MAT",
                symbol = "MAT",
                name = "Mathematics",
                type = SubjectType.MATHEMATICS,
                periodsPerWeek = 5,
                maxPeriodsPerDay = 1,
            ),
            Subject(
                id = "SCI",
                symbol = "SCI",
                name = "Integrated Science",
                type = SubjectType.SCIENCE,
                periodsPerWeek = 5,
                requiresDoubledPeriod = true,
                requiresSpecialRoom = true,
                roomCapabilityTag = RoomCapabilityTag.SCIENCE_LAB,
                maxPeriodsPerDay = 2,
            ),
            Subject(
                id = "PTS",
                symbol = "PTS",
                name = "Pre-Technical Studies",
                type = SubjectType.TECHNICAL,
                periodsPerWeek = 4,
                requiresDoubledPeriod = true,
                requiresSpecialRoom = true,
                roomCapabilityTag = RoomCapabilityTag.WORKSHOP,
                maxPeriodsPerDay = 2,
            ),
            Subject(
                id = "SST",
                symbol = "SST",
                name = "Social Studies",
                type = SubjectType.SOCIAL,
                periodsPerWeek = 4,
                maxPeriodsPerDay = 1,
            ),
            Subject(
                id = "RE",
                symbol = "RE",
                name = "Religious Education",
                type = SubjectType.RELIGIOUS,
                periodsPerWeek = 4,
                maxPeriodsPerDay = 1,
            ),
            Subject(
                id = "AGR",
                symbol = "AGR",
                name = "Agriculture and Nutrition",
                type = SubjectType.AGRICULTURE,
                periodsPerWeek = 4,
                requiresDoubledPeriod = true,
                requiresSpecialRoom = true,
                roomCapabilityTag = RoomCapabilityTag.GARDEN,
                maxPeriodsPerDay = 2,
            ),
            Subject(
                id = "CAS",
                symbol = "CAS",
                name = "Creative Arts and Sports",
                type = SubjectType.ARTS_SPORTS,
                periodsPerWeek = 5,
                requiresDoubledPeriod = true,
                preferBeforeBreak = true,
                maxPeriodsPerDay = 2,
            ),
            Subject(
                id = "PPI",
                symbol = "PPI",
                name = "Pastoral Programme of Instruction",
                type = SubjectType.PASTORAL,
                periodsPerWeek = 1,
                isPpiFixed = true,
                maxPeriodsPerDay = 1,
            ),
        )
    }
}
