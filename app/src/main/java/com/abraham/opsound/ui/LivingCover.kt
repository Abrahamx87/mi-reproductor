package com.abraham.opsound.ui

import android.graphics.Matrix
import android.net.Uri
import android.view.TextureView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Las animaciones de portada que puedes elegir. */
enum class CoverAnimation(val label: String, val description: String) {
    LIVING("Viva", "Zoom lento, destello de luz y partículas"),
    BREATHE("Respirar", "La portada late suavecito, como si respirara"),
    TILT("3D", "Se inclina despacio como una tarjeta 3D con reflejo"),
    VINYL("Vinilo", "Se vuelve un disco que gira mientras suena"),
    AURORA("Aurora", "Luces de colores que flotan sobre la portada"),
    SNOW("Nieve", "Copos que caen despacio sobre la portada"),
    RANDOM("Sorpresa", "Una animación distinta en cada canción"),
    NONE("Sin animación", "La portada quieta");

    companion object {
        fun from(name: String) = entries.firstOrNull { it.name == name } ?: LIVING
        private val animated = listOf(LIVING, BREATHE, TILT, VINYL, AURORA, SNOW)
        /** "Sorpresa": siempre la misma para la misma canción. */
        fun resolve(style: CoverAnimation, songId: Long): CoverAnimation =
            if (style == RANDOM) animated[Math.floorMod(songId, animated.size.toLong()).toInt()] else style
    }
}

/** Portada con la animación elegida. */
@Composable
fun LivingCover(cover: ImageBitmap, style: CoverAnimation, isPlaying: Boolean, modifier: Modifier = Modifier) {
    when (style) {
        CoverAnimation.NONE, CoverAnimation.RANDOM ->
            Image(cover, contentDescription = "Portada", contentScale = ContentScale.Crop, modifier = modifier)
        CoverAnimation.LIVING -> LivingStyle(cover, modifier)
        CoverAnimation.BREATHE -> BreatheStyle(cover, modifier)
        CoverAnimation.TILT -> TiltStyle(cover, modifier)
        CoverAnimation.VINYL -> VinylStyle(cover, isPlaying, modifier)
        CoverAnimation.AURORA -> AuroraStyle(cover, modifier)
        CoverAnimation.SNOW -> SnowStyle(cover, modifier)
    }
}

// ── Viva: zoom lento + destello + partículas que suben
@Composable
private fun LivingStyle(cover: ImageBitmap, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "viva")
    val zoom = t.animateFloat(1.0f, 1.09f, infiniteRepeatable(tween(16_000, easing = LinearEasing), RepeatMode.Reverse), label = "zoom")
    val drift = t.animateFloat(-1f, 1f, infiniteRepeatable(tween(22_000, easing = LinearEasing), RepeatMode.Reverse), label = "movimiento")
    val sweep = t.animateFloat(-0.6f, 2.4f, infiniteRepeatable(tween(9_000, easing = LinearEasing)), label = "destello")
    val clock = t.animateFloat(0f, 1f, infiniteRepeatable(tween(30_000, easing = LinearEasing)), label = "reloj")
    val particles = remember { List(28) { Particle(Random(it * 7919)) } }
    Box(modifier) {
        Image(
            cover, contentDescription = "Portada", contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = zoom.value; scaleY = zoom.value
                translationX = drift.value * size.width * 0.025f
                translationY = -drift.value * size.height * 0.015f
            },
        )
        Canvas(Modifier.fillMaxSize()) {
            val p = sweep.value
            if (p in -0.4f..1.4f) {
                val cx = size.width * p
                drawRect(
                    Brush.linearGradient(
                        0f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.10f), 1f to Color.Transparent,
                        start = Offset(cx - size.width * 0.35f, 0f), end = Offset(cx + size.width * 0.05f, size.height),
                    )
                )
            }
            val c = clock.value
            particles.forEach { pt ->
                val y = ((pt.y - c * pt.speed) % 1f + 1f) % 1f
                val x = pt.x + sin((c * pt.speed * 6 + pt.phase) * 2 * PI).toFloat() * 0.015f
                val twinkle = 0.5f + 0.5f * sin((c * 40 * pt.speed + pt.phase) * 2 * PI).toFloat()
                drawCircle(Color.White.copy(alpha = pt.alpha * twinkle), radius = pt.radius * density, center = Offset(x * size.width, y * size.height))
            }
        }
    }
}

// ── Respirar: late suavecito con un brillo que va y viene
@Composable
private fun BreatheStyle(cover: ImageBitmap, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "respirar")
    val beat = t.animateFloat(0f, 1f, infiniteRepeatable(tween(2_400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "latido")
    Box(modifier) {
        Image(
            cover, contentDescription = "Portada", contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val s = 1f + beat.value * 0.045f
                scaleX = s; scaleY = s
            },
        )
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.10f * beat.value), Color.Transparent),
                    center = center, radius = size.minDimension * 0.75f,
                )
            )
        }
    }
}

// ── 3D: se inclina despacio, con un reflejo que sigue la inclinación
@Composable
private fun TiltStyle(cover: ImageBitmap, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "3d")
    val a = t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "angulo")
    Box(
        modifier.graphicsLayer {
            rotationY = sin(a.value) * 9f
            rotationX = cos(a.value * 0.8f) * 6f
            cameraDistance = 14f * density
        }
    ) {
        Image(cover, contentDescription = "Portada", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            val x = (0.5f + sin(a.value) * 0.6f) * size.width
            drawRect(
                Brush.linearGradient(
                    0f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.16f), 1f to Color.Transparent,
                    start = Offset(x - size.width * 0.5f, 0f), end = Offset(x + size.width * 0.2f, size.height),
                )
            )
        }
    }
}

// ── Vinilo: disco que gira solo cuando suena
@Composable
private fun VinylStyle(cover: ImageBitmap, isPlaying: Boolean, modifier: Modifier) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            rotation.animateTo(rotation.value + 360f, tween(8_000, easing = LinearEasing))
            rotation.snapTo(rotation.value % 360f)
        }
    }
    Box(modifier.clip(CircleShape).graphicsLayer { rotationZ = rotation.value }) {
        Image(cover, contentDescription = "Portada", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            // Surcos del disco
            for (i in 1..9) {
                drawCircle(Color.Black.copy(alpha = 0.10f), radius = r * (0.35f + i * 0.07f), style = Stroke(width = 1.2f * density))
            }
            // Brillo del vinilo
            drawCircle(
                Brush.sweepGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.12f), Color.Transparent, Color.White.copy(alpha = 0.08f), Color.Transparent),
                    center = center,
                ),
                radius = r,
            )
            // Centro del disco
            drawCircle(Color(0xFF0B0F14), radius = r * 0.16f)
            drawCircle(Color.White.copy(alpha = 0.25f), radius = r * 0.16f, style = Stroke(width = 2f * density))
            drawCircle(Color.Black, radius = r * 0.035f)
        }
    }
}

// ── Aurora: luces de colores que flotan encima
@Composable
private fun AuroraStyle(cover: ImageBitmap, modifier: Modifier) {
    val accent = Accent
    val t = rememberInfiniteTransition(label = "aurora")
    val a = t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(12_000, easing = LinearEasing)), label = "aurora")
    Box(modifier) {
        Image(cover, contentDescription = "Portada", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            val colors = listOf(accent, Color(0xFFB388FF), Color(0xFF64FFDA))
            colors.forEachIndexed { i, col ->
                val ph = a.value + i * 2.1f
                val c = Offset(
                    size.width * (0.5f + 0.42f * cos(ph)),
                    size.height * (0.5f + 0.38f * sin(ph * 1.3f)),
                )
                drawCircle(
                    Brush.radialGradient(listOf(col.copy(alpha = 0.38f), Color.Transparent), center = c, radius = size.minDimension * 0.55f),
                    radius = size.minDimension * 0.55f, center = c, blendMode = BlendMode.Screen,
                )
            }
        }
    }
}

// ── Nieve: copos que caen despacio
@Composable
private fun SnowStyle(cover: ImageBitmap, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "nieve")
    val clock = t.animateFloat(0f, 1f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "reloj")
    val flakes = remember { List(45) { Particle(Random(it * 104729 + 3)) } }
    Box(modifier) {
        Image(cover, contentDescription = "Portada", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            val c = clock.value
            flakes.forEach { f ->
                val y = (f.y + c * f.speed * 1.5f) % 1f
                val x = f.x + sin((c * 8 * f.speed + f.phase) * 2 * PI).toFloat() * 0.03f
                drawCircle(Color.White.copy(alpha = 0.35f + f.alpha), radius = (f.radius + 0.6f) * density, center = Offset(x * size.width, y * size.height))
            }
        }
    }
}

private class Particle(r: Random) {
    val x = r.nextFloat()
    val y = r.nextFloat()
    val speed = 0.6f + r.nextFloat() * 1.6f
    val radius = 0.8f + r.nextFloat() * 1.8f
    val alpha = 0.12f + r.nextFloat() * 0.35f
    val phase = r.nextFloat()
}

/** Portada en video: se repite en bucle, sin sonido, recortada en cuadrado. */
@Composable
fun VideoCover(file: File, playing: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(player, playing) { player.playWhenReady = playing }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).also { view ->
                player.setVideoTextureView(view)
                fun crop(video: VideoSize) {
                    if (video.width == 0 || video.height == 0 || view.width == 0 || view.height == 0) return
                    val vw = view.width.toFloat()
                    val vh = view.height.toFloat()
                    val videoRatio = video.width * video.pixelWidthHeightRatio / video.height
                    val viewRatio = vw / vh
                    val (sx, sy) = if (videoRatio > viewRatio) (videoRatio / viewRatio) to 1f else 1f to (viewRatio / videoRatio)
                    view.setTransform(Matrix().apply { setScale(sx, sy, vw / 2f, vh / 2f) })
                }
                player.addListener(object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: VideoSize) = crop(videoSize)
                })
                view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> crop(player.videoSize) }
            }
        },
    )
}
