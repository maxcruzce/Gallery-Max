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
    // BUG FIX: FloatArray não implementa equals/hashCode corretamente em data class
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FaceInfo) return false
        return faceId == other.faceId &&
               mediaId == other.mediaId &&
               mediaUri == other.mediaUri &&
               bounds == other.bounds &&
               normalizedBounds == other.normalizedBounds &&
               rotationDegrees == other.rotationDegrees &&
               embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int {
        var result = faceId.hashCode()
        result = 31 * result + mediaId.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        return result
    }
}

data class Person(
    val id: String,
    var name: String? = null,
    val faceIds: MutableList<String> = mutableListOf(),
    val averageEmbedding: FloatArray? = null
) {
    // BUG FIX: FloatArray não implementa equals/hashCode corretamente em data class
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Person) return false
        return id == other.id && name == other.name &&
               faceIds == other.faceIds &&
               averageEmbedding.contentEquals(other.averageEmbedding)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + (averageEmbedding?.contentHashCode() ?: 0)
        return result
    }
}

class FaceRepository(context: Context) {
    private val TAG = "FaceRepository"
    private val prefs = context.getSharedPreferences("face_management", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val SIMILARITY_THRESHOLD = 0.58f
    private val MAX_FACES_TO_COMPARE = 5
    private val MERGE_THRESHOLD = 0.75f

    private var faceMap: MutableMap<String, FaceInfo> = loadMap("face_map")
    private var personMap: MutableMap<String, Person> = loadMap("person_map")

    fun addFace(
        mediaId: Long,
        uri: Uri,
        bounds: Rect,
        normalizedBounds: NormalizedRect? = null,
        rotationDegrees: Int = 0,
        embedding: FloatArray? = null
    ) {
        val faceId = "face_${mediaId}_${bounds.left}_${bounds.top}"
        if (faceMap.containsKey(faceId)) return

        val normalizedEmbedding = embedding?.let { normalize(it) }
        val info = FaceInfo(mediaId, uri.toString(), bounds, normalizedBounds, rotationDegrees, faceId, normalizedEmbedding)
        faceMap[faceId] = info

        if (normalizedEmbedding != null) {
            val match = findBestMatch(normalizedEmbedding)
            if (match != null) {
                val (matchedPerson, similarity) = match
                Log.d(TAG, "Matched face $faceId to ${matchedPerson.id} (sim=$similarity)")
                addFaceToPerson(matchedPerson, faceId, normalizedEmbedding)
                save()
                return
            }
        }

        val personId = "person_$faceId"
        val person = Person(personId, null, mutableListOf(faceId), normalizedEmbedding?.copyOf())
        personMap[personId] = person
        Log.d(TAG, "Created new person $personId for face $faceId")
        save()
    }

    private fun addFaceToPerson(person: Person, faceId: String, embedding: FloatArray) {
        if (person.faceIds.contains(faceId)) return
        person.faceIds.add(faceId)

        val avg = person.averageEmbedding
        val n = person.faceIds.size.toFloat()
        val newAvg = if (avg == null || n <= 1f) {
            embedding.copyOf()
        } else {
            // Média incremental de Welford — numericamente estável
            FloatArray(embedding.size) { i -> avg[i] + (embedding[i] - avg[i]) / n }
        }
        personMap[person.id] = person.copy(averageEmbedding = normalize(newAvg))
    }

    private fun normalize(v: FloatArray): FloatArray {
        var sum = 0f
        for (x in v) sum += x * x
        val mag = kotlin.math.sqrt(sum.toDouble()).toFloat()
        if (mag < 1e-10f) return v.copyOf()
        return FloatArray(v.size) { v[it] / mag }
    }

    private fun findBestMatch(newEmbedding: FloatArray): Pair<Person, Float>? {
        var bestMatch: Person? = null
        var highestScore = 0f

        for (person in personMap.values) {
            val avgSim = person.averageEmbedding?.let { cosineSimilarity(newEmbedding, it) } ?: 0f

            var bestIndividualSim = 0f
            for (faceId in person.faceIds.takeLast(MAX_FACES_TO_COMPARE)) {
                faceMap[faceId]?.embedding?.let { faceEmb ->
                    val sim = cosineSimilarity(newEmbedding, faceEmb)
                    if (sim > bestIndividualSim) bestIndividualSim = sim
                }
            }

            val score = maxOf(avgSim, bestIndividualSim)
            if (score > SIMILARITY_THRESHOLD && score > highestScore) {
                highestScore = score
                bestMatch = person
            }
        }

        // Log de quase-match para debug
        if (bestMatch == null) {
            val topGlobal = personMap.values.maxOfOrNull {
                it.averageEmbedding?.let { emb -> cosineSimilarity(newEmbedding, emb) } ?: 0f
            } ?: 0f
            if (topGlobal > 0.4f) Log.d(TAG, "Best non-match: $topGlobal (threshold=$SIMILARITY_THRESHOLD)")
        }

        return bestMatch?.let { it to highestScore }
    }

    private fun cosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
        if (vec1.size != vec2.size) return 0f
        var dot = 0f
        for (i in vec1.indices) dot += vec1[i] * vec2[i]
        return dot.coerceIn(-1f, 1f)
    }

    fun getAllPeople(): List<Pair<Person, List<FaceInfo>>> {
        return personMap.values
            .filter { it.faceIds.isNotEmpty() } // BUG FIX: Filtra pessoas sem faces
            .sortedByDescending { it.faceIds.size }
            .map { person -> person to person.faceIds.mapNotNull { faceMap[it] } }
    }

    fun getFacesForMedia(mediaId: Long): List<Triple<FaceInfo, String?, String?>> {
        return faceMap.values.filter { it.mediaId == mediaId }.map { face ->
            val person = personMap.values.find { it.faceIds.contains(face.faceId) }
            Triple(face, person?.name, person?.id)
        }
    }

    fun getTopPeople(limit: Int = 5): List<Pair<Person, FaceInfo>> {
        return personMap.values
            .filter { it.faceIds.isNotEmpty() }
            .sortedByDescending { it.faceIds.size }
            .take(limit)
            .mapNotNull { person ->
                person.faceIds.firstNotNullOfOrNull { faceMap[it] }?.let { person to it }
            }
    }

    fun renamePerson(personId: String, newName: String) {
        personMap[personId]?.let {
            personMap[personId] = it.copy(name = newName)
            save()
        }
    }

    fun mergePeople(targetPersonId: String, sourcePersonIds: List<String>) {
        val target = personMap[targetPersonId] ?: return
        sourcePersonIds.forEach { sourceId ->
            if (sourceId == targetPersonId) return@forEach
            personMap[sourceId]?.let { source ->
                source.faceIds.forEach { faceId ->
                    faceMap[faceId]?.embedding?.let { emb -> addFaceToPerson(target, faceId, emb) }
                }
                // Preserva nome se o target não tiver
                if (target.name == null && source.name != null) {
                    personMap[targetPersonId] = target.copy(name = source.name)
                }
                personMap.remove(sourceId)
            }
        }
        save()
    }

    suspend fun autoMergePeople(onProgress: (String) -> Unit = {}) = withContext(Dispatchers.Default) {
        Log.d(TAG, "Auto-merge started. Persons: ${personMap.size}")

        val persons = personMap.values.filter { it.averageEmbedding != null }.toList()
        val toMerge = mutableListOf<Pair<String, String>>()
        val removed = mutableSetOf<String>()

        for (i in persons.indices) {
            if (persons[i].id in removed) continue
            for (j in i + 1 until persons.size) {
                if (persons[j].id in removed) continue
                val sim = cosineSimilarity(persons[i].averageEmbedding!!, persons[j].averageEmbedding!!)
                if (sim >= MERGE_THRESHOLD) {
                    val (keep, drop) = if (persons[i].faceIds.size >= persons[j].faceIds.size)
                        persons[i] to persons[j] else persons[j] to persons[i]
                    toMerge.add(keep.id to drop.id)
                    removed.add(drop.id)
                    Log.d(TAG, "AutoMerge: ${keep.id} <- ${drop.id} (sim=$sim)")
                }
            }
        }

        withContext(Dispatchers.Main) {
            if (toMerge.isNotEmpty()) {
                onProgress("Mesclando ${toMerge.size} grupos duplicados...")
                for ((keepId, dropId) in toMerge) {
                    val keep = personMap[keepId] ?: continue
                    val drop = personMap[dropId] ?: continue
                    for (faceId in drop.faceIds) {
                        faceMap[faceId]?.embedding?.let { addFaceToPerson(keep, faceId, it) }
                    }
                    if (keep.name == null && drop.name != null) {
                        personMap[keepId] = keep.copy(name = drop.name)
                    }
                    personMap.remove(dropId)
                }
                save()
                onProgress("Otimização concluída!")
            } else {
                onProgress("Nenhuma duplicata encontrada.")
            }
        }
    }

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

    private fun updateAverageEmbedding(person: Person) {
        val faces = person.faceIds.mapNotNull { faceMap[it]?.embedding }
        if (faces.isEmpty()) {
            personMap[person.id] = person.copy(averageEmbedding = null)
            return
        }
        val size = faces[0].size
        val avg = FloatArray(size) { i -> faces.sumOf { it[i].toDouble() }.toFloat() / faces.size }
        personMap[person.id] = person.copy(averageEmbedding = normalize(avg))
    }

    fun deletePerson(personId: String) {
        personMap.remove(personId)
        save()
    }

    fun updateFaceBounds(faceId: String, newBounds: Rect) {
        faceMap[faceId]?.let { info ->
            faceMap[faceId] = info.copy(
                bounds = newBounds,
                normalizedBounds = info.normalizedBounds?.copy(
                    left = newBounds.left.toFloat(),
                    top = newBounds.top.toFloat(),
                    right = newBounds.right.toFloat(),
                    bottom = newBounds.bottom.toFloat()
                )
            )
            save()
        }
    }

    private fun save() {
        prefs.edit()
            .putString("face_map", gson.toJson(faceMap))
            .putString("person_map", gson.toJson(personMap))
            .apply()
    }

    private inline fun <reified T> loadMap(key: String): MutableMap<String, T> {
        val json = prefs.getString(key, null) ?: return mutableMapOf()
        return try {
            val type = object : TypeToken<MutableMap<String, T>>() {}.type
            gson.fromJson(json, type) ?: mutableMapOf()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading $key from prefs", e)
            mutableMapOf()
        }
    }
}
