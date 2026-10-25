package com.eventoscomunitarios.app.features.edit_event

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEventScreen(
    onCloseClick: () -> Unit,
    onChangesSaved: () -> Unit,
    viewModel: EditEventViewModel = viewModel()
) {
    val context = LocalContext.current

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> viewModel.photoUri = uri }
    )

    val calendar = remember { Calendar.getInstance() }
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val dayStr = dayOfMonth.toString().padStart(2, '0')
            val monthStr = (month + 1).toString().padStart(2, '0')
            viewModel.fecha = "$dayStr/$monthStr/$year"
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    val timePickerDialog = TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            val amPm = if (hourOfDay >= 12) "p.m." else "a.m."
            val hour12 = if (hourOfDay % 12 == 0) 12 else hourOfDay % 12
            val minuteStr = minute.toString().padStart(2, '0')
            viewModel.hora = "$hour12:$minuteStr $amPm"
        },
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE),
        false
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editar evento") },
                navigationIcon = {
                    IconButton(onClick = onCloseClick) { Icon(Icons.Default.Close, contentDescription = "Cerrar") }
                }
            )
        },
        bottomBar = {
            Button(
                onClick = { viewModel.guardarCambios() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C6898)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Guardar cambios")
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(
                            if (viewModel.photoUri != null) Color(0xFFE4F0FF) else Color(0xFFF3F4F6),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (viewModel.photoUri != null) "✅" else "📷", fontSize = 24.sp)
                        Text(
                            text = if (viewModel.photoUri != null) "Foto seleccionada" else "Actualizar foto de portada",
                            color = Color(0xFF1E3A8A),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = viewModel.titulo,
                    onValueChange = { viewModel.titulo = it },
                    label = { Text("Título del evento") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = viewModel.categoria,
                    onValueChange = { viewModel.categoria = it },
                    label = { Text("Categoría") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = viewModel.fecha,
                            onValueChange = { },
                            label = { Text("Fecha") },
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(modifier = Modifier.matchParentSize().clickable { datePickerDialog.show() })
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = viewModel.hora,
                            onValueChange = { },
                            label = { Text("Hora") },
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(modifier = Modifier.matchParentSize().clickable { timePickerDialog.show() })
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = viewModel.direccion,
                    onValueChange = { viewModel.direccion = it },
                    label = { Text("Dirección") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = viewModel.descripcion,
                    onValueChange = { viewModel.descripcion = it },
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = viewModel.cupo,
                        onValueChange = { viewModel.cupo = it },
                        label = { Text("Cupo máximo") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = viewModel.precio,
                        onValueChange = { viewModel.precio = it },
                        label = { Text("Precio") },
                        modifier = Modifier.weight(1f),
                        enabled = !viewModel.isGratuito
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Evento gratuito", fontWeight = FontWeight.Bold)
                        Text("Sin costo de inscripción", fontSize = 12.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = viewModel.isGratuito,
                        onCheckedChange = { viewModel.onGratuitoChanged(it) }
                    )
                }
                Spacer(Modifier.height(80.dp))
            }

            if (viewModel.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF2C6898))
                }
            }
        }

        if (viewModel.showSuccessDialog) {
            AlertDialog(
                onDismissRequest = { },
                containerColor = Color(0xFFE0F2FE),
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("¡Cambios guardados!", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                },
                text = {
                    Text("El evento fue actualizado correctamente.", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.onDialogDismissed()
                        onChangesSaved()
                    }) {
                        Text("Aceptar")
                    }
                }
            )
        }
    }
}
