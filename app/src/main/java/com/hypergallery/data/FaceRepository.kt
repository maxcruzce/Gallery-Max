package com.hypergallery.data

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class FaceInfo(
    val mediaId: Long,
    val mediaUri: String,
    val bounds: Rect,
    val normalizedBounds: NormalizedRect? = null,
    val rotationDegrees: Int = 0,
    val faceId: String,
    val embedding: FloatArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FaceInfo) return false
        return faceId == other.faceId && mediaId == other.mediaId
    }
    override fun hashCode(): Int = faceId.hashCode() * 31 + mediaId.hashCode()
}

data class Person(
    val id: String,
    var name: String? = null,
    val faceIds: MutableList<String> = mutableListOf(),
    val averageEmbedding: FloatArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Person) return false
        return id == other.id
    }
    override fun hashCode(): Int = id.hashCode()
}

class FaceRepository(context: Context) {
    private val TAG = "FaceRepository"
    private val prefs = context.getSharedPreferences("face_management", Context.MODE_PRIVATE)
    private val gson = Gson()

    // Threshold para similaridade coseno (embeddings L2-normalizados)
    // 0.55 é conservador — prefere criar nova pessoa do que misturar
    private val SIMILARITY_THRESHOLD = 0.55f
    // Threshold para mescla automática — deve ser bem mais alto
    private val MERGE_THRESHOLD = 0.78f
    // Quantas faces individuais comparar além do centróide
    private val MAX_FACES_TO_COMPARE = 8

    @Volatile private var faceMap: MutableMap<String, FaceInfo> = mutableMapOf()
    @Volatile private var personMap: MutableMap<String, Person> = mutableMapOf()

    init {
        loadFromPrefs()
    }

    private fun loadFromPrefs() {
        faceMap = loadMap("face_map")
        personMap = loadMap("person_map")
        Log.d(TAG, "Loaded ${faceMap.size} faces, ${personMap.size} persons from prefs")
    }

    /**
     * Adiciona um rosto detectado e tenta agrupá-lo com uma pessoa existente.
     * Thread-safe: chamado de IO/Default coroutines.
     */
    @Synchronized
    fun addFace(
        mediaId: Long,
        uri: Uri,
        bounds: Rect,
        normalizedBounds: NormalizedRect? = null,
        rotationDegrees: Int = 0,
        embedding: FloatArray? = null
    ) {
        val faceId = buildFaceId(mediaId, bounds)

        // Idempotente: não adiciona o mesmo rosto duas vezes
        if (faceMap.containsKey(faceId)) {
            // Se já existe mas sem embedding e agora temos um, atualiza
            if (embedding != null && faceMap[faceId]?.embedding == null) {
                val updated = faceMap[faceId]!!.copy(embedding = normalize(embedding))
                faceMap[faceId] = updated
                // Re-avalia agrupamento com embedding novo
                reassignFaceTobestPerson(faceId, updated.embedding!!)
                save()
            }
            return
        }

        val normalizedEmb = embedding?.let { normalize(it) }
        val info = FaceInfo(
            mediaId, uri.toString(), bounds, normalizedBounds,
            rotationDegrees, faceId, normalizedEmb
        )
        faceMap[faceId] = info

        if (normalizedEmb != null) {
            // Tenta encontrar pessoa existente
            val match = findBestMatch(normalizedEmb)
            if (match != null) {
                addFaceToPerson(match.first, faceId, normalizedEmb)
                save()
                return
            }
        }

        // Sem match ou sem embedding: cria nova pessoa
        createNewPerson(faceId, normalizedEmb)
        save()
    }

    private fun buildFaceId(mediaId: Long, bounds: Rect): String =
        "face_${mediaId}_${bounds.left}_${bounds.top}_${bounds.right}_${bounds.bottom}"

    private fun createNewPerson(faceId: String, embedding: FloatArray?) {
        val personId = "person_$faceId"
        val person = Person(
            id = personId,
            name = null,
            faceIds = mutableListOf(faceId),
            averageEmbedding = embedding?.copyOf()
        )
        personMap[personId] = person
        Log.d(TAG, "New person $personId for face $faceId")
    }

    private fun reassignFaceTobestPerson(faceId: String, embedding: FloatArray) {
        // Remove da pessoa atual se existir
        val currentPerson = personMap.values.find { it.faceIds.contains(faceId) }
        currentPerson?.let {
            it.faceIds.remove(faceId)
            if (it.faceIds.isEmpty()) personMap.remove(it.id)
            else updateAverageEmbedding(it)
        }

        val match = findBestMatch(embedding)
        if (match != null) {
            addFaceToPerson(match.first, faceId, embedding)
        } else {
            createNewPerson(faceId, embedding)
        }
    }

    @Synchronized
    fun addFaceToPerson(person: Person, faceId: String, embedding: FloatArray) {
        if (person.faceIds.contains(faceId)) return
        person.faceIds.add(faceId)

        // Média incremental (Welford) — numericamente estável
        val avg = person.averageEmbedding
        val n = person.faceIds.size.toFloat()
        val newAvg = if (avg == null || n <= 1f) {
            embedding.copyOf()
        } else {
            FloatArray(embedding.size) { i -> avg[i] + (embedding[i] - avg[i]) / n }
        }
        personMap[person.id] = person.copy(averageEmbedding = normalize(newAvg))
    }

    private fun findBestMatch(newEmbedding: FloatArray): Pair<Person, Float>? {
        var bestPerson: Person? = null
        var bestScore = SIMILARITY_THRESHOLD // Mínimo para considerar match

        for (person in personMap.values) {
            if (person.faceIds.isEmpty()) continue

            // Similaridade com o centróide do cluster
            val centroidSim = person.averageEmbedding?.let { cosineSim(newEmbedding, it) } ?: 0f

            // Melhor similaridade com faces individuais recentes (evita drift do centróide)
            var bestIndivSim = 0f
            for (faceId in person.faceIds.takeLast(MAX_FACES_TO_COMPARE)) {
                faceMap[faceId]?.embedding?.let { emb ->
                    val s = cosineSim(newEmbedding, emb)
                    if (s > bestIndivSim) bestIndivSim = s
                }
            }

            // Score final: max dos dois para ser mais inclusivo
            val score = maxOf(centroidSim, bestIndivSim)

            if (score > bestScore) {
                bestScore = score
                bestPerson = person
            }
        }

        return bestPerson?.let { it to bestScore }
    }

    private fun cosineSim(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot.coerceIn(-1f, 1f)
    }

    private fun normalize(v: FloatArray): FloatArray {
        var sum = 0f
        for (x in v) sum += x * x
        val mag = kotlin.math.sqrt(sum.toDouble()).toFloat()
        if (mag < 1e-10f) return v.copyOf()
        return FloatArray(v.size) { v[it] / mag }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  LEITURA PÚBLICA
    // ──────────────────────────────────────────────────────────────────────────

    fun getAllPeople(): List<Pair<Person, List<FaceInfo>>> {
        return personMap.values
            .filter { it.faceIds.isNotEmpty() }
            .sortedByDescending { it.faceIds.size }
            .map { person ->
                val faces = person.faceIds.mapNotNull { faceMap[it] }
                person to faces
            }
    }

    fun getFacesForMedia(mediaId: Long): List<Triple<FaceInfo, String?, String?>> {
        return faceMap.values
            .filter { it.mediaId == mediaId }
            .map { face ->
                val person = personMap.values.find { it.faceIds.contains(face.faceId) }
                Triple(face, person?.name, person?.id)
            }
    }

    fun getFaceCountForMedia(mediaId: Long): Int =
        faceMap.values.count { it.mediaId == mediaId }

    fun isMediaAnalyzed(mediaId: Long): Boolean =
        faceMap.values.any { it.mediaId == mediaId }

    fun getPersonCount(): Int = personMap.values.count { it.faceIds.isNotEmpty() }
    fun getFaceCount(): Int = faceMap.size

    // ──────────────────────────────────────────────────────────────────────────
    //  EDIÇÃO
    // ──────────────────────────────────────────────────────────────────────────

    @Synchronized
    fun renamePerson(personId: String, newName: String) {
        personMap[personId]?.let {
            personMap[personId] = it.copy(name = newName.trim().ifEmpty { null })
            save()
        }
    }

    @Synchronized
    fun mergePeople(targetPersonId: String, sourcePersonIds: List<String>) {
        val target = personMap[targetPersonId] ?: return
        for (sourceId in sourcePersonIds) {
            if (sourceId == targetPersonId) continue
            val source = personMap[sourceId] ?: continue
            for (faceId in source.faceIds.toList()) {
                faceMap[faceId]?.embedding?.let { emb ->
                    addFaceToPerson(target, faceId, emb)
                } ?: run {
                    // Face sem embedding: adiciona mesmo assim
                    if (!target.faceIds.contains(faceId)) target.faceIds.add(faceId)
                }
            }
            if (target.name == null && source.name != null) {
                personMap[targetPersonId] = target.copy(name = source.name)
            }
            personMap.remove(sourceId)
        }
        save()
    }

    @Synchronized
    fun removeFaceFromPerson(personId: String, faceId: String) {
        personMap[personId]?.let { person ->
            person.faceIds.remove(faceId)
            if (person.faceIds.isEmpty()) {
                personMap.remove(personId)
            } else {
                updateAverageEmbedding(person)
            }
            save()
        }
    }

    @Synchronized
    fun deletePerson(personId: String) {
        personMap.remove(personId)
        save()
    }

    @Synchronized
    fun updateFaceBounds(faceId: String, newBounds: Rect) {
        faceMap[faceId]?.let { info ->
            faceMap[faceId] = info.copy(bounds = newBounds)
            save()
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  AUTO-MERGE
    // ──────────────────────────────────────────────────────────────────────────

    suspend fun autoMergePeople(onProgress: (String) -> Unit = {}) = withContext(Dispatchers.Default) {
        Log.d(TAG, "AutoMerge started. Persons: ${personMap.size}")
        onProgress("Analisando ${personMap.size} grupos...")

        // Só considera pessoas com embedding
        val persons = synchronized(this@FaceRepository) {
            personMap.values.filter { it.averageEmbedding != null }.toList()
        }

        val toMerge = mutableListOf<Pair<String, String>>() // (keep, drop)
        val removed = mutableSetOf<String>()

        for (i in persons.indices) {
            if (persons[i].id in removed) continue
            for (j in i + 1 until persons.size) {
                if (persons[j].id in removed) continue
                val sim = cosineSim(persons[i].averageEmbedding!!, persons[j].averageEmbedding!!)
                if (sim >= MERGE_THRESHOLD) {
                    // Mantém a pessoa com mais faces
                    val (keep, drop) = if (persons[i].faceIds.size >= persons[j].faceIds.size)
                        persons[i] to persons[j] else persons[j] to persons[i]
                    toMerge.add(keep.id to drop.id)
                    removed.add(drop.id)
                    Log.d(TAG, "AutoMerge: ${keep.id} <- ${drop.id} (sim=%.3f)".format(sim))
                }
            }
        }

        withContext(Dispatchers.Main) {
            if (toMerge.isNotEmpty()) {
                onProgress("Mesclando ${toMerge.size} grupos duplicados...")
                synchronized(this@FaceRepository) {
                    for ((keepId, dropId) in toMerge) {
                        val keep = personMap[keepId] ?: continue
                        val drop = personMap[dropId] ?: continue
                        for (faceId in drop.faceIds.toList()) {
                            faceMap[faceId]?.embedding?.let { addFaceToPerson(keep, faceId, it) }
                                ?: run { if (!keep.faceIds.contains(faceId)) keep.faceIds.add(faceId) }
                        }
                        if (keep.name == null && drop.name != null) {
                            personMap[keepId] = keep.copy(name = drop.name)
                        }
                        personMap.remove(dropId)
                    }
                    save()
                }
                onProgress("Otimização concluída! ${toMerge.size} grupos mesclados.")
            } else {
                onProgress("Nenhuma duplicata encontrada.")
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  RESET
    // ──────────────────────────────────────────────────────────────────────────

    @Synchronized
    fun clearAll() {
        faceMap.clear()
        personMap.clear()
        prefs.edit().remove("face_map").remove("person_map").apply()
        Log.d(TAG, "All face data cleared")
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  INTERNAL
    // ──────────────────────────────────────────────────────────────────────────

    private fun updateAverageEmbedding(person: Person) {
        val embeddings = person.faceIds.mapNotNull { faceMap[it]?.embedding }
        if (embeddings.isEmpty()) {
            personMap[person.id] = person.copy(averageEmbedding = null)
            return
        }
        val size = embeddings[0].size
        val avg = FloatArray(size) { i ->
            embeddings.sumOf { it[i].toDouble() }.toFloat() / embeddings.size
        }
        personMap[person.id] = person.copy(averageEmbedding = normalize(avg))
    }

    @Synchronized
    private fun save() {
        try {
            prefs.edit()
                .putString("face_map", gson.toJson(faceMap))
                .putString("person_map", gson.toJson(personMap))
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving face data", e)
        }
    }

    private inline fun <reified T> loadMap(key: String): MutableMap<String, T> {
        val json = prefs.getString(key, null) ?: return mutableMapOf()
        return try {
            val type = object : TypeToken<MutableMap<String, T>>() {}.type
            gson.fromJson<MutableMap<String, T>>(json, type) ?: mutableMapOf()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading $key, resetting", e)
            mutableMapOf()
        }
    }
}
