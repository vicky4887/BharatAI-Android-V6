package com.bharatai.education.core

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.sqrt

/** Offline deterministic calculations for six carefully delimited problem types.
 *  Correctness depends on the *confirmed* input quantities and assumptions.
 *  Does not interpret diagrams or perform general JEE/NEET reasoning.
 */
object VerifiedSolver {
    private val mc = MathContext(34, RoundingMode.HALF_UP)
    data class Spec(val kind: String, val target: String, val given: Map<String, String>)
    data class Solution(
        val value: String,
        val unit: String,
        val steps: List<String>,
        val assumptions: List<String>
    ) { val answer: String get() = (value + if (unit.isBlank()) "" else " $unit") }

    class InvalidProblem(message: String) : IllegalArgumentException(message)

    private fun fail(message: String): Nothing = throw InvalidProblem(message)
    private fun num(spec: Spec, key: String): BigDecimal {
        val raw = spec.given[key]?.trim() ?: fail("Missing ${key.replace('_',' ')}")
        val n = try { BigDecimal(raw) } catch (_: NumberFormatException) { fail("Invalid number: $key") }
        if (n.abs() > BigDecimal("1000000000000")) fail("$key is outside the supported range")
        return n
    }
    private fun fmt(n: BigDecimal): String {
        val display = n.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros()
        return if (display.signum() == 0) "0" else display.toPlainString()
    }
    private fun permitted(s: Spec, vararg names: String) {
        val allowed = names.toSet()
        val unknown = s.given.keys - allowed
        if (unknown.isNotEmpty()) fail("Unexpected input field(s): ${unknown.joinToString()}")
    }
    private fun requirePositive(x: BigDecimal, name: String) { if (x.signum() <= 0) fail("$name must be positive") }
    private fun d(s: String): BigDecimal = BigDecimal(s)
    private fun b(n: Int): BigDecimal = BigDecimal(n)

    fun solve(spec: Spec): Solution = when (spec.kind) {
        "circuit" -> circuit(spec)
        "motion" -> motion(spec)
        "triangle" -> triangle(spec)
        "enthalpy" -> enthalpy(spec)
        "punnett" -> punnett(spec)
        "mirror" -> mirror(spec)
        else -> fail("Unsupported problem family: ${spec.kind}")
    }
    private fun circuit(s: Spec): Solution {
        permitted(s, "connection", "resistors_ohm", "voltage_v")
        if (s.target !in listOf("current", "equivalent_resistance")) fail("Unsupported circuit target")
        val connection = s.given["connection"]?.lowercase() ?: fail("Choose series or parallel")
        if (connection !in listOf("series", "parallel")) fail("Only simple series or parallel circuits are supported")
        val raw = s.given["resistors_ohm"]?.trim().orEmpty()
        val values = raw.split(Regex("[,; ]+")).filter { it.isNotBlank() }
        if (values.size !in 2..16) fail("Provide 2–16 resistances separated by commas")
        val resistors = values.map { try { d(it) } catch (_: NumberFormatException) { fail("Invalid resistance: $it") } }
        if (resistors.any { it.signum() <= 0 || it > d("1000000000000") }) fail("All resistances must be positive and finite")
        val req = if (connection == "series") resistors.reduce { a, c -> a.add(c, mc) }
                  else BigDecimal.ONE.divide(resistors.map { BigDecimal.ONE.divide(it, mc) }
                      .reduce { a, c -> a.add(c, mc) }, mc)
        val calc = if (connection == "series") "R_eq = ${resistors.joinToString(" + ") { fmt(it) }} = ${fmt(req)} Ω"
                   else "1/R_eq = ${resistors.joinToString(" + ") { "1/${fmt(it)}" }}; R_eq = ${fmt(req)} Ω"
        val assumption = "Ideal DC resistors, ${connection} connection, no other branches."
        if (s.target == "equivalent_resistance") return Solution(fmt(req), "Ω", listOf(calc), listOf(assumption))
        val voltage = num(s, "voltage_v")
        if (voltage.signum() < 0) fail("This calculator expects a non-negative voltage magnitude")
        val current = voltage.divide(req, mc)
        return Solution(fmt(current), "A", listOf(calc, "I = V/R_eq = ${fmt(voltage)}/${fmt(req)} = ${fmt(current)} A"), listOf(assumption))
    }
    private fun motion(s: Spec): Solution {
        permitted(s, "initial_velocity_mps", "final_velocity_mps", "time_s", "acceleration_mps2")
        if (s.target !in listOf("acceleration", "final_speed", "distance", "displacement")) fail("Unsupported motion target")
        val u = num(s, "initial_velocity_mps")
        val t = num(s, "time_s")
        requirePositive(t, "Time")
        val vProvided = s.given["final_velocity_mps"]?.isNotBlank() == true
        val aProvided = s.given["acceleration_mps2"]?.isNotBlank() == true
        if (!vProvided && !aProvided) fail("Supply final velocity or acceleration")
        val a: BigDecimal
        val v: BigDecimal
        if (vProvided && aProvided) {
            v = num(s, "final_velocity_mps")
            a = num(s, "acceleration_mps2")
            if (u.add(a.multiply(t, mc), mc).subtract(v, mc).abs() > d("0.000001"))
                fail("Final velocity and acceleration are inconsistent")
        } else if (vProvided) {
            v = num(s, "final_velocity_mps")
            a = v.subtract(u, mc).divide(t, mc)
        } else {
            a = num(s, "acceleration_mps2")
            v = u.add(a.multiply(t, mc), mc)
        }
        val assumptions = listOf("Straight-line motion with uniform acceleration; signed velocities are along the same chosen axis.")
        val basics = "u = ${fmt(u)} m/s; v = ${fmt(v)} m/s; t = ${fmt(t)} s."
        return when (s.target) {
            "acceleration" -> Solution(fmt(a), "m/s²", listOf(basics, "a = (v − u)/t = (${fmt(v)} − ${fmt(u)})/${fmt(t)} = ${fmt(a)} m/s²"), assumptions)
            "final_speed" -> Solution(fmt(v.abs()), "m/s", listOf(basics, "Final speed = |v| = ${fmt(v.abs())} m/s"), assumptions)
            "displacement" -> {
                val x = u.add(v, mc).multiply(t, mc).divide(b(2), mc)
                Solution(fmt(x), "m", listOf(basics, "Displacement = (u+v)t/2 = ${fmt(x)} m"), assumptions)
            }
            else -> {
                val x = u.add(v, mc).multiply(t, mc).divide(b(2), mc)
                val reversal = u.signum() * v.signum() < 0
                val dist = if (reversal) u.multiply(u, mc).add(v.multiply(v, mc), mc)
                    .divide(a.abs().multiply(b(2), mc), mc) else x.abs()
                val extra = if (reversal) "Velocity changes direction: split at v=0; distance = (u²+v²)/(2|a|) = ${fmt(dist)} m"
                            else "No direction reversal: distance = |displacement| = ${fmt(dist)} m"
                Solution(fmt(dist), "m", listOf(basics, extra), assumptions)
            }
        }
    }
    private fun triangle(s: Spec): Solution {
        permitted(s, "leg_a", "leg_b", "unit")
        if (s.target !in listOf("perimeter", "area", "hypotenuse")) fail("Unsupported triangle target")
        val a = num(s, "leg_a"); val c = num(s, "leg_b")
        requirePositive(a, "Leg A"); requirePositive(c, "Leg B")
        val unit = s.given["unit"] ?: fail("Supply a length unit")
        if (unit !in listOf("cm", "mm", "m")) fail("Unit must be cm, mm, or m")
        val assumptions = listOf("Given sides are the two perpendicular legs of a right triangle.")
        if (s.target == "area") {
            val area = a.multiply(c, mc).divide(b(2), mc)
            return Solution(fmt(area), "$unit²", listOf("Area = leg A × leg B / 2 = ${fmt(a)} × ${fmt(c)} / 2 = ${fmt(area)} $unit²"), assumptions)
        }
        val c2 = a.multiply(a, mc).add(c.multiply(c, mc), mc)
        // sqrt(Double) limits display precision for non-perfect squares; do not claim an exact value.
        val hyp = BigDecimal.valueOf(sqrt(c2.toDouble()))
        val hypStep = "Hypotenuse = √(${fmt(a)}² + ${fmt(c)}²) ≈ ${fmt(hyp)} $unit"
        if (s.target == "hypotenuse") return Solution(fmt(hyp), unit, listOf(hypStep), assumptions + "Displayed length rounded to eight decimal places.")
        val perim = a.add(c, mc).add(hyp, mc)
        return Solution(fmt(perim), unit, listOf(hypStep, "Perimeter = ${fmt(a)} + ${fmt(c)} + ${fmt(hyp)} ≈ ${fmt(perim)} $unit"), assumptions + "Displayed length rounded to eight decimal places.")
    }
    private fun enthalpy(s: Spec): Solution {
        permitted(s, "reactants_kj_per_mol", "products_kj_per_mol")
        if (s.target !in listOf("change", "type")) fail("Unsupported enthalpy target")
        val r = num(s, "reactants_kj_per_mol"); val p = num(s, "products_kj_per_mol")
        val change = p.subtract(r, mc)
        val explain = "ΔH = products − reactants = ${fmt(p)} − ${fmt(r)} = ${fmt(change)} kJ/mol"
        val assumptions = listOf("Values refer to a single balanced reaction at constant pressure, in matching kJ/mol units.")
        if (s.target == "change") return Solution(fmt(change), "kJ/mol", listOf(explain), assumptions)
        val category = if (change.signum() < 0) "exothermic" else if (change.signum() > 0) "endothermic" else "thermoneutral"
        return Solution(category, "", listOf(explain, "Negative ΔH: exothermic; positive ΔH: endothermic; zero: thermoneutral."), assumptions)
    }
    private fun punnett(s: Spec): Solution {
        permitted(s, "parent1", "parent2")
        if (s.target !in listOf("dominant", "recessive")) fail("Choose dominant or recessive phenotype")
        val p1 = s.given["parent1"]?.trim().orEmpty(); val p2 = s.given["parent2"]?.trim().orEmpty()
        if (p1.length != 2 || p2.length != 2 || !(p1+p2).all { it.isLetter() && it.isAsciiLetter() }) fail("Use two-allele genotypes such as Aa and aa")
        if ((p1+p2).map { it.lowercaseChar() }.toSet().size != 1) fail("Only a single gene locus is supported")
        val combos = p1.flatMap { a -> p2.map { b -> "$a$b" } }
        val dominant = combos.count { pair -> pair.any { it.isUpperCase() } }
        val wanted = if (s.target == "dominant") dominant else 4 - dominant
        val divisor = gcd(wanted, 4)
        return Solution("${wanted/divisor}/${4/divisor}", "", listOf("Gamete combinations: ${combos.joinToString()}", "${s.target} phenotype = $wanted out of 4 offspring combinations."), listOf("One autosomal gene, equal segregation, complete dominance; no linkage or epistasis."))
    }
    private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'
    private fun gcd(x: Int, y: Int): Int { var a=x; var b=y; while (b!=0) { val t=a%b;a=b;b=t };return if(a==0) 1 else a }
    private fun mirror(s: Spec): Solution {
        permitted(s, "incidence_angle_deg")
        if (s.target !in listOf("reflection_angle", "between_rays")) fail("Unsupported mirror target")
        val angle = num(s, "incidence_angle_deg")
        if (angle < BigDecimal.ZERO || angle > d("90")) fail("Incidence angle must be 0–90°")
        val v = if (s.target == "between_rays") angle.multiply(b(2), mc) else angle
        val steps = if (s.target == "reflection_angle") listOf("Law of reflection: angle of reflection = angle of incidence = ${fmt(v)}°")
                    else listOf("Angle between incident and reflected rays = 2 × ${fmt(angle)} = ${fmt(v)}°")
        return Solution(fmt(v), "°", steps, listOf("Plane mirror; incidence angle measured relative to the normal, not the surface."))
    }
}
