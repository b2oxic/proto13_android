package com.proto13.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.proto13.android.ui.theme.Proto13AndroidTheme
import org.json.JSONArray
import java.io.File
import java.util.Calendar
import kotlin.math.min

fun normalizeTitleText(value: String): String {
    return value
        .replace(Regex("[^A-Za-z0-9\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .trim()
}

private fun loadCardNamesFromAsset(context: Context): List<String> {
    return try {
        context.assets.open("mtg_local_reference.json")
            .bufferedReader()
            .use { it.readText() }
            .let { jsonText ->
                JSONArray(jsonText)
                    .let { array ->
                        (0 until array.length()).map { index ->
                            val card = array.getJSONObject(index)
                            card.optString("name", "")
                        }
                    }
            }
            .filter { it.isNotBlank() }
    } catch (_: Exception) {
        emptyList()
    }
}

internal fun findExactCardTitleMatch(ocrText: String, knownTitles: Collection<String>): String? {
    val candidates = ocrText.lineSequence()
        .map { normalizeTitleText(it) }
        .filter { it.isNotBlank() && it.any { ch -> ch.isLetter() } }
        .toList()

    return candidates.firstOrNull { candidate ->
        knownTitles.any { knownTitle ->
            normalizeTitleText(knownTitle) == candidate
        }
    }
}

internal fun shouldRetryCapture(ocrText: String, knownTitles: Collection<String>, maxAttempts: Int, attempt: Int): Boolean {
    if (attempt >= maxAttempts) return false
    return findExactCardTitleMatch(ocrText, knownTitles) == null
}

private fun containsFooterYear(text: String): Boolean {
    val lower = text.lowercase()
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    return (1996..currentYear).any { year ->
        Regex("""\b${year}\d*\b""").containsMatchIn(lower)
    }
}

private fun footerMatchScore(text: String): Int {
    val lower = text.lowercase()
    var score = 0

    if (lower.contains("wizards of the coast")) score += 50
    if (Regex("""\d{3}\s*/\s*\d{3}""").containsMatchIn(lower)) score += 25
    if (lower.contains("©")) score += 30
    if (lower.contains("tm") && lower.contains("&")) score += 20
    if (containsFooterYear(lower)) score += 55
    if (Regex("""(?:m|tm)\s*(?:&?\s*c)?\s*(?:19[9-9]\d|20\d{2})\d*""").containsMatchIn(lower)) score += 15

    return score
}

private fun footerSignalCount(text: String): Int {
    val lower = text.lowercase()
    var count = 0

    if (lower.contains("wizards of the coast")) count += 1
    if (Regex("""\d{3}\s*/\s*\d{3}""").containsMatchIn(lower)) count += 1
    if (lower.contains("©")) count += 1
    if (lower.contains("tm") && lower.contains("&")) count += 1
    if (containsFooterYear(lower)) count += 1
    if (Regex("""(?:m|tm)\s*(?:&?\s*c)?\s*(?:19[9-9]\d|20\d{2})\d*""").containsMatchIn(lower)) count += 1

    return count
}

private fun isFooterLine(line: String): Boolean {
    return footerMatchScore(line) > 0
}

private data class FooterScanResult(
    val index: Int,
    val text: String,
    val score: Int,
    val signalCount: Int,
    val side: String
)

private fun findBestFooterWindow(lines: List<String>): FooterScanResult? {
    if (lines.isEmpty()) return null

    val windowSize = min(5, lines.size)
    val leadingWindowText = lines.take(windowSize).joinToString(" ")
    val trailingWindowText = lines.takeLast(windowSize).joinToString(" ")

    val leadingCandidate = FooterScanResult(
        index = 0,
        text = leadingWindowText,
        score = footerMatchScore(leadingWindowText),
        signalCount = footerSignalCount(leadingWindowText),
        side = "leading"
    )

    val trailingStartIndex = maxOf(0, lines.size - windowSize)
    val trailingCandidate = FooterScanResult(
        index = trailingStartIndex,
        text = trailingWindowText,
        score = footerMatchScore(trailingWindowText),
        signalCount = footerSignalCount(trailingWindowText),
        side = "trailing"
    )

    return listOf(leadingCandidate, trailingCandidate)
        .maxWithOrNull(compareBy<FooterScanResult> { it.score }.thenBy { it.signalCount })
}

data class TitleExtractionResult(
    val title: String,
    val footerSummary: String
)

fun extractBestTitle(ocrText: String): String {
    return analyzeTitleExtraction(ocrText).title
}

private fun analyzeTitleExtraction(ocrText: String): TitleExtractionResult {
    if (ocrText.isBlank()) return TitleExtractionResult("", "No OCR text detected.")

    val lines = ocrText.lineSequence()
        .map { normalizeTitleText(it) }
        .filter { it.isNotBlank() && it.any { ch -> ch.isLetter() } }
        .toList()

    if (lines.isEmpty()) {
        return TitleExtractionResult("", "No OCR text lines found.")
    }

    val bestFooter = findBestFooterWindow(lines)
    val footerDetected = bestFooter != null &&
        (bestFooter.score >= 50 || (bestFooter.score >= 45 && bestFooter.signalCount >= 2))
    val footerIndex = if (footerDetected) bestFooter.index else -1
    val footerMatch = if (footerDetected) bestFooter.text else null
    val footerSummary = if (footerMatch != null) {
        val footerSideDescription = if (bestFooter!!.side == "leading") {
            "leading side"
        } else {
            "trailing side"
        }
        "Footer block detected on the $footerSideDescription: '$footerMatch' (scored from Wizards of the Coast / number-number / copyright / TM & / 4-digit year). Evaluating the opposite side of the card for the title."
    } else {
        "No footer-style block found; scanning the full OCR text for the last valid title-like line."
    }

    val validTitleCandidates = lines.withIndex()
        .filter { looksLikeTitle(it.value) }
        .map { it.index to it.value }

    if (footerIndex >= 0 && validTitleCandidates.isNotEmpty()) {
        val footerIsTrailing = bestFooter?.side == "trailing"
        val prioritizedTitle = if (footerIsTrailing) {
            validTitleCandidates
                .sortedBy { it.first }
                .firstOrNull()
                ?.second
                ?: lines.first()
        } else {
            validTitleCandidates
                .sortedByDescending { it.first }
                .firstOrNull()
                ?.second
                ?: lines.last()
        }

        val sideDescription = if (footerIsTrailing) {
            "footer is on the trailing side, so the title is selected from the earliest valid title-like OCR line"
        } else {
            "footer is on the leading side, so the title is selected from the latest valid title-like OCR line"
        }
        return TitleExtractionResult(
            prioritizedTitle,
            "$footerSummary $sideDescription."
        )
    }

    val scoredCandidates = lines.withIndex()
        .filter { looksLikeTitle(it.value) }
        .map { (index, line) ->
            val wordCount = line.split(" ").count { it.isNotBlank() }
            val footerPenalty = if (isFooterLine(line)) 100 else 0
            val singleWordPenalty = if (wordCount <= 1) 2 else 0
            val baseScore = if (line.length in 3..80) 10 else 0

            val distanceFromEnd = lines.size - index
            val positionBonus = maxOf(0, 18 - (distanceFromEnd * 3))

            line to (baseScore + (wordCount * 4) + positionBonus - footerPenalty - singleWordPenalty)
        }

    val bestCandidate = scoredCandidates
        .sortedByDescending { it.second }
        .firstOrNull()
        ?.first
        ?: normalizeTitleText(ocrText)

    return TitleExtractionResult(bestCandidate, footerSummary)
}

private fun isClearlyNonTitleLine(line: String): Boolean {
    val lower = line.lowercase()
    return isFooterLine(line) ||
        lower.contains("description") ||
        lower.contains("for each") ||
        lower.contains("copyright") ||
        Regex("""\bartist\s+name\b""").containsMatchIn(lower) ||
        Regex("""\b(?:version|edition)\s+\d+\b""").containsMatchIn(lower) ||
        Regex("""\b(?:art|artist)\s*code\s+\d+\b""").containsMatchIn(lower) ||
        Regex("""\b(?:card|collector|set|language|rarity)\s+\d+\b""").containsMatchIn(lower) ||
        Regex("""\d{3}\s*/\s*\d{3}""").containsMatchIn(lower) ||
        Regex("""\d{3,}\s+\d{3,}""").containsMatchIn(lower)
}

private fun looksLikeTitle(line: String): Boolean {
    if (line.isBlank()) return false

    val words = line.split(" ").filter { it.isNotBlank() }
    if (words.size < 2 || words.size > 6) return false
    if (isClearlyNonTitleLine(line)) return false

    val lower = line.lowercase()
    return line.length in 3..80 &&
        !lower.contains(":") &&
        !lower.contains("(") &&
        !lower.contains(")") &&
        !lower.contains("\"")
}

fun matchTitleToReference(candidate: String, knownTitles: Collection<String> = emptyList()): String {
    val cleaned = normalizeTitleText(candidate)
    if (cleaned.isBlank()) {
        return "No title detected."
    }

    if (knownTitles.isEmpty()) {
        return "No reference titles available."
    }

    val exactMatch = knownTitles.firstOrNull { normalizeTitleText(it) == cleaned }
    if (exactMatch != null) {
        return "Exact match: $exactMatch"
    }

    val ranked = knownTitles.map { reference ->
        val referenceNormalized = normalizeTitleText(reference)
        val distance = levenshteinDistance(cleaned, referenceNormalized)
        val maxLength = maxOf(cleaned.length, referenceNormalized.length)
        val similarity = if (maxLength == 0) 1.0 else 1.0 - (distance.toDouble() / maxLength)
        reference to similarity
    }.sortedByDescending { it.second }

    val bestMatch = ranked.firstOrNull()
    return if ((bestMatch?.second ?: 0.0) >= 0.55) {
        "Possible match: ${bestMatch!!.first}"
    } else {
        "No strong match. Closest: ${bestMatch?.first ?: "unknown"}"
    }
}

private fun levenshteinDistance(left: String, right: String): Int {
    if (left == right) return 0
    if (left.isEmpty()) return right.length
    if (right.isEmpty()) return left.length

    val previous = IntArray(right.length + 1)
    val current = IntArray(right.length + 1)

    for (j in previous.indices) {
        previous[j] = j
    }

    for (i in left.indices) {
        current[0] = i + 1
        for (j in right.indices) {
            val substitutionCost = if (left[i] == right[j]) 0 else 1
            current[j + 1] = min(
                min(current[j] + 1, previous[j + 1] + 1),
                previous[j] + substitutionCost
            )
        }
        for (j in previous.indices) {
            previous[j] = current[j]
        }
    }

    return current[right.length]
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Proto13AndroidTheme {
                Proto13App()
            }
        }
    }
}

@Composable
fun Proto13App(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isProcessing by remember { mutableStateOf(false) }
    var recognizedText by remember { mutableStateOf("") }
    var bestTitle by remember { mutableStateOf("") }
    var titleMatch by remember { mutableStateOf("") }
    var footerSummary by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            errorMessage = "Camera permission is required to capture photos."
        }
    }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    LaunchedEffect(previewView, hasCameraPermission) {
        if (!hasCameraPermission || previewView == null) {
            return@LaunchedEffect
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = CameraPreview.Builder().build().also {
                it.setSurfaceProvider(previewView?.surfaceProvider)
            }
            val capture = ImageCapture.Builder().build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    capture
                )
                imageCapture = capture
            } catch (exc: Exception) {
                errorMessage = exc.localizedMessage ?: "Unable to start camera."
            }
        }, executor)
    }

    DisposableEffect(key1 = lifecycleOwner) {
        onDispose {
            val provider = try {
                ProcessCameraProvider.getInstance(context).get()
            } catch (_: Exception) {
                null
            }
            provider?.unbindAll()
        }
    }

    var capturePhoto: (Int, Int) -> Unit = { _, _ -> }
    var processImage: (Bitmap, Int, Int) -> Unit = { _, _, _ -> }

    capturePhoto = { attempt, maxAttempts ->
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            hasCameraPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            val currentCapture = imageCapture
            if (currentCapture != null) {
                val outputFile = File(context.cacheDir, "proto13_capture_${System.currentTimeMillis()}.jpg")
                val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

                currentCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            val bitmap = BitmapFactory.decodeFile(outputFile.absolutePath)
                            if (bitmap != null) {
                                capturedBitmap = bitmap
                                processImage(bitmap, attempt, maxAttempts)
                            } else {
                                if (attempt < maxAttempts) {
                                    capturePhoto(attempt + 1, maxAttempts)
                                } else {
                                    errorMessage = "The captured image could not be decoded."
                                }
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            if (attempt < maxAttempts) {
                                capturePhoto(attempt + 1, maxAttempts)
                            } else {
                                errorMessage = exception.message ?: "Capture failed."
                                isProcessing = false
                            }
                        }
                    }
                )
            } else {
                errorMessage = "Camera is not ready yet."
            }
        }
    }

    processImage = { bitmap, attempt, maxAttempts ->
        isProcessing = true
        errorMessage = null
        recognizedText = ""
        bestTitle = ""
        titleMatch = ""
        footerSummary = ""

        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val rawText = visionText.text.ifBlank { "No text detected." }
                recognizedText = rawText

                val exactReferenceTitle = findExactCardTitleMatch(
                    rawText,
                    loadCardNamesFromAsset(context)
                )

                if (exactReferenceTitle != null) {
                    bestTitle = exactReferenceTitle
                    titleMatch = "Exact match: $exactReferenceTitle"
                    footerSummary = "Exact card name matched in the local reference data."
                    isProcessing = false
                    return@addOnSuccessListener
                }

                if (shouldRetryCapture(rawText, loadCardNamesFromAsset(context), maxAttempts, attempt)) {
                    isProcessing = false
                    capturePhoto(attempt + 1, maxAttempts)
                    return@addOnSuccessListener
                }

                val extraction = analyzeTitleExtraction(rawText)
                bestTitle = extraction.title
                titleMatch = matchTitleToReference(extraction.title, loadCardNamesFromAsset(context))
                footerSummary = extraction.footerSummary
                isProcessing = false
            }
            .addOnFailureListener { exception ->
                if (attempt < maxAttempts) {
                    capturePhoto(attempt + 1, maxAttempts)
                } else {
                    errorMessage = exception.localizedMessage ?: "OCR failed."
                    isProcessing = false
                }
            }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "Card OCR",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Preview a card, capture the still, and OCR the result.",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (!hasCameraPermission) {
                Button(
                    onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant camera permission")
                }
            } else {
                AndroidView(
                    factory = { context ->
                        PreviewView(context).also { previewView = it }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { capturePhoto(0, 3) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Capture photo")
                }
            }

            if (isProcessing) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator()
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (capturedBitmap != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Captured image",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Image(
                    bitmap = capturedBitmap!!.asImageBitmap(),
                    contentDescription = "Captured photo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
            }

            if (recognizedText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Recognized text",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = recognizedText,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            if (bestTitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Best title",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = bestTitle,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = footerSummary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = titleMatch,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

