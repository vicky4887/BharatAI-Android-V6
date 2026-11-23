package com.bharatai.education.core

/** Deliberately narrow rule interpreter. Returns null if unsupported/ambiguous.
 *  OCR errors, units and diagram topology must always be reviewed by the student.
 */
object QuestionParser {
    private val n = "([+-]?(?:\\d+(?:\\.\\d+)?|\\.\\d+))"
    private val resistance = Regex("$n\\s*(?:Ω|ohms?)(?![A-Za-z])", RegexOption.IGNORE_CASE)
    private val voltage = Regex("$n\\s*(?:V|volts?)(?![A-Za-z])", RegexOption.IGNORE_CASE)
    private val side = Regex("$n\\s*(cm|mm|m)(?![A-Za-z/])", RegexOption.IGNORE_CASE)
    private val enthalpyUnit = "\\s*kJ\\s*/\\s*mol"
    private val cross = Regex("\\b([A-Za-z]{2})\\s*(?:×|[xX]|cross(?:ed)?\\s+with)\\s*([A-Za-z]{2})\\b")
    private fun find(q: String, regex: Regex): String? = regex.find(q)?.groupValues?.get(1)

    fun parse(question: String): VerifiedSolver.Spec? {
        val q = question.trim().replace('−','-')
        if (q.length !in 10..4000) return null
        val low = q.lowercase()
        val type = when {
            "resistor" in low || "ohm" in low || 'Ω' in q -> circuit(q,low)
            "triangle" in low -> triangle(q,low)
            "reactant" in low && "product" in low && ("enthalpy" in low || "kj/mol" in low.replace(" ","")) -> enthalpy(q,low)
            "genotype" in low || "punnett" in low || "offspring" in low || "phenotype" in low -> punnett(q,low)
            "mirror" in low && ("incidence" in low || "incident" in low) -> mirror(q,low)
            ("velocity" in low || "speed" in low) && ("time" in low || "seconds" in low || " s" in low) -> motion(q,low)
            else -> null
        }
        return try { type?.also { VerifiedSolver.solve(it) } } catch (_: IllegalArgumentException) { null }
    }

    private fun circuit(q: String, low: String): VerifiedSolver.Spec? {
        val series = Regex("\\bseries\\b").containsMatchIn(low)
        val parallel = Regex("\\bparallel\\b").containsMatchIn(low)
        if (series == parallel) return null // can't infer topology from OCR
        val values = resistance.findAll(q).map { it.groupValues[1] }.toList()
        if (values.size !in 2..16) return null
        val isCurrent = Regex("\\bcurrent\\b").containsMatchIn(low)
        val isEquivalent = "equivalent resistance" in low || "total resistance" in low
        if (isCurrent == isEquivalent) return null
        val voltageValues = voltage.findAll(q).map { it.groupValues[1] }.toList()
        if (isCurrent && voltageValues.size != 1) return null
        if (!isCurrent && voltageValues.size > 1) return null
        val given = mutableMapOf("connection" to if (series) "series" else "parallel", "resistors_ohm" to values.joinToString(", "))
        if (isCurrent) given["voltage_v"] = voltageValues.single()
        return VerifiedSolver.Spec("circuit", if (isCurrent) "current" else "equivalent_resistance", given)
    }
    private fun triangle(q: String, low: String): VerifiedSolver.Spec? {
        if (!("right" in low || "perpendicular" in low)) return null
        val values = side.findAll(q).toList()
        if (values.size != 2) return null
        val units = values.map { it.groupValues[2].lowercase() }.distinct()
        if (units.size != 1) return null
        val target = when {
            "perimeter" in low -> "perimeter"
            "area" in low -> "area"
            "hypotenuse" in low -> "hypotenuse"
            else -> return null
        }
        // Reject when the two measured lengths might include a hypotenuse.
        if (Regex("\\bhypotenuse\\s*(?:is|=|of)\\s*\\d").containsMatchIn(low)) return null
        return VerifiedSolver.Spec("triangle", target, mapOf("leg_a" to values[0].groupValues[1], "leg_b" to values[1].groupValues[1], "unit" to units.single()))
    }
    private fun enthalpy(q: String, low: String): VerifiedSolver.Spec? {
        val react = Regex("reactants?[^\\d+-]{0,55}$n$enthalpyUnit", RegexOption.IGNORE_CASE)
        val prod = Regex("products?[^\\d+-]{0,55}$n$enthalpyUnit", RegexOption.IGNORE_CASE)
        val r = react.find(q)?.groupValues?.get(1) ?: return null
        val p = prod.find(q)?.groupValues?.get(1) ?: return null
        val category = "exothermic" in low || "endothermic" in low || "classify" in low || "reaction type" in low
        val delta = "calculate" in low && ("enthalpy" in low || "δh" in low || "Δh" in q) || "change in enthalpy" in low
        if (category == delta) return null
        return VerifiedSolver.Spec("enthalpy", if (category) "type" else "change", mapOf("reactants_kj_per_mol" to r, "products_kj_per_mol" to p))
    }
    private fun punnett(q: String, low: String): VerifiedSolver.Spec? {
        val match = cross.find(q) ?: return null
        val dominant = "dominant" in low; val recessive = "recessive" in low
        if (dominant == recessive) return null
        return VerifiedSolver.Spec("punnett", if (dominant) "dominant" else "recessive", mapOf("parent1" to match.groupValues[1], "parent2" to match.groupValues[2]))
    }
    private fun mirror(q: String, low: String): VerifiedSolver.Spec? {
        if (!("normal" in low || "from the normal" in low)) return null
        val angle = Regex("$n\\s*(?:°|degrees?|deg)(?![A-Za-z])", RegexOption.IGNORE_CASE).findAll(q).toList()
        if (angle.size != 1) return null
        val target = when {
            "between" in low && "rays" in low -> "between_rays"
            "reflection" in low || "reflected" in low -> "reflection_angle"
            else -> return null
        }
        return VerifiedSolver.Spec("mirror",target,mapOf("incidence_angle_deg" to angle.single().groupValues[1]))
    }
    private fun motion(q: String, low: String): VerifiedSolver.Spec? {
        val initial = find(q, Regex("(?:initial|starting)\\s+(?:velocity|speed)[^\\d-]{0,25}$n\\s*m\\s*/\\s*s",RegexOption.IGNORE_CASE)) ?: return null
        val final = find(q, Regex("(?:final|ending)\\s+(?:velocity|speed)[^\\d-]{0,25}$n\\s*m\\s*/\\s*s",RegexOption.IGNORE_CASE))
        val acceleration = find(q, Regex("(?:acceleration)[^\\d-]{0,25}$n\\s*m\\s*/\\s*s(?:²|\\^2|2)", RegexOption.IGNORE_CASE))
        val time = find(q, Regex("(?:time|over|in)[^\\d-]{0,20}$n\\s*(?:s|seconds?)(?![A-Za-z])",RegexOption.IGNORE_CASE)) ?: return null
        val target = when {
            "find acceleration" in low || "calculate acceleration" in low -> "acceleration"
            "find final speed" in low || "calculate final speed" in low -> "final_speed"
            "find displacement" in low || "calculate displacement" in low -> "displacement"
            "find distance" in low || "calculate distance" in low -> "distance"
            else -> return null
        }
        val g = mutableMapOf("initial_velocity_mps" to initial,"time_s" to time)
        if (final != null) g["final_velocity_mps"] = final
        if (acceleration != null) g["acceleration_mps2"] = acceleration
        if (final == null && acceleration == null) return null
        return VerifiedSolver.Spec("motion",target,g)
    }
}

object ExampleQuestions {
    data class Example(val label: String, val question: String, val spec: VerifiedSolver.Spec, val expected: String)
    val entries: List<Example> = listOf(
        Example("Series circuit", "A 20 V battery is connected to 17 ohm and 3 ohm resistors in series. Find the current.", VerifiedSolver.Spec("circuit", "current", mapOf("connection" to "series", "resistors_ohm" to "17, 3", "voltage_v" to "20")), "1 A"),
        Example("Acceleration", "An object's initial velocity is 1 m/s and final velocity is 71 m/s over 14 s. Calculate acceleration.", VerifiedSolver.Spec("motion", "acceleration", mapOf("initial_velocity_mps" to "1", "final_velocity_mps" to "71", "time_s" to "14")), "5 m/s²"),
        Example("Triangle perimeter", "A right triangle has perpendicular legs 152 cm and 285 cm. Find its perimeter.", VerifiedSolver.Spec("triangle", "perimeter", mapOf("leg_a" to "152", "leg_b" to "285", "unit" to "cm")), "760 cm"),
        Example("Enthalpy type", "Reactants have enthalpy 775 kJ/mol and products have enthalpy 515 kJ/mol. Classify the reaction as exothermic or endothermic.", VerifiedSolver.Spec("enthalpy", "type", mapOf("reactants_kj_per_mol" to "775", "products_kj_per_mol" to "515")), "exothermic"),
        Example("Mendelian genetics", "For the Punnett cross Aa × Aa, what is the probability of recessive phenotype in offspring?", VerifiedSolver.Spec("punnett", "recessive", mapOf("parent1" to "Aa", "parent2" to "Aa")), "1/4"),
        Example("Plane mirror", "A ray strikes a plane mirror with an incidence angle of 65 degrees measured from the normal. Find angle of reflection.", VerifiedSolver.Spec("mirror", "reflection_angle", mapOf("incidence_angle_deg" to "65")), "65 °")
    )
}
