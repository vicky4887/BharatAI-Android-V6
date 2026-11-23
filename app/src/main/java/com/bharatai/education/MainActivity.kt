package com.bharatai.education

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.bharatai.education.localai.NativeLlama
import com.bharatai.education.localai.ModelImport
import com.bharatai.education.localai.LocalProposal
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.bharatai.education.core.ExampleQuestions
import com.bharatai.education.core.QuestionParser
import com.bharatai.education.core.VerifiedSolver
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF1554B4), secondary = Color(0xFF006D77))) {
                BharatAIScreen()
            }
        }
    }
}

private data class Family(
    val kind: String, val label: String, val targets: List<Pair<String,String>>, val fields: List<Pair<String,String>>
)
private val families = listOf(
    Family("circuit", "Electric circuits", listOf("current" to "Current", "equivalent_resistance" to "Equivalent R"),
        listOf("connection" to "Connection: series / parallel", "resistors_ohm" to "Resistors in Ω (comma-separated)", "voltage_v" to "Battery voltage (V)")),
    Family("motion", "Uniform motion", listOf("acceleration" to "Acceleration", "final_speed" to "Final speed", "displacement" to "Displacement", "distance" to "Distance"),
        listOf("initial_velocity_mps" to "Initial velocity (m/s)", "final_velocity_mps" to "Final velocity (m/s)", "acceleration_mps2" to "Acceleration (m/s²) - optional", "time_s" to "Time (s)")),
    Family("triangle", "Right triangles", listOf("perimeter" to "Perimeter", "hypotenuse" to "Hypotenuse", "area" to "Area"),
        listOf("leg_a" to "Perpendicular leg A", "leg_b" to "Perpendicular leg B", "unit" to "Length unit: cm / mm / m")),
    Family("enthalpy", "Reaction enthalpy", listOf("change" to "Enthalpy ΔH", "type" to "Reaction type"),
        listOf("reactants_kj_per_mol" to "Reactants enthalpy (kJ/mol)", "products_kj_per_mol" to "Products enthalpy (kJ/mol)")),
    Family("punnett", "Mendelian genetics", listOf("dominant" to "Dominant", "recessive" to "Recessive"),
        listOf("parent1" to "First parent genotype (Aa)", "parent2" to "Second parent genotype (Aa)")),
    Family("mirror", "Plane mirror", listOf("reflection_angle" to "Reflection angle", "between_rays" to "Between rays"),
        listOf("incidence_angle_deg" to "Incidence angle (degrees from normal)"))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BharatAIScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var question by remember { mutableStateOf("") }
    var familyKind by remember { mutableStateOf("circuit") }
    var target by remember { mutableStateOf("current") }
    val inputs = remember { mutableStateMapOf<String, String>() }
    var confirmed by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<VerifiedSolver.Solution?>(null) }
    var notice by remember { mutableStateOf("Offline calculations ready. Choose an example or enter a question.") }
    var ocrBusy by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var localModelPath by remember { mutableStateOf(ModelImport.importedModel(context)?.absolutePath ?: "") }
    var localModelReady by remember { mutableStateOf(false) }
    var modelBusy by remember { mutableStateOf(false) }
    var modelStatus by remember { mutableStateOf("Model support: ${if (NativeLlama.available) "native JNI available" else "not built into APK"}. Import GGUF after installing native-enabled APK.") }
    val chooseGGUF = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            modelBusy = true
            modelStatus = "Importing GGUF into private storage…"
            try {
                withContext(Dispatchers.IO) { if (localModelReady) NativeLlama.release() }
                localModelReady = false
                val imported = withContext(Dispatchers.IO) { ModelImport.importFromUri(context, uri) }
                localModelPath = imported.absolutePath
                modelStatus = "GGUF imported (${imported.length() / (1024 * 1024)} MiB). Tap Load local model."
            } catch (e: Exception) {
                modelStatus = "Import failed: ${e.message}"
            } finally { modelBusy = false }
        }
    }

    fun applySpec(spec: VerifiedSolver.Spec, message: String) {
        familyKind = spec.kind
        target = spec.target
        inputs.clear()
        inputs.putAll(spec.given)
        confirmed = false
        result = null
        notice = message
    }
    fun ocrPhoto(uri: Uri) {
        ocrBusy = true
        notice = "Recognizing printed text on-device…"
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val image = InputImage.fromFilePath(context, uri)
            recognizer.process(image)
                .addOnSuccessListener { detected ->
                    if (detected.text.isBlank()) {
                        notice = "No readable text found. Try a clearer photo or type the question."
                    } else {
                        question = detected.text.trim()
                        confirmed = false
                        result = null
                        notice = "OCR extracted printed text. CORRECT any symbols, units or digits before interpreting. Diagrams are not interpreted by OCR."
                    }
                }.addOnFailureListener { e -> notice = "OCR failed: ${e.localizedMessage ?: "unreadable image"}" }
                .addOnCompleteListener { ocrBusy = false; recognizer.close() }
        } catch (e: Exception) {
            ocrBusy = false
            recognizer.close()
            notice = "Unable to open image: ${e.localizedMessage}"
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) ocrPhoto(uri)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = cameraUri
        if (success && uri != null) ocrPhoto(uri) else notice = "Camera cancelled or failed."
    }
    fun takePhoto() {
        try {
            val dir = File(context.cacheDir, "camera_images").apply { mkdirs() }
            val file = File.createTempFile("bharatai_", ".jpg", dir)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraUri = uri
            camera.launch(uri)
        } catch (e: Exception) { notice = "Camera unavailable: ${e.localizedMessage}" }
    }
    val family = families.first { it.kind == familyKind }
    Scaffold(topBar = { TopAppBar(title = { Text("BharatAI Education · Offline V6") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Verified solutions for six supported problem families", style = MaterialTheme.typography.titleMedium)
            Text("Offline OCR and verified solvers work without the model. Local V6 text interpretation requires the native-enabled build and an imported GGUF. Image diagrams are not yet understood.", style = MaterialTheme.typography.bodySmall)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Local BharatAI V6 (no EdgeXpert connection)", fontWeight = FontWeight.Bold)
                    Text(modelStatus, style = MaterialTheme.typography.bodySmall)
                    if (localModelPath.isNotBlank()) Text("Selected: ${File(localModelPath).name}", style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(enabled = !modelBusy, onClick = { chooseGGUF.launch(arrayOf("*/*")) }) {
                            Text("Import GGUF")
                        }
                        Button(enabled = !modelBusy && localModelPath.isNotBlank() && NativeLlama.available,
                            onClick = {
                                scope.launch {
                                    modelBusy = true
                                    modelStatus = "Loading model on device…"
                                    try {
                                        val msg = withContext(Dispatchers.IO) {
                                            NativeLlama.initialize(localModelPath, 2048, 3)
                                        }
                                        localModelReady = !msg.startsWith("ERROR:")
                                        modelStatus = msg
                                    } catch (e: Exception) { localModelReady = false; modelStatus = "Load failed: ${e.message}" }
                                    finally { modelBusy = false }
                                }
                            }) { Text("Load V6") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(enabled = !modelBusy && localModelReady && question.isNotBlank(), onClick = {
                            scope.launch {
                                modelBusy = true
                                modelStatus = "Interpreting question using local GGUF…"
                                try {
                                    val text = withContext(Dispatchers.IO) {
                                        NativeLlama.generate(LocalProposal.prompt(question), 256)
                                    }
                                    if (text.startsWith("ERROR:")) throw IllegalArgumentException(text)
                                    val proposal = LocalProposal.interpretOrRule(text, question)
                                    applySpec(proposal.spec, proposal.notice)
                                    modelStatus = "Local model response processed. Check each input below."
                                } catch (e: Exception) {
                                    modelStatus = "Local interpretation failed: ${e.message}"
                                    confirmed = false
                                } finally { modelBusy = false }
                            }
                        }) { Text("Interpret with V6") }
                        OutlinedButton(enabled = !modelBusy && localModelReady, onClick = {
                            scope.launch {
                                modelBusy = true
                                withContext(Dispatchers.IO) { NativeLlama.release() }
                                localModelReady = false
                                modelBusy = false
                                modelStatus = "Model unloaded. Solver/OCR still work."
                            }
                        }) { Text("Unload") }
                    }
                    if (modelBusy) Text("Working locally; this may be slow on first load…", style = MaterialTheme.typography.bodySmall)
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Try a verified example", fontWeight = FontWeight.Bold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExampleQuestions.entries.forEach { ex ->
                            OutlinedButton(onClick = { question = ex.question; applySpec(ex.spec, "Example values loaded. Review them and confirm before solving.") }) {
                                Text(ex.label)
                            }
                        }
                    }
                }
            }
            Text("1 · Enter or photograph the question", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = question,
                onValueChange = { question = it; confirmed = false; result = null },
                label = { Text("Question text (editable after OCR)") },
                placeholder = { Text("A 20 V battery has 17 Ω and 3 Ω resistors in series. Find current.") },
                modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 8
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !ocrBusy, onClick = { takePhoto() }) { Text("Camera") }
                OutlinedButton(enabled = !ocrBusy, onClick = {
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Gallery") }
                Button(enabled = !ocrBusy, onClick = {
                    val interpreted = QuestionParser.parse(question)
                    if (interpreted == null) {
                        confirmed = false; result = null
                        notice = "Cannot interpret confidently. Select a family and enter values manually. No values were guessed."
                    } else applySpec(interpreted, "Rules extracted the values. Confirm the units, requested quantity and assumptions before solving.")
                }) { Text(if (ocrBusy) "Reading…" else "Interpret text") }
            }
            Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                Text(notice, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
            }
            HorizontalDivider()
            Text("2 · Review or enter structured values", fontWeight = FontWeight.Bold)
            Text("Supported family (scroll to choose)", style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                families.forEach { f ->
                    FilterChip(selected = familyKind == f.kind, onClick = {
                        familyKind = f.kind; target = f.targets.first().first
                        inputs.clear(); confirmed = false; result = null
                    }, label = { Text(f.label) })
                }
            }
            Text("Calculate", style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                family.targets.forEach { (key,label) ->
                    FilterChip(selected = target == key, onClick = { target = key; confirmed = false; result = null }, label = { Text(label) })
                }
            }
            family.fields.filterNot { familyKind == "circuit" && target == "equivalent_resistance" && it.first == "voltage_v" }
                .forEach { (key, label) ->
                    OutlinedTextField(
                        value = inputs[key] ?: "",
                        onValueChange = { inputs[key] = it; confirmed = false; result = null },
                        label = { Text(label) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                }
            Row {
                Checkbox(checked = confirmed, onCheckedChange = { confirmed = it; result = null })
                Text("I checked every value, unit, requested quantity, and the stated assumptions.", modifier = Modifier.padding(top = 10.dp))
            }
            Button(enabled = confirmed, modifier = Modifier.fillMaxWidth(), onClick = {
                try {
                    // Only fields for the active family are supplied to the deterministic solver.
                    val keys = family.fields.map { it.first }.toSet()
                    val data = inputs.filterKeys { it in keys }.filterValues { it.isNotBlank() }.toMutableMap()
                    if (familyKind == "circuit" && target == "equivalent_resistance") data.remove("voltage_v")
                    result = VerifiedSolver.solve(VerifiedSolver.Spec(familyKind, target, data))
                    notice = "Calculation completed from confirmed values. This does NOT validate OCR or diagram interpretation."
                } catch (e: IllegalArgumentException) {
                    result = null
                    notice = "Could not solve: ${e.message}"
                }
            }) { Text("3 · Calculate verified answer") }
            result?.let { sol ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("CALCULATED ANSWER", style = MaterialTheme.typography.labelMedium)
                        Text(sol.answer, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                        Text("Steps", fontWeight = FontWeight.Bold)
                        sol.steps.forEachIndexed { index, step -> Text("${index + 1}. $step") }
                        Text("Assumptions — verify these", fontWeight = FontWeight.Bold)
                        sol.assumptions.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        Text("Verified arithmetic for the confirmed inputs; this does not guarantee the question was understood correctly.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
