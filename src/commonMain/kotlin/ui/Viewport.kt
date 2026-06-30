package ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GlassViewport(
    loadedImage: ImageBitmap?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).blur(20.dp).background(Color.White.copy(alpha = 0.45f)))
        Box(
            modifier = Modifier.fillMaxSize().border(1.2.dp, androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color.White.copy(0.8f), Color.White.copy(0.15f))), RoundedCornerShape(24.dp)).padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (loadedImage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                        .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = loadedImage,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Text(
                    text = "Hello!\n\nThis is SC Editor, an app to view Supercell graphic files format.",
                    color = Color(0xFF94A3B8),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
