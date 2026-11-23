import com.bharatai.education.core.*
fun main() {
  var passed = 0
  for (case in ExampleQuestions.entries) {
    val result = VerifiedSolver.solve(case.spec)
    check(result.answer == case.expected) {"${case.label}: ${result.answer} expected ${case.expected}"}
    val parsed = QuestionParser.parse(case.question)
    check(parsed != null) {"Parser rejected example: ${case.label}"}
    check(VerifiedSolver.solve(parsed).answer == case.expected) {"${case.label}: bad parsed response ${VerifiedSolver.solve(parsed).answer}"}
    passed += 2
    println("PASS ${case.label}: ${result.answer}")
  }
  fun case(kind:String,target:String,g:Map<String,String>,expected:String) {
    val sol=VerifiedSolver.solve(VerifiedSolver.Spec(kind,target,g))
    check(sol.answer==expected) {"$kind/$target: ${sol.answer} expected $expected"}
    passed++
  }
  case("circuit","equivalent_resistance", mapOf("connection" to "parallel", "resistors_ohm" to "6,3"), "2 Ω")
  case("circuit","current", mapOf("connection" to "parallel", "resistors_ohm" to "6,3","voltage_v" to "12"), "6 A")
  case("motion","distance",mapOf("initial_velocity_mps" to "10", "final_velocity_mps" to "-10", "time_s" to "4"),"20 m")
  case("motion","displacement",mapOf("initial_velocity_mps" to "10", "final_velocity_mps" to "-10", "time_s" to "4"),"0 m")
  case("punnett","dominant",mapOf("parent1" to "Aa", "parent2" to "Aa"),"3/4")
  case("enthalpy","change",mapOf("reactants_kj_per_mol" to "775", "products_kj_per_mol" to "515"), "-260 kJ/mol")
  case("mirror","between_rays", mapOf("incidence_angle_deg" to "65"),"130 °")
  fun reject(spec:VerifiedSolver.Spec) {
    try {VerifiedSolver.solve(spec); error("Expected rejection $spec") }
    catch(e: VerifiedSolver.InvalidProblem){passed++}
  }
  reject(VerifiedSolver.Spec("triangle","area",mapOf("leg_a" to "0", "leg_b" to "4", "unit" to "cm")))
  reject(VerifiedSolver.Spec("circuit","current",mapOf("connection" to "parallel", "resistors_ohm" to "0,3", "voltage_v" to "12")))
  reject(VerifiedSolver.Spec("punnett","dominant",mapOf("parent1" to "Ab", "parent2" to "Aa")))
  check(QuestionParser.parse("Compute something complicated") == null);passed++
  check(QuestionParser.parse("A right triangle has legs 3 cm and 4 cm and hypotenuse 5 cm. Find the perimeter.") == null);passed++
  check(QuestionParser.parse("A circuit has 17 ohm and 3 ohm resistors. Find current.") == null);passed++
  println("ALL $passed checks passed")
}
