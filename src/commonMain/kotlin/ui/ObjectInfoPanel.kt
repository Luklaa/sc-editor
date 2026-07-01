package ui

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
                    .background(Color.White.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Texture ${selectedTexture.index}", color = Color(0xFF0F172A), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Размер холста: ${selectedTexture.width} × ${selectedTexture.height}", color = Color(0xFF475569), fontSize = 12.sp)
                    Text("Формат пикселей: ${selectedTexture.format}", color = Color(0xFF475569), fontSize = 12.sp)
                    Text("Файл: ${tab.name}", color = Color(0xFF64748B), fontSize = 11.sp)
                }
            }
        } else if (selectedObject != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(selectedObject.name.ifEmpty { selectedObject.type }, color = Color(0xFF0F172A), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("ID: ${selectedObject.id}", color = Color(0xFF475569), fontSize = 12.sp)
                    Text("Тип: ${selectedObject.type}", color = Color(0xFF475569), fontSize = 12.sp)
                    Text("Файл: ${tab.name}", color = Color(0xFF64748B), fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text("Текущая сводка", color = Color(0xFF1E293B), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("Объектов в файле: ${tab.objects.size}", color = Color(0xFF475569), fontSize = 12.sp)
            Text("Текстур в файле: ${tab.textures.size}", color = Color(0xFF475569), fontSize = 12.sp)
            Text("Версия контейнера: ${tab.containerVersion}", color = Color(0xFF475569), fontSize = 12.sp)

            Spacer(modifier = Modifier.height(12.dp))

            when (selectedObject.type) {
                "MovieClip" -> {
                    Text("Анимация", color = Color(0xFF1E293B), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("• В старом редакторе это был отдельный MovieClipPropertyPanel", color = Color(0xFF64748B), fontSize = 11.sp)
                    Text("• Здесь будет подключена информация по кадрам и таймлайну", color = Color(0xFF64748B), fontSize = 11.sp)
                }
                "Shape" -> {
                    Text("Форма", color = Color(0xFF1E293B), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("• В старом редакторе это был отдельный ShapeInfoPanel", color = Color(0xFF64748B), fontSize = 11.sp)
                    Text("• Поддержка draw commands и выделения деталей будет добавлена позже", color = Color(0xFF64748B), fontSize = 11.sp)
                }
                "TextField" -> {
                    Text("Текст", color = Color(0xFF1E293B), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("• Текстовые элементы будут расширены по аналогии со старой панелью информации", color = Color(0xFF64748B), fontSize = 11.sp)
                }
            }
        } else {
            Text("Выберите объект в списке для просмотра свойств", color = Color(0xFF94A3B8), fontSize = 12.sp)
        }
    }
}
