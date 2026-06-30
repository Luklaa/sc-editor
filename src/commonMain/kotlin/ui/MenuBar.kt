//package ui
//
//import androidx.compose.foundation.background
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.material3.DropdownMenu
//import androidx.compose.material3.DropdownMenuItem
//import androidx.compose.material3.HorizontalDivider
//import androidx.compose.material3.Text
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.draw.clip
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//
//@Composable
//fun GlassMenuBar(
//    onOpenClick: () -> Unit,
//    onCloseClick: () -> Unit,
//    onCloseAllClick: () -> Unit,
//    onExitClick: () -> Unit,
//    modifier: Modifier = Modifier
//) {
//    var fileMenuExpanded by remember { mutableStateOf(false) }
//    var placeholderMenuExpanded by remember { mutableStateOf<String?>(null) }
//
//    GlassBox(
//        modifier = modifier.height(44.dp),
//        alpha = 0.5f,
//        cornerRadius = 10,
//        contentPadding = 0.dp
//    ) {
//        Row(
//            verticalAlignment = Alignment.CenterVertically,
//            horizontalArrangement = Arrangement.Start,
//            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)
//        ) {
//            Box {
//                MenuButton("File") { fileMenuExpanded = true }
//                DropdownMenu(
//                    expanded = fileMenuExpanded,
//                    onDismissRequest = { fileMenuExpanded = false },
//                    modifier = Modifier.background(Color.White.copy(alpha = 0.9f))
//                ) {
//                    DropdownMenuItem(text = { Text("Open") }, onClick = { fileMenuExpanded = false; onOpenClick() })
//                    DropdownMenuItem(text = { Text("Save") }, onClick = { fileMenuExpanded = false }, enabled = false)
//                    DropdownMenuItem(text = { Text("Save As...") }, onClick = { fileMenuExpanded = false }, enabled = false)
//                    DropdownMenuItem(text = { Text("Close") }, onClick = { fileMenuExpanded = false; onCloseClick() })
//                    DropdownMenuItem(text = { Text("Close All") }, onClick = { fileMenuExpanded = false; onCloseAllClick() })
//                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
//                    DropdownMenuItem(text = { Text("Open Screenshots folder") }, onClick = { fileMenuExpanded = false }, enabled = false)
//                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
//                    DropdownMenuItem(text = { Text("Exit") }, onClick = { fileMenuExpanded = false; onExitClick() })
//                }
//            }
//
//            val menus = listOf("Edit", "View", "Options", "Help")
//            menus.forEach { name ->
//                Box {
//                    MenuButton(name) { placeholderMenuExpanded = name }
//                    DropdownMenu(
//                        expanded = placeholderMenuExpanded == name,
//                        onDismissRequest = { placeholderMenuExpanded = null },
//                        modifier = Modifier.background(Color.White.copy(alpha = 0.9f))
//                    ) {
//                        DropdownMenuItem(text = { Text("(Пусто)") }, onClick = { placeholderMenuExpanded = null })
//                    }
//                }
//            }
//        }
//    }
//}
//
//@Composable
//fun MenuButton(text: String, onClick: () -> Unit) {
//    Box(
//        modifier = Modifier
//            .clip(RoundedCornerShape(6.dp))
//            .clickable { onClick() }
//            .padding(horizontal = 12.dp, vertical = 6.dp),
//        contentAlignment = Alignment.Center
//    ) {
//        Text(text = text, color = Color(0xFF1E293B), fontSize = 13.sp)
//    }
//}
