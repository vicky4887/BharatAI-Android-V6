package com.bharatai.education.localai

import com.bharatai.education.core.VerifiedSolver
import org.junit.Assert.*
import org.junit.Test

class LocalProposalTest {
    private val circuitQuestion = "A 20 V battery has two resistors of 17 ohm and 3 ohm in series. Calculate current."

    @Test fun validJsonWithExtraAnswerDoesNotUseModelArithmetic() {
        val input = """{"kind":"circuit","target":"current","given":{"connection":"series","resistors_ohm":[17,3],"voltage_v":20},"answer":"0.5 A"}"""
        val result = LocalProposal.parse(input, circuitQuestion)
        assertEquals("1 A", VerifiedSolver.solve(result.spec).answer)
    }
    @Test fun fallbackIsExplicitWhenModelOnlyProvidesAnswer() {
        val result = LocalProposal.interpretOrRule("""{"answer":"1 A"}""", circuitQuestion)
        assertTrue(result.notice.contains("RULES fallback"))
        assertEquals("1 A", VerifiedSolver.solve(result.spec).answer)
    }
    @Test fun unsupportedInputFailsWithoutRules() {
        try {
            LocalProposal.interpretOrRule("""{"unsupported":true}""", "How many quarks in a proton?")
            fail("Unsupported question should not be solved")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("cannot interpret question"))
        }
    }
    @Test fun wrongAnswerIsNotTrusted() {
        val input = """{"kind":"motion","target":"acceleration","given":{"initial_velocity_mps":1,"final_velocity_mps":71,"time_s":14},"answer":"6 m/s2"}"""
        val result = LocalProposal.parse(input, "Velocity changes from 1 to 71 m/s in 14 seconds. Find acceleration")
        assertEquals("5 m/s²", VerifiedSolver.solve(result.spec).answer)
    }
    @Test fun nativePromptNeverRequestsFinalSolution() {
        val prompt = LocalProposal.prompt("A 20 V battery in series")
        assertTrue(prompt.contains("Never solve the question"))
        assertTrue(prompt.contains("/no_think"))
        assertTrue(prompt.contains("given"))
    }
    @Test fun incompleteSpecFallsBackExplicitly() {
        val result = LocalProposal.interpretOrRule("""{"kind":"circuit","target":"current","given":{}}""", circuitQuestion)
        assertTrue(result.notice.contains("RULES fallback"))
    }
    @Test fun invalidDomainValuesAreNotSilentlyAccepted() {
        val text = """{"kind":"circuit","target":"current","given":{"connection":"teleport","resistors_ohm":[17,3],"voltage_v":20}}"""
        val result = LocalProposal.interpretOrRule(text, circuitQuestion)
        assertTrue(result.notice.contains("RULES fallback"))
    }
    @Test fun refusesUnexpectedTarget() {
        try {
            LocalProposal.parse("""{"kind":"circuit","target":"factorization","given":{"connection":"series","resistors_ohm":[17,3],"voltage_v":20}}""", "Unsupported")
            fail("Unsupported model target must fail")
        } catch (expected: IllegalArgumentException) { assertTrue(expected.message!!.contains("Unsupported target")) }
    }
}
