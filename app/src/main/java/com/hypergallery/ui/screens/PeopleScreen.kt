package com.hypergallery.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hypergallery.data.FaceInfo
import com.hypergallery.data.Person
import com.hypergallery.ui.components.FaceThumbnail
import com.hypergallery.ui.theme.HeaderBg
import com.hypergallery.ui.theme.OnSurface
import com.hypergallery.ui.theme.Primary
import com.hypergallery.ui.theme.Surface

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PeopleScreen(
    peopleWithFaces: List<Pair<Person, List<FaceInfo>>>,
    isAnalyzing: Boolean = false,
    analysisProgress: Float = 0f,
    analyzedCount: Int = 0,
    totalToAnalyze: Int = 0,
    faceAnalysisStatus: String = "",
    faceModelAvailable: Boolean = false,
    onBackClick: () -> Unit,
    onRenamePerson: (String, String) -> Unit,
    onMergePeople: (String, List<String>) -> Unit,
    onRemoveFace: (String, String) -> Unit,
    onDeletePerson: (String) -> Unit = {},
    onSearchFacesForPerson: (String) -> Unit = {},
    onPersonClick: (Person) -> Unit,
    onOptimizePeople: () -> Unit = {}
) {
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var isMergeMode by remember { mutableStateOf(false) }
    var personToRename by remember { mutableStateOf<Person?>(null) }
    var personMenuTarget by remember { mutableStateOf<Person?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(Surface)) {

        // ── Header ────────────────────────────────────────────────────────
        Box(
            modifier = Modifier.fillMaxWidth().background(HeaderBg).padding(vertical = 10.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = if (isMergeMode) {{ isMergeMode = false; selectedIds = emptySet() }} else onBackClick
                ) {
                    Icon(
                        if (isMergeMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null, tint = OnSurface
                    )
                }
                Text(
                    text = if (isMergeMode) "Selecione para mesclar" else "Pessoas",
                    color = OnSurface, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
                if (!isMergeMode && peopleWithFaces.isNotEmpty()) {
                    IconButton(onClick = onOptimizePeople) {
                        Icon(Icons.Default.AutoAwesome, "Otimizar grupos", tint = OnSurface)
                    }
                    IconButton(onClick = { isMergeMode = true }) {
                        Icon(Icons.Default.CallMerge, "Mesclar manual", tint = OnSurface)
                    }
                } else if (isMergeMode && selectedIds.size >= 2) {
                    TextButton(onClick = {
                        val sorted = selectedIds.toList()
                        onMergePeople(sorted.first(), sorted.drop(1))
                        isMergeMode = false; selectedIds = emptySet()
                    }) {
                        Text("MESCLAR (${selectedIds.size})", color = Primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ── Banner de análise em andamento ────────────────────────────────
        if (isAnalyzing || faceAnalysisStatus.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Primary.copy(alpha = 0.08f))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .animateContentSize()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Primary
                        )
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Default.CheckCircle, null,
                            tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = if (isAnalyzing && totalToAnalyze > 0)
                            "Analisando rostos: $analyzedCount/$totalToAnalyze"
                        else faceAnalysisStatus,
                        color = OnSurface, fontSize = 13.sp
                    )
                }
                if (isAnalyzing && totalToAnalyze > 0) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { analysisProgress },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                        color = Primary, trackColor = OnSurface.copy(alpha = 0.1f)
                    )
                }
                if (!faceModelAvailable) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "💡 Para agrupamento mais preciso, adicione mobilefacenet.tflite em assets/",
                        color = OnSurface.copy(alpha = 0.6f), fontSize = 11.sp
                    )
                }
            }
        }

        // ── Conteúdo ──────────────────────────────────────────────────────
        if (peopleWithFaces.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(
                        Icons.Default.People, null,
                        tint = OnSurface.copy(alpha = 0.2f),
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = if (isAnalyzing) "Identificando pessoas..." else "Nenhuma pessoa encontrada",
                        color = OnSurface.copy(alpha = 0.5f),
                        fontSize = 16.sp, fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                    if (!isAnalyzing) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "As pessoas são detectadas automaticamente nas suas fotos.\nVerifique se as fotos estão carregadas.",
                            color = OnSurface.copy(alpha = 0.35f),
                            fontSize = 13.sp, textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 100.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(peopleWithFaces, key = { it.first.id }) { (person, faces) ->
                    val isSelected = selectedIds.contains(person.id)
                    PersonCard(
                        person = person,
                        faces = faces,
                        isSelected = isSelected,
                        isMergeMode = isMergeMode,
                        onClick = {
                            if (isMergeMode) {
                                selectedIds = if (isSelected) selectedIds - person.id else selectedIds + person.id
                            } else {
                                onPersonClick(person)
                            }
                        },
                        onLongClick = { if (!isMergeMode) personMenuTarget = person },
                        onMenuClick = { personMenuTarget = person }
                    )
                }
            }
        }
    }

    // ── Menu de contexto ──────────────────────────────────────────────────
    personMenuTarget?.let { person ->
        DropdownMenu(expanded = true, onDismissRequest = { personMenuTarget = null }) {
            DropdownMenuItem(
                text = { Text(person.name?.let { "Renomear \"$it\"" } ?: "Dar nome a esta pessoa") },
                onClick = { personToRename = person; personMenuTarget = null },
                leadingIcon = { Icon(Icons.Default.Edit, null) }
            )
            DropdownMenuItem(
                text = { Text("Procurar mais fotos") },
                onClick = { onSearchFacesForPerson(person.id); personMenuTarget = null },
                leadingIcon = { Icon(Icons.Default.Search, null) }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Apagar pessoa", color = Color.Red) },
                onClick = { onDeletePerson(person.id); personMenuTarget = null },
                leadingIcon = { Icon(Icons.Default.PersonRemove, null, tint = Color.Red) }
            )
        }
    }

    // ── Diálogo de renomear ───────────────────────────────────────────────
    personToRename?.let { person ->
        var nameText by remember(person.id) { mutableStateOf(person.name ?: "") }
        AlertDialog(
            onDismissRequest = { personToRename = null },
            title = { Text(if (person.name == null) "Dar nome a esta pessoa" else "Renomear") },
            text = {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    placeholder = { Text("Nome da pessoa...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = { onRenamePerson(person.id, nameText); personToRename = null },
                    enabled = nameText.isNotBlank()
                ) { Text("Salvar") }
            },
            dismissButton = {
                TextButton(onClick = { personToRename = null }) { Text("Cancelar") }
            }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PersonCard(
    person: Person,
    faces: List<FaceInfo>,
    isSelected: Boolean,
    isMergeMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Primary.copy(alpha = 0.12f)
                            else OnSurface.copy(alpha = 0.05f)
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, Primary) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Foto do rosto com overlay
            Box(contentAlignment = Alignment.TopEnd) {
                if (faces.isNotEmpty()) {
                    FaceThumbnail(
                        face = faces.first(),
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape),
                        onClick = onClick
                    )
                } else {
                    // Placeholder quando não há foto de rosto
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(OnSurface.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person, null,
                            tint = OnSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(50.dp)
                        )
                    }
                }

                // Badge "Novo" para pessoas sem nome
                if (person.name == null && !isMergeMode) {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp, end = 2.dp)
                            .background(Color(0xFFFF5722), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Novo", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Checkmark no modo merge
                if (isMergeMode && isSelected) {
                    Icon(
                        Icons.Default.CheckCircle, null,
                        tint = Primary,
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color.White, CircleShape)
                            .padding(2.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Nome
            Text(
                text = person.name ?: "Pessoa Desconhecida",
                fontSize = 14.sp,
                fontWeight = if (person.name != null) FontWeight.SemiBold else FontWeight.Normal,
                color = if (person.name != null) OnSurface else OnSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            // Contagem de fotos
            Text(
                text = "${faces.size} foto${if (faces.size != 1) "s" else ""}",
                fontSize = 12.sp,
                color = OnSurface.copy(alpha = 0.5f)
            )

            // Miniaturas de fotos adicionais (se tiver mais de 1)
            if (faces.size > 1 && !isMergeMode) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    faces.drop(1).take(3).forEach { face ->
                        FaceThumbnail(
                            face = face,
                            modifier = Modifier.size(32.dp).clip(CircleShape),
                            onClick = onClick
                        )
                    }
                    if (faces.size > 4) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(OnSurface.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+${faces.size - 4}", fontSize = 10.sp, color = OnSurface.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }
}
