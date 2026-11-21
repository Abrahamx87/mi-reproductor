package com.abraham.opsound.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.abraham.opsound.R

/** Letra redondeada estilo BandLab (Montserrat, licencia OFL). */
val Montserrat = FontFamily(
    Font(R.font.montserrat_medium, FontWeight.Normal),
    Font(R.font.montserrat_medium, FontWeight.Medium),
    Font(R.font.montserrat_semibold, FontWeight.SemiBold),
    Font(R.font.montserrat_bold, FontWeight.Bold),
)

private fun TextStyle.m() = copy(fontFamily = Montserrat)

val OpsoundTypography: Typography = Typography().let { d ->
    Typography(
        displayLarge = d.displayLarge.m(),
        displayMedium = d.displayMedium.m(),
        displaySmall = d.displaySmall.m(),
        headlineLarge = d.headlineLarge.m(),
        headlineMedium = d.headlineMedium.m(),
        headlineSmall = d.headlineSmall.m(),
        titleLarge = d.titleLarge.m(),
        titleMedium = d.titleMedium.m(),
        titleSmall = d.titleSmall.m(),
        bodyLarge = d.bodyLarge.m(),
        bodyMedium = d.bodyMedium.m(),
        bodySmall = d.bodySmall.m(),
        labelLarge = d.labelLarge.m(),
        labelMedium = d.labelMedium.m(),
        labelSmall = d.labelSmall.m(),
    )
}
