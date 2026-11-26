package com.abraham.opsound.ui

import android.graphics.Matrix
import android.net.Uri
import android.view.TextureView
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
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
import kotlin.math.sin
import kotlin.random.Random

/**
 * "Portada viva": la imagen se acerca y se mueve muy lento, pasa un destello de luz
 * y flotan partículas, para que se sienta viva como en Apple Music.
 */
@Composable
fun LivingCover(cover: ImageBitmap, animated: Boolean, modifier: Modifier = Modifier) {
    if (!animated) {
        Image(cover, contentDescription = "Portada", contentScale = ContentScale.Crop, modifier = modifier)
        return
    }
    val t = rememberInfiniteTransition(label = "portada-viva")
    val zoom = t.animateFloat(1.0f, 1.09f, infiniteRepeatable(tween(16_000, easing = LinearEasing), RepeatMode.Reverse), label = "zoom")
    val drift = t.animateFloat(-1f, 1f, infiniteRepeatable(tween(22_000, easing = LinearEasing), RepeatMode.Reverse), label = "movimiento")
    val sweep = t.animateFloat(-0.6f, 2.4f, infiniteRepeatable(tween(9_000, easing = LinearEasing)), label = "destello")
    val clock = t.animateFloat(0f, 1f, infiniteRepeatable(tween(30_000, easing = LinearEasing)), label = "reloj")
    val particles = remember { List(28) { Particle(Random(it * 7919)) } }

    Box(modifier) {
        Image(
            cover,
            contentDescription = "Portada",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Se lee dentro de graphicsLayer: solo se redibuja, no recalcula la pantalla.
                    scaleX = zoom.value; scaleY = zoom.value
                    translationX = drift.value * size.width * 0.025f
                    translationY = -drift.value * size.height * 0.015f
                },
        )
        Canvas(Modifier.fillMaxSize()) {
            // Destello de luz diagonal.
            val p = sweep.value
            if (p in -0.4f..1.4f) {
                val cx = size.width * p
                drawRect(
                    Brush.linearGradient(
                        0f to Color.Transparent,
                        0.5f to Color.White.copy(alpha = 0.10f),
                        1f to Color.Transparent,
                        start = Offset(cx - size.width * 0.35f, 0f),
                        end = Offset(cx + size.width * 0.05f, size.height),
                    )
                )
            }
            // Partículas que suben despacio y titilan.
            val c = clock.value
            particles.forEach { pt ->
                val y = ((pt.y - c * pt.speed) % 1f + 1f) % 1f
                val x = pt.x + sin((c * pt.speed * 6 + pt.phase) * 2 * PI).toFloat() * 0.015f
                val twinkle = 0.5f + 0.5f * sin((c * 40 * pt.speed + pt.phase) * 2 * PI).toFloat()
                drawCircle(
                    Color.White.copy(alpha = pt.alpha * twinkle),
                    radius = pt.radius * density,
                    center = Offset(x * size.width, y * size.height),
                )
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
