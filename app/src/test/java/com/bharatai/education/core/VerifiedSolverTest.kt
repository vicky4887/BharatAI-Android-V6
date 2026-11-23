package com.bharatai.education.core

import org.junit.Assert.*
import org.junit.Test

class VerifiedSolverTest {
    @Test fun sixReferenceQuestions() {
        ExampleQuestions.entries.forEach { case ->
            assertEquals(case.label, case.expected, VerifiedSolver.solve(case.spec).answer)
        }
    }
    @Test fun ruleParserCoversSixExamples() {
        ExampleQuestions.entries.forEach { case ->
            val parsed = QuestionParser.parse(case.question)
            assertNotNull(case.label, parsed)
            assertEquals(case.label, case.expected, VerifiedSolver.solve(parsed!!).answer)
        }
    }
    @Test fun parallelCircuitAndMotionReversal() {
        assertEquals("6 A", VerifiedSolver.solve(VerifiedSolver.Spec("circuit", "current", mapOf(
            "connection" to "parallel", "resistors_ohm" to "6,3", "voltage_v" to "12"))).answer)
        assertEquals("20 m", VerifiedSolver.solve(VerifiedSolver.Spec("motion", "distance", mapOf(
            "initial_velocity_mps" to "10", "final_velocity_mps" to "-10", "time_s" to "4"))).answer)
    }
    @Test fun refusesImpossibleInputs() {
        try {
            VerifiedSolver.solve(VerifiedSolver.Spec("circuit", "current", mapOf(
                "connection" to "series", "resistors_ohm" to "0, 10", "voltage_v" to "20")))
            fail("Expected invalid resistance")
        } catch (_: VerifiedSolver.InvalidProblem) { }
    }
    @Test fun failsClosedOnAmbiguousProblems() {
        assertNull(QuestionParser.parse("A circuit has 10 Ω and 5 Ω resistors. Find current."))
        assertNull(QuestionParser.parse("A triangle is drawn with two legs. Find perimeter."))
        assertNull(QuestionParser.parse("A right triangle has legs 3 cm and 4 cm and hypotenuse 5 cm. Find the perimeter."))
    }
    @Test fun wrongGeneticLocusIsRejected() {
        try {
            VerifiedSolver.solve(VerifiedSolver.Spec("punnett", "dominant", mapOf("parent1" to "Ab", "parent2" to "Aa")))
            fail("Expected invalid genotype")
        } catch (_: VerifiedSolver.InvalidProblem) { }
    }
}
