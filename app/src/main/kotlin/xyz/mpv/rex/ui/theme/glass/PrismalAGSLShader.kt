package xyz.mpv.rex.ui.theme.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.ui.theme.maxstream.MaxStreamTheme

/**
 * PrismalAGSL Shader string for Android 13+ (API 33+)
 * Performs liquid glass refraction, specular highlight sweep, chromatic dispersion, and frosted tinting.
 */
private const val PRISMAL_AGSL_SHADER_CODE = """
    uniform shader composer;
    uniform float2 size;
    uniform float time;
    uniform float refractionIntensity;
    uniform float dispersion;
    uniform float4 tintColor;

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / size;
        
        // Fluid wave refraction vector
        float wave = sin(uv.x * 12.0 + time * 1.5) * cos(uv.y * 12.0 + time * 1.5) * refractionIntensity;
        float2 refractedCoords = fragCoord + float2(wave * size.x * 0.02, wave * size.y * 0.02);
        
        // Chromatic dispersion offsets (Cyan & Magenta split)
        float2 offsetR = float2(dispersion * 3.0, -dispersion * 1.5);
        float2 offsetB = float2(-dispersion * 3.0, dispersion * 1.5);
        
        half4 c = composer.eval(refractedCoords);
        half4 cR = composer.eval(refractedCoords + offsetR);
        half4 cB = composer.eval(refractedCoords + offsetB);
        
        // Specular light sweep across top-left to bottom-right
        float specularSweep = max(0.0, 1.0 - length(uv - float2(0.2 + sin(time * 0.5) * 0.1, 0.2)));
        specularSweep = pow(specularSweep, 3.0) * 0.35;
        
        // Combine chromatic channels
        half4 finalColor = half4(cR.r, c.g, cB.b, c.a);
        
        // Apply liquid glass tint
        finalColor.rgb = mix(finalColor.rgb, tintColor.rgb, tintColor.a);
        finalColor.rgb += half3(specularSweep * 0.8, specularSweep * 0.9, specularSweep * 1.0);
        
        return finalColor;
    }
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object PrismalShaderHolder {
    val runtimeShader by lazy {
        RuntimeShader(PRISMAL_AGSL_SHADER_CODE)
    }
}

/**
 * Modifier that applies PrismalAGSL Shader liquid glass effect on API 33+ (Android 13+),
 * or hardware layer blur & gradient shimmer fallback on pre-API 33 devices.
 */
fun Modifier.prismalLiquidGlass(
    shape: Shape = RoundedCornerShape(20.dp),
    refractionIntensity: Float = 0.5f,
    dispersion: Float = 1.2f,
    tint: Color = Color(0x3B0C0F17),
    borderColor: Color = MaxStreamTheme.GlassBorder,
    borderWidth: Dp = 1.dp,
    animatedSweep: Boolean = true
): Modifier = this.then(
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Modifier
            .clip(shape)
            .graphicsLayer {
                val shader = PrismalShaderHolder.runtimeShader
                shader.setFloatUniform("size", size.width, size.height)
                shader.setFloatUniform("time", if (animatedSweep) System.currentTimeMillis() % 1000000 / 1000f else 0f)
                shader.setFloatUniform("refractionIntensity", refractionIntensity)
                shader.setFloatUniform("dispersion", dispersion)
                shader.setFloatUniform(
                    "tintColor",
                    tint.red, tint.green, tint.blue, tint.alpha
                )
                renderEffect = RenderEffect
                    .createRuntimeShaderEffect(shader, "composer")
                    .asComposeRenderEffect()
            }
            .border(
                width = borderWidth,
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaxStreamTheme.ElectricCyan.copy(alpha = 0.6f),
                        Color.White.copy(alpha = 0.25f),
                        MaxStreamTheme.CrimsonAccent.copy(alpha = 0.5f)
                    )
                ),
                shape = shape
            )
    } else {
        // Fallback for pre-Android 13: Hardware layer translucent acrylic with specular border
        Modifier
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        tint,
                        Color(0xF207080B)
                    )
                )
            )
            .border(
                width = borderWidth,
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaxStreamTheme.ElectricCyan.copy(alpha = 0.5f),
                        Color.White.copy(alpha = 0.2f),
                        MaxStreamTheme.CrimsonAccent.copy(alpha = 0.4f)
                    )
                ),
                shape = shape
            )
    }
)
