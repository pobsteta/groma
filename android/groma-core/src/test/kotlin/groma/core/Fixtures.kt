package groma.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

object Fixtures {
    val racine = File(System.getProperty("groma.racine") ?: "../..")
    fun dossier(nom: String) = File(racine, "fixtures/$nom")
    fun lire(f: File): JsonObject = Json.parseToJsonElement(f.readText()) as JsonObject
    fun JsonElement?.d(): Double? = if (this == null || this is JsonNull) null else jsonPrimitive.content.toDouble()
    fun JsonElement?.s(): String? = if (this == null || this is JsonNull) null else jsonPrimitive.content
}
