package com.hypergallery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.transform.Transformation
import android.graphics.Bitmap
import android.graphics.Rect
import com.hypergallery.data.FaceInfo
import com.hypergallery.data.Person
import com.hypergallery.ui.components.FaceThumbnail
import com.hypergallery.ui.theme.HeaderBg
import com.hypergallery.ui.theme.OnSurface
import com.hypergallery.ui.theme.Primary
import com.hypergallery.ui.theme.Surface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(
    peopleWithFaces: List<Pair<Person, List<FaceInfo>>>,
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
    var selectedPeopleIds by remember { mutableStateOf(setOf<String>()) }
    var isMergeMode by remember { mutableStateOf(false) }
    var personToRename by remember { mutableStateOf<Person?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    
    var personMenuTarget by remember { mutableStateOf<Person?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(Surface)) {
        // Header
        Box(modifier = Modifier.fillMaxWidth().background(HeaderBg).padding(vertical = 10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = if (isMergeMode) { { isMergeMode = false; selectedPeopleIds = emptySet() } } else onBackClick) {
                    Icon(
                        imageVector = if (isMergeMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = OnSurface
                    )
                }
                Text(
                    text = if (isMergeMode) "Mesclar Pessoas" else "Pessoas e Pets",
                    color = OnSurface,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
                
                if (!isMergeMode && peopleWithFaces.isNotEmpty()) {
                    IconButton(onClick = onOptimizePeople) {
                        Icon(Icons.Default.CallMerge, contentDescription = "Otimizar", tint = OnSurface)
                    }
                    IconButton(onClick = { isMergeMode = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Mesclar manual", tint = OnSurface)
                    }
                } else if (isMergeMode && selectedPeopleIds.size >= 2) {
                    TextButton(onClick = {
                        val target = selectedPeopleIds.first()
                        val sources = selectedPeopleIds.drop(1).toList()
                        onMergePeople(target, sources)
                        isMergeMode = false
                        selectedPeopleIds = emptySet()
                    }) {
                        Text("MESCLAR", color = Primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (peopleWithFaces.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Nenhuma pessoa identificada", color = OnSurface.copy(alpha = 0.5f))
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 100.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                items(peopleWithFaces) { (person, faces) ->
                    val isSelected = selectedPeopleIds.contains(person.id)
                    PersonGroupItem(
                        person = person,
                        faces = faces,
                        isSelected = isSelected,
                        isMergeMode = isMergeMode,
                        onClick = {
                            if (isMergeMode) {
                                selectedPeopleIds = if (isSelected) selectedPeopleIds - person.id else selectedPeopleIds + person.id
                            } else {
                                onPersonClick(person)
                            }
                        },
                        onLongClick = {
                            if (!isMergeMode) personMenuTarget = person
                        },
                        onRemoveFace = { faceId -> onRemoveFace(person.id, faceId) }
                    )
                }
            }
        }
    }

    // Menu de Contexto da Pessoa
    personMenuTarget?.let { person ->
        DropdownMenu(
            expanded = true,
            onDismissRequest = { personMenuTarget = null }
        ) {
            DropdownMenuItem(
                text = { Text("Renomear Pessoa") },
                onClick = { 
                    personToRename = person
                    showRenameDialog = true
                    personMenuTarget = null 
                },
                leadingIcon = { Icon(Icons.Default.Edit, null) }
            )
            DropdownMenuItem(
                text = { Text("Procurar Rostos") },
                onClick = { 
                    onSearchFacesForPerson(person.id)
                    personMenuTarget = null 
                },
                leadingIcon = { Icon(Icons.Default.Search, null) }
            )
            DropdownMenuItem(
                text = { Text("Apagar Pessoa", color = Color.Red) },
                onClick = { 
                    onDeletePerson(person.id)
                    personMenuTarget = null 
                },
                leadingIcon = { Icon(Icons.Default.PersonRemove, null, tint = Color.Red) }
            )
        }
    }

    if (showRenameDialog && personToRename != null) {
        var nameText by remember { mutableStateOf(personToRename?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(if (personToRename?.name == null) "Dar Nome a esta Pessoa" else "Renomear Pessoa") },
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
                Button(onClick = {
                    onRenamePerson(personToRename!!.id, nameText)
                    showRenameDialog = false
                }) { Text("Salvar") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PersonGroupItem(
    person: Person,
    faces: List<FaceInfo>,
    isSelected: Boolean,
    isMergeMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onRemoveFace: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Primary.copy(alpha = 0.1f) else OnSurface.copy(alpha = 0.05f)
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, Primary) else null
    ) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.TopEnd) {
                if (faces.isNotEmpty()) {
                    FaceThumbnail(
                        face = faces.first(),
                        modifier = Modifier.size(120.dp).clip(CircleShape),
                        onClick = onClick
                    )
                }
                
                if (person.name == null) {
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .background(Color.Red, CircleShape)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Novo", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (!isMergeMode) {
                    IconButton(
                        onClick = onLongClick,
                        modifier = Modifier.size(32.dp).padding(4.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(Icons.Default.MoreVert, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
                
                if (isMergeMode && isSelected) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(28.dp).background(Color.White, CircleShape).padding(2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = person.name ?: "Pessoa Desconhecida",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            
            Text(text = "${faces.size} fotos", fontSize = 12.sp, color = OnSurface.copy(alpha = 0.6f))
        }
    }
}

