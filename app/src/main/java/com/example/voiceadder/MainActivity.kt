package com.example.voiceadder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

// ---- палитра, повторяет веб-версию ----
private val CasingLight = Color(0xFF24272C)
private val Casing = Color(0xFF1B1D21)
private val CasingDark = Color(0xFF111215)
private val LedBg = Color(0xFF201503)
private val LedText = Color(0xFFFFB238)
private val LedDim = Color(0xFF5A3D10)
private val Paper = Color(0xFFF3EEDF)
private val PaperShadow = Color(0xFFD9D0B8)
private val Ink = Color(0xFF2B2620)
private val MutedInk = Color(0xFF8C8371)
private val MutedCasing = Color(0xFF9A9EA6)
private val AccentRed = Color(0xFFC1443A)

private const val CAP = 10

data class Entry(val id: Int, val value: Double)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                VoiceAdderScreen()
            }
        }
    }
}

@Composable
fun VoiceAdderScreen() {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(listOf<Entry>()) }
    var idCounter by remember { mutableIntStateOf(0) }
    var listening by remember { mutableStateOf(false) }
    var manualStop by remember { mutableStateOf(true) }
    var hint by remember { mutableStateOf("Нажмите и говорите") }
    var manualText by remember { mutableStateOf("") }

    val vibrator = remember {
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun addNumber(value: Double) {
        idCounter += 1
        val updated = entries + Entry(idCounter, value)
        entries = if (updated.size > CAP) updated.takeLast(CAP) else updated
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator?.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    val recognizerIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
    }

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    DisposableEffect(Unit) {
        onDispose { speechRecognizer?.destroy() }
    }

    fun startListening() {
        if (speechRecognizer == null) {
            hint = "Голосовой ввод недоступен на этом устройстве"
            return
        }
        manualStop = false
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listening = true
                hint = "Слушаю…"
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                listening = false
                if (!manualStop) {
                    try {
                        speechRecognizer.startListening(recognizerIntent)
                        listening = true
                    } catch (e: Exception) { /* игнорируем, попробуем на следующем клике */ }
                } else {
                    hint = "Нажмите и говорите"
                }
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim().orEmpty()
                val num = NumberParser.extract(text)
                hint = when {
                    num != null -> {
                        addNumber(num)
                        "Добавлено: ${NumberParser.format(num)}"
                    }
                    text.isNotEmpty() -> "Не расслышал число: «$text»"
                    else -> hint
                }
                if (!manualStop) {
                    try {
                        speechRecognizer.startListening(recognizerIntent)
                        listening = true
                    } catch (e: Exception) { listening = false }
                } else {
                    listening = false
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        try {
            speechRecognizer.startListening(recognizerIntent)
            listening = true
        } catch (e: Exception) {
            hint = "Не удалось запустить микрофон"
        }
    }

    fun stopListening() {
        manualStop = true
        speechRecognizer?.stopListening()
        listening = false
        hint = "Нажмите и говорите"
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening() else hint = "Нужен доступ к микрофону"
    }

    fun onMicClick() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (listening) stopListening() else startListening()
    }

    val sum = entries.sumOf { it.value }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF2A2D33), Color(0xFF101114)),
                    radius = 1200f
                )
            )
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .background(
                    Brush.verticalGradient(listOf(CasingLight, Casing, CasingDark)),
                    RoundedCornerShape(28.dp)
                )
                .padding(18.dp)
        ) {
            // eyebrow
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                if (listening) Color(0xFF5FD97E) else MutedCasing,
                                CircleShape
                            )
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "ГОЛОСОВОЙ СУММАТОР",
                        color = MutedCasing,
                        fontSize = 11.sp,
                        letterSpacing = 1.5.sp
                    )
                }
                Text(
                    if (listening) "СЛУШАЮ" else "ГОТОВ",
                    color = MutedCasing,
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp
                )
            }

            // LED window
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LedBg, RoundedCornerShape(14.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Text(
                    "СУММА ПОСЛЕДНИХ $CAP",
                    color = LedDim,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
                Text(
                    NumberParser.format(sum),
                    color = LedText,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${entries.size} / $CAP чисел",
                        color = LedDim,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        entries.lastOrNull()?.let { "+${NumberParser.format(it.value)}" } ?: "—",
                        color = LedDim,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // paper tape
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 210.dp)
                    .background(Paper, RoundedCornerShape(4.dp))
                    .padding(14.dp)
            ) {
                Text(
                    "ЛЕНТА ВВОДА",
                    color = MutedInk,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(0.dp, PaperShadow)
                        .padding(bottom = 8.dp)
                )
                if (entries.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Продиктуйте или введите первое число",
                            color = MutedInk,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    val ordered = entries.reversed()
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        itemsIndexed(ordered, key = { _, e -> e.id }) { i, e ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "#${entries.size - i}",
                                    color = MutedInk,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    (if (e.value >= 0) "+" else "") + NumberParser.format(e.value),
                                    color = Ink,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // mic button
            Box(contentAlignment = Alignment.Center, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                if (listening) {
                    val infinite = rememberInfiniteTransition(label = "pulse")
                    val scale by infinite.animateFloat(
                        initialValue = 0.9f,
                        targetValue = 1.5f,
                        animationSpec = infiniteRepeatable(
                            tween(1600, easing = LinearEasing),
                            RepeatMode.Restart
                        ),
                        label = "scale"
                    )
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .scale(scale)
                            .border(2.dp, AccentRed, CircleShape)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .background(
                            Brush.radialGradient(
                                colors = if (listening)
                                    listOf(Color(0xFFFF8F7A), AccentRed, Color(0xFF8A2F27))
                                else
                                    listOf(Color(0xFFFFCF7A), LedText, Color(0xFFC97B12))
                            ),
                            CircleShape
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onMicClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (listening) "■" else "●",
                        color = LedBg,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                hint,
                color = MutedCasing,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))

            // manual input
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = manualText,
                    onValueChange = { manualText = it },
                    placeholder = { Text("Ввести число вручную", fontSize = 14.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CasingDark,
                        unfocusedContainerColor = CasingDark,
                        focusedTextColor = MutedCasing,
                        unfocusedTextColor = MutedCasing,
                        focusedBorderColor = LedText,
                        unfocusedBorderColor = Color(0xFF3A3D44)
                    ),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        val n = manualText.replace(',', '.').toDoubleOrNull()
                        if (n != null) {
                            addNumber(n)
                            manualText = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CasingLight)
                ) {
                    Text("+", fontSize = 18.sp, color = MutedCasing)
                }
            }

            Spacer(Modifier.height(10.dp))

            // bottom row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { if (entries.isNotEmpty()) entries = entries.dropLast(1) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MutedCasing)
                ) { Text("Отменить", fontSize = 12.sp) }

                OutlinedButton(
                    onClick = { entries = emptyList() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE58077))
                ) { Text("Сброс", fontSize = 12.sp) }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Держит в памяти последние $CAP чисел. При ${CAP + 1}-м число самое старое уходит с ленты.\nДля голоса нужен доступ к микрофону.",
                color = Color(0xFF5C5F66),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
