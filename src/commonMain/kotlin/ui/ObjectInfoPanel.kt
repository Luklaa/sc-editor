package ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GlassObjectInfo(
    selectedObject: ScObjectItem?,
    selectedTexture: ScTextureItem? = null,
    tab: OpenedTab,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
        if (selectedObject == null && selectedTexture != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.surface.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Texture ${selectedTexture.index}", color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Size: ${selectedTexture.width} × ${selectedTexture.height}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text("Pixels format: ${selectedTexture.format}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text("File: ${tab.name}", color = MaterialTheme.colorScheme.outline, fontSize = 11.sp)
                }
            }
        } else if (selectedObject != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.surface.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(selectedObject.name.ifEmpty { selectedObject.type }, color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("ID: ${selectedObject.id}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text("Type: ${selectedObject.type}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text("File: ${tab.name}", color = MaterialTheme.colorScheme.outline, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text("Summary", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("Objects: ${tab.objects.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text("Textures: ${tab.textures.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text("Container version: ${tab.containerVersion}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(12.dp))

            when (selectedObject.type) {
                "MovieClip" -> {
                    Text("MovieClip", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("• placeholder text1", color = MaterialTheme.colorScheme.outline, fontSize = 11.sp)
                    Text("• placeholder text2", color = MaterialTheme.colorScheme.outline, fontSize = 11.sp)
                }
                "Shape" -> {
                    Text("Shape", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("• placeholder text1", color = MaterialTheme.colorScheme.outline, fontSize = 11.sp)
                    Text("• placeholder text2", color = MaterialTheme.colorScheme.outline, fontSize = 11.sp)
                }
                "TextField" -> {
                    Text("TextField", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("• placeholder text1", color = MaterialTheme.colorScheme.outline, fontSize = 11.sp)
                }
            }
        } else {
            Text("placeholder text1", color = MaterialTheme.colorScheme.outlineVariant, fontSize = 12.sp)
        }
    }
}
