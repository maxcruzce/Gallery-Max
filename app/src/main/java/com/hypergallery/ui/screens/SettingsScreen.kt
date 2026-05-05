package com.hypergallery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.material3.LinearProgressIndicator
import com.hypergallery.ui.theme.Primary
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypergallery.ui.theme.HeaderBg
import com.hypergallery.ui.theme.OnSurface
import com.hypergallery.ui.theme.Surface

@Composable
fun SettingsScreen(
    isDarkMode: Boolean,
    isWhatsAppParserEnabled: Boolean,
    whatsappBackupName: String?,
    importProgress: Float?,
    saveExifData: Boolean = true,
    isFaceRecognitionEnabled: Boolean = true,
    isAnalyzing: Boolean = false,
    analysisProgress: Float = 0f,
    analyzedCount: Int = 0,
    totalToAnalyze: Int = 0,
    isOptimizing: Boolean = false,
    optimizationStatus: String = "",
    onToggleDarkMode: () -> Unit,
    onToggleWhatsAppParser: () -> Unit,
    onImportBackupClick: () -> Unit,
    onToggleSaveExif: () -> Unit,
    onToggleFaceRecognition: () -> Unit,
    onBackClick: () -> Unit,
    onOptimizePeople: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(HeaderBg)
                .padding(vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = OnSurface
                    )
                }
                Text(
                    text = "Configurações",
                    color = OnSurface,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tema Escuro",
                        color = OnSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Alternar entre tema claro e escuro",
                        color = OnSurface.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = isDarkMode,
                    onCheckedChange = { onToggleDarkMode() }
                )
            }
            HorizontalDivider(color = OnSurface.copy(alpha = 0.1f))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Organizar por WhatsApp",
                        color = OnSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Vincular mídias aos nomes dos contatos via backup .txt",
                        color = OnSurface.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = isWhatsAppParserEnabled,
                    onCheckedChange = { onToggleWhatsAppParser() }
                )
            }
            
            if (isWhatsAppParserEnabled) {
                androidx.compose.material3.Button(
                    onClick = onImportBackupClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Text(if (whatsappBackupName != null) "Trocar Backup: $whatsappBackupName" else "Importar Arquivo de Backup (.txt)")
                }

                importProgress?.let { progress ->
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape),
                        color = com.hypergallery.ui.theme.Primary,
                        trackColor = com.hypergallery.ui.theme.OnSurface.copy(alpha = 0.1f),
                    )
                }
                
                whatsappBackupName?.let { name ->
                    Text(
                        text = "Backup atual: $name",
                        color = OnSurface.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            }

            HorizontalDivider(color = OnSurface.copy(alpha = 0.1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Salvar Alterações de EXIF",
                        color = OnSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Salvar as alterações de descrição e tags no banco de dados",
                        color = OnSurface.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = saveExifData,
                    onCheckedChange = { onToggleSaveExif() }
                )
            }
            
            HorizontalDivider(color = OnSurface.copy(alpha = 0.1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Reconhecimento Facial",
                        color = OnSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Detectar e identificar pessoas nas fotos",
                        color = OnSurface.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = isFaceRecognitionEnabled,
                    onCheckedChange = { onToggleFaceRecognition() }
                )
            }
            
            HorizontalDivider(color = OnSurface.copy(alpha = 0.1f))
            
            // Background Analysis Progress
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                Text(
                    text = "Busca Inteligente (IA)",
                    color = OnSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Processamento de pessoas, objetos e textos em segundo plano",
                    color = OnSurface.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                if (isAnalyzing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = "Analisando mídia... ($analyzedCount/$totalToAnalyze)",
                            color = Primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    LinearProgressIndicator(
                        progress = { analysisProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape),
                        color = Primary,
                        trackColor = OnSurface.copy(alpha = 0.1f)
                    )
                } else {
                    Text(
                        text = if (totalToAnalyze > 0 && analyzedCount >= totalToAnalyze) 
                            "✓ Todo o conteúdo foi processado" 
                            else "Aguardando novas mídias para processar",
                        color = OnSurface.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )
                }
            }

            HorizontalDivider(color = OnSurface.copy(alpha = 0.1f))
            
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                Text(
                    text = "Otimizar Agrupamento",
                    color = OnSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Mesclar automaticamente pessoas com características similares",
                    color = OnSurface.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (optimizationStatus.isNotEmpty()) {
                    Text(
                        text = optimizationStatus,
                        color = Primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (isOptimizing) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .padding(bottom = 12.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape),
                            color = Primary,
                            trackColor = OnSurface.copy(alpha = 0.1f)
                        )
                    }
                }

                androidx.compose.material3.Button(
                    onClick = onOptimizePeople,
                    enabled = !isOptimizing,
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Text(if (isOptimizing) "Otimizando..." else "Mesclar Pessoas Repetidas")
                }
            }

            HorizontalDivider(color = OnSurface.copy(alpha = 0.1f))
            
            // Espaço para futuras configurações
        }
    }
}

// Helper para Box que não foi importado no snippet acima mas é necessário
@Composable
private fun Box(
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.Box(modifier = modifier) {
        content()
    }
}
