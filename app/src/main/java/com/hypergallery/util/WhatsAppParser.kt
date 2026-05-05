package com.hypergallery.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

data class MediaMap(
    val fileName: String,
    val sender: String,
    val timestamp: String,
    val dateAddedMs: Long = 0L,
    val isHiddenMedia: Boolean = false
)

object WhatsAppParser {
    private const val TAG = "WhatsAppParser"
    
    private val fileExtensions = listOf(
        ".jpg", ".jpeg", ".png", ".webp", ".gif", ".mp4", ".3gp", ".mp3", 
        ".opus", ".pdf", ".doc", ".docx", ".vcf"
    )
    
    private val mediaIndicators = listOf(
        "arquivo anexado", "anexado", "mídia oculta", "media omitida", 
        "omitted", "imagem", "foto", "vídeo", "video", "áudio", "audio",
        "nota de voz", "documento", "sticker", ".vcf"
    )

    fun parseWhatsAppBackup(textContent: String): List<MediaMap> {
        Log.d(TAG, "=== INICIANDO PARSER DO WHATSAPP ===")
        Log.d(TAG, "Tamanho do conteúdo: ${textContent.length} caracteres")
        
        val mediaList = mutableListOf<MediaMap>()
        val lines = textContent.lines()
        
        Log.d(TAG, "Total de linhas: ${lines.size}")
        
        val dateFormats = listOf(
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US),
            SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US)
        )
        
        // Identificar os participantes da conversa
        val participants = mutableSetOf<String>()
        
        for (line in lines) {
            val senderMatch = Pattern.compile("^\\d{2}/\\d{2}/\\d{2,4}.*?-\\s*(.+?):").matcher(line)
            if (senderMatch.find()) {
                val sender = senderMatch.group(1)?.trim() ?: continue
                if (sender != "Mensagem apagada" && !sender.contains("https://")) {
                    participants.add(sender)
                }
            }
        }
        
        Log.d(TAG, "Participantes identificados: $participants")
        
        var mediaCount = 0
        
        for (line in lines) {
            if (line.isBlank()) continue
            
            val mainPattern = Pattern.compile("^(\\d{2}/\\d{2}/\\d{2,4})[,\\s]+(\\d{2}:\\d{2}(?::\\d{2})?)\\s*[-–]\\s*(.+?):\\s*(.*)$")
            val mainMatcher = mainPattern.matcher(line)
            
            if (mainMatcher.find()) {
                val dateStr = mainMatcher.group(1) ?: ""
                val timeStr = mainMatcher.group(2) ?: ""
                val sender = mainMatcher.group(3)?.trim() ?: "Desconhecido"
                var content = mainMatcher.group(4)?.trim() ?: ""
                
                val timestamp = "$dateStr $timeStr"
                
                var dateAddedMs = 0L
                for (fmt in dateFormats) {
                    try {
                        val parsed = fmt.parse("$dateStr $timeStr")
                        if (parsed != null) {
                            dateAddedMs = parsed.time
                            break
                        }
                    } catch (e: Exception) { }
                }
                
                val isMedia = detectMedia(content)
                
                if (isMedia) {
                    mediaCount++
                    val isHidden = content.contains("Mídia oculta", ignoreCase = true) || 
                                  content.contains("omitted", ignoreCase = true)
                    
                    val fileName = extractFileName(content, sender, isHidden)
                    
                    Log.d(TAG, "Mídia [$mediaCount] sender=$sender fileName=$fileName isHidden=$isHidden content=${content.take(50)}")
                    
                    mediaList.add(MediaMap(
                        fileName = fileName,
                        sender = sender,
                        timestamp = timestamp,
                        dateAddedMs = dateAddedMs,
                        isHiddenMedia = isHidden
                    ))
                }
            }
        }
        
        Log.d(TAG, "=== FIM DO PARSER ===")
        Log.d(TAG, "Total mídias encontradas: $mediaCount")
        Log.d(TAG, "Mídia oculta: ${mediaList.count { it.isHiddenMedia }}")
        Log.d(TAG, "Mídia válida: ${mediaList.count { !it.isHiddenMedia }}")
        
        return mediaList
    }

    private fun detectMedia(content: String): Boolean {
        if (content.isBlank()) return false
        
        val lowerContent = content.lowercase()
        
        for (indicator in mediaIndicators) {
            if (lowerContent.contains(indicator)) return true
        }
        
        for (ext in fileExtensions) {
            if (lowerContent.contains(ext)) return true
        }
        
        return false
    }

    private fun extractFileName(content: String, sender: String, isHidden: Boolean): String {
        if (isHidden) {
            return "Mídia oculta"
        }
        
        val parts = content.split(" ")
        
        for (part in parts) {
            val cleanPart = part.trim().replace(">", "").replace("<", "")
            
            val hasExtension = fileExtensions.any { ext -> 
                cleanPart.lowercase().endsWith(ext) 
            }
            
            if (hasExtension && cleanPart.length > 3) {
                return cleanPart.split("(").first().trim()
            }
        }
        
        return "Mídia de $sender"
    }
}