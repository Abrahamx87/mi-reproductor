package com.abraham.opsound.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Posición de la canción que avanza cuadro por cuadro (120 Hz en tu S25),
 * aunque el reproductor solo reporte la posición unas veces por segundo.
 */
@Stable
class SmoothPosition {
    /** Lo que se dibuja en pantalla, en milisegundos. */
    var displayMs by mutableLongStateOf(0L)
        internal set

    internal var anchorMs = 0L
    internal var anchorNanos = 0L
    internal var pendingSeekMs: Long? = null
    internal var pendingSince = 0L
    internal val seekOffset = Animatable(0f)
}

@Composable
fun rememberSmoothPosition(position: () -> Long, durationMs: Long, isPlaying: Boolean): SmoothPosition {
    val state = remember { SmoothPosition() }
    val playing by rememberUpdatedState(isPlaying)
    val duration by rememberUpdatedState(durationMs)
    val reportedPosition by rememberUpdatedState(position)

    LaunchedEffect(state) {
        while (true) {
            val animating = state.seekOffset.isRunning
            if (!playing && !animating) {
                // En pausa no hace falta dibujar 120 veces por segundo.
                val reported = reportedPosition()
                if (state.pendingSeekMs == null) state.anchorMs = reported
                state.anchorNanos = 0L
                state.displayMs = (state.pendingSeekMs ?: reported).coerceIn(0L, duration.coerceAtLeast(0L))
                delay(60)
                continue
            }
            withFrameNanos { now ->
                val reported = reportedPosition()
                if (state.anchorNanos == 0L) {
                    state.anchorNanos = now
                    if (state.pendingSeekMs == null) state.anchorMs = reported
                }
                val estimate = state.anchorMs + if (playing) (now - state.anchorNanos) / 1_000_000 else 0L

                val pending = state.pendingSeekMs
                if (pending != null) {
                    // Espera a que el reproductor llegue al punto elegido antes de volver a sincronizar.
                    if (abs(reported - pending) < 1_000 || now - state.pendingSince > 1_500_000_000L) {
                        state.pendingSeekMs = null
                    }
                } else if (abs(estimate - reported) > 350 || !playing) {
                    state.anchorMs = reported
                    state.anchorNanos = now
                }

                val synced = state.anchorMs + if (playing) (now - state.anchorNanos) / 1_000_000 else 0L
                val shown = synced + state.seekOffset.value.toLong()
                state.displayMs = shown.coerceIn(0L, duration.coerceAtLeast(0L))
            }
        }
    }
    return state
}

/** Barra delgada con bolita: avanza fluido, se puede tocar o arrastrar, y al tocar se desliza suave. */
@Composable
fun SmoothSeekBar(
    smooth: SmoothPosition,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    trackHeight: Float = 1.5f,
) {
    val scope = rememberCoroutineScope()
    val seek by rememberUpdatedState(onSeek)
    val duration by rememberUpdatedState(durationMs)
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val thumbSize by animateDpAsState(if (dragFraction != null) 18.dp else 12.dp, tween(150), label = "bolita")

    fun jumpTo(fraction: Float) {
        val target = (fraction.coerceIn(0f, 1f) * duration).toLong()
        val from = smooth.displayMs
        smooth.pendingSeekMs = target
        smooth.pendingSince = System.nanoTime()
        smooth.anchorMs = target
        smooth.anchorNanos = 0L
        seek(target)
        scope.launch {
            smooth.seekOffset.snapTo((from - target).toFloat())
            smooth.seekOffset.animateTo(0f, tween(280, easing = FastOutSlowInEasing))
        }
    }

    Canvas(
        modifier
            .height(28.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset -> jumpTo(offset.x / size.width) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset -> dragFraction = (offset.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        dragFraction?.let { f ->
                            // Al soltar, sin animación: la bolita ya está donde la dejaste.
                            val target = (f * duration).toLong()
                            smooth.pendingSeekMs = target
                            smooth.pendingSince = System.nanoTime()
                            smooth.anchorMs = target
                            smooth.anchorNanos = 0L
                            smooth.displayMs = target
                            seek(target)
                        }
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null },
                ) { change, _ ->
                    change.consume()
                    dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                }
            }
    ) {
        val fraction = dragFraction
            ?: if (duration > 0) (smooth.displayMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
        val y = size.height / 2f
        val stroke = trackHeight.dp.toPx()
        val x = fraction * size.width
        drawLine(Color.White.copy(alpha = 0.3f), Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Round)
        drawLine(Color.White, Offset(0f, y), Offset(x, y), stroke, StrokeCap.Round)
        drawCircle(Color.White, radius = thumbSize.toPx() / 2f, center = Offset(x.coerceIn(0f, size.width), y))
    }
}

/** Línea de progreso del mini reproductor (sin bolita). */
@Composable
fun SmoothProgressLine(smooth: SmoothPosition, durationMs: Long, modifier: Modifier = Modifier) {
    val duration by rememberUpdatedState(durationMs)
    Canvas(modifier.height(2.dp)) {
        val fraction = if (duration > 0) (smooth.displayMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
        drawRect(Color.White.copy(alpha = 0.15f))
        drawRect(Color.White, size = size.copy(width = size.width * fraction))
    }
}
