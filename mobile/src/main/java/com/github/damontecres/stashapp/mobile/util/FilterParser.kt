package com.github.damontecres.stashapp.mobile.util

import com.apollographql.apollo.api.Optional
import com.github.damontecres.stashapp.mobile.api.type.CriterionModifier
import com.github.damontecres.stashapp.mobile.api.type.DateCriterionInput
import com.github.damontecres.stashapp.mobile.api.type.DuplicationCriterionInput
import com.github.damontecres.stashapp.mobile.api.type.HierarchicalMultiCriterionInput
import com.github.damontecres.stashapp.mobile.api.type.IntCriterionInput
import com.github.damontecres.stashapp.mobile.api.type.MultiCriterionInput
import com.github.damontecres.stashapp.mobile.api.type.OrientationCriterionInput
import com.github.damontecres.stashapp.mobile.api.type.OrientationEnum
import com.github.damontecres.stashapp.mobile.api.type.PhashDistanceCriterionInput
import com.github.damontecres.stashapp.mobile.api.type.ResolutionCriterionInput
import com.github.damontecres.stashapp.mobile.api.type.ResolutionEnum
import com.github.damontecres.stashapp.mobile.api.type.SceneFilterType
import com.github.damontecres.stashapp.mobile.api.type.StringCriterionInput
import com.github.damontecres.stashapp.mobile.api.type.TimestampCriterionInput
import org.json.JSONArray
import org.json.JSONObject

object FilterParser {
    private fun JSONObject.toMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val keys = this.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            var value: Any? = this.get(key)
            if (value is JSONArray) {
                value = value.toList()
            } else if (value is JSONObject) {
                value = value.toMap()
            } else if (value == JSONObject.NULL) {
                value = null
            }
            map[key] = value
        }
        return map
    }

    private fun JSONArray.toList(): List<Any?> {
        val list = mutableListOf<Any?>()
        for (i in 0 until this.length()) {
            var value: Any? = this.get(i)
            if (value is JSONArray) {
                value = value.toList()
            } else if (value is JSONObject) {
                value = value.toMap()
            } else if (value == JSONObject.NULL) {
                value = null
            }
            list.add(value)
        }
        return list
    }

    fun convertStringCriterionInput(it: Map<String, *>?): StringCriterionInput? {
        if (it == null) return null
        val valueObj = it["value"]
        val value = when (valueObj) {
            is String -> valueObj
            is Map<*, *> -> valueObj["value"]?.toString()
            else -> valueObj?.toString()
        } ?: ""

        val modifierStr = it["modifier"] as? String ?: "EQUALS"
        val modifier = try { CriterionModifier.valueOf(modifierStr) } catch (e: Exception) { CriterionModifier.EQUALS }
        return StringCriterionInput(
            value = value,
            modifier = modifier
        )
    }

    fun convertIntCriterionInput(it: Map<String, *>?): IntCriterionInput? {
        if (it == null) return null
        val valueObj = it["value"]
        val value = when (valueObj) {
            is Number -> valueObj.toInt()
            is String -> valueObj.toIntOrNull()
            is Map<*, *> -> valueObj["value"]?.toString()?.toIntOrNull()
            else -> null
        } ?: 0

        val value2 = when (valueObj) {
            is Map<*, *> -> valueObj["value2"]?.toString()?.toIntOrNull()
            else -> null
        }

        val modifierStr = it["modifier"] as? String ?: "EQUALS"
        val modifier = try { CriterionModifier.valueOf(modifierStr) } catch (e: Exception) { CriterionModifier.EQUALS }
        return IntCriterionInput(
            value = value,
            value2 = if (value2 != null) Optional.present(value2) else Optional.absent(),
            modifier = modifier
        )
    }

    fun convertBoolean(it: Map<String, *>?): Boolean? {
        if (it == null) return null
        val valueStr = when (val valueObj = it["value"]) {
            is Boolean -> valueObj.toString()
            is Map<*, *> -> valueObj["value"]?.toString()
            else -> valueObj?.toString()
        } ?: return null
        val value = valueStr.toBoolean()
        val modifierStr = it["modifier"] as? String ?: "EQUALS"
        val modifier = try { CriterionModifier.valueOf(modifierStr) } catch (e: Exception) { CriterionModifier.EQUALS }
        return when (modifier) {
            CriterionModifier.EQUALS -> value
            CriterionModifier.NOT_EQUALS -> !value
            else -> value
        }
    }

    fun convertString(it: Map<String, *>?): String? {
        if (it == null) return null
        return when (val valueObj = it["value"]) {
            is String -> valueObj
            is Map<*, *> -> valueObj["value"]?.toString()
            else -> valueObj?.toString()
        }
    }

    fun convertDateCriterionInput(it: Map<String, *>?): DateCriterionInput? {
        if (it == null) return null
        val valueObj = it["value"] as? Map<*, *>
        val value = valueObj?.get("value")?.toString() ?: it["value"]?.toString() ?: ""
        val value2 = valueObj?.get("value2")?.toString()
        val modifierStr = it["modifier"] as? String ?: "EQUALS"
        val modifier = try { CriterionModifier.valueOf(modifierStr) } catch (e: Exception) { CriterionModifier.EQUALS }
        return DateCriterionInput(
            value = value,
            value2 = if (!value2.isNullOrBlank()) Optional.present(value2) else Optional.absent(),
            modifier = modifier
        )
    }

    fun convertTimestampCriterionInput(it: Map<String, *>?): TimestampCriterionInput? {
        if (it == null) return null
        val valueObj = it["value"] as? Map<*, *>
        val value = valueObj?.get("value")?.toString() ?: it["value"]?.toString() ?: ""
        val value2 = valueObj?.get("value2")?.toString()
        val modifierStr = it["modifier"] as? String ?: "EQUALS"
        val modifier = try { CriterionModifier.valueOf(modifierStr) } catch (e: Exception) { CriterionModifier.EQUALS }
        return TimestampCriterionInput(
            value = value,
            value2 = if (!value2.isNullOrBlank()) Optional.present(value2) else Optional.absent(),
            modifier = modifier
        )
    }

    private fun convertToResolutionEnum(str: String): ResolutionEnum =
        when (str.uppercase()) {
            "144P", "VERY_LOW" -> ResolutionEnum.VERY_LOW
            "240P", "LOW" -> ResolutionEnum.LOW
            "360P", "R360P" -> ResolutionEnum.R360P
            "480P", "STANDARD" -> ResolutionEnum.STANDARD
            "540P", "WEB_HD" -> ResolutionEnum.WEB_HD
            "720P", "STANDARD_HD" -> ResolutionEnum.STANDARD_HD
            "1080P", "FULL_HD" -> ResolutionEnum.FULL_HD
            "1440P", "QUAD_HD" -> ResolutionEnum.QUAD_HD
            "4K", "FOUR_K" -> ResolutionEnum.FOUR_K
            "5K", "FIVE_K" -> ResolutionEnum.FIVE_K
            "6K", "SIX_K" -> ResolutionEnum.SIX_K
            "7K", "SEVEN_K" -> ResolutionEnum.SEVEN_K
            "8K", "EIGHT_K" -> ResolutionEnum.EIGHT_K
            "HUGE" -> ResolutionEnum.HUGE
            else -> try { ResolutionEnum.valueOf(str.uppercase()) } catch (e: Exception) { ResolutionEnum.FULL_HD }
        }

    fun convertResolutionCriterionInput(it: Map<String, *>?): ResolutionCriterionInput? {
        if (it == null) return null
        val valStr = it["value"]?.toString() ?: ""
        val modifierStr = it["modifier"]?.toString() ?: "GREATER_THAN"
        return ResolutionCriterionInput(
            value = convertToResolutionEnum(valStr),
            modifier = try { CriterionModifier.valueOf(modifierStr) } catch (e: Exception) { CriterionModifier.GREATER_THAN },
        )
    }

    fun convertOrientationCriterionInput(it: Map<String, *>?): OrientationCriterionInput? {
        if (it == null) return null
        val value = it["value"]
        val enumList = if (value is List<*>) {
            value.mapNotNull {
                try { OrientationEnum.valueOf(it.toString().uppercase()) } catch (e: Exception) { null }
            }
        } else if (value != null) {
            listOfNotNull(try { OrientationEnum.valueOf(value.toString().uppercase()) } catch (e: Exception) { null })
        } else emptyList()

        if (enumList.isEmpty()) return null
        return OrientationCriterionInput(value = enumList)
    }

    fun convertPhashDistanceCriterionInput(it: Map<String, *>?): PhashDistanceCriterionInput? {
        if (it == null) return null
        val values = it["value"] as? Map<*, *>
        val valStr = values?.get("value")?.toString() ?: it["value"]?.toString() ?: ""
        val distance = values?.get("distance")?.toString()?.toIntOrNull()
        val modifierStr = it["modifier"]?.toString() ?: "EQUALS"
        return PhashDistanceCriterionInput(
            value = valStr,
            modifier = try { CriterionModifier.valueOf(modifierStr) } catch (e: Exception) { CriterionModifier.EQUALS },
            distance = if (distance != null) Optional.present(distance) else Optional.absent()
        )
    }

    fun convertDuplicationCriterionInput(it: Map<String, *>?): DuplicationCriterionInput? {
        if (it == null) return null
        val dup = it["value"]?.toString()?.toBoolean()
        val dist = it["distance"]?.toString()?.toIntOrNull()
        val phash = it["phash"]?.toString()?.toBoolean()
        val url = it["url"]?.toString()?.toBoolean()
        val stashId = it["stash_id"]?.toString()?.toBoolean()
        val title = it["title"]?.toString()?.toBoolean()
        return DuplicationCriterionInput(
            duplicated = if (dup != null) Optional.present(dup) else Optional.absent(),
            distance = if (dist != null) Optional.present(dist) else Optional.absent(),
            phash = if (phash != null) Optional.present(phash) else Optional.absent(),
            url = if (url != null) Optional.present(url) else Optional.absent(),
            stash_id = if (stashId != null) Optional.present(stashId) else Optional.absent(),
            title = if (title != null) Optional.present(title) else Optional.absent()
        )
    }

    private fun mapToIds(list: Any?): List<String>? {
        if (list == null) return null
        return when (list) {
            is List<*> -> list.mapNotNull { item ->
                when (item) {
                    is Map<*, *> -> item["id"]?.toString() ?: item["value"]?.toString()?.let { if (it.isNotBlank()) it else null } ?: item.toString()
                    else -> item?.toString()
                }
            }.filter { it.isNotBlank() }
            is Map<*, *> -> {
                val subItems = list["items"] ?: list["value"]
                mapToIds(subItems)
            }
            is String -> if (list.isNotBlank()) listOf(list) else null
            is Number -> listOf(list.toString())
            else -> null
        }
    }

    fun convertMultiCriterionInput(it: Map<String, *>?): MultiCriterionInput? {
        if (it == null) return null
        val modifierStr = it["modifier"] as? String ?: "INCLUDES"
        val modifier = try { CriterionModifier.valueOf(modifierStr) } catch (e: Exception) { CriterionModifier.INCLUDES }

        val valObj = it["value"]
        val items = when (valObj) {
            is Map<*, *> -> mapToIds(valObj["items"]) ?: mapToIds(valObj["value"])
            else -> mapToIds(valObj)
        }
        val excludes = when (valObj) {
            is Map<*, *> -> mapToIds(valObj["excluded"]) ?: mapToIds(valObj["excludes"])
            else -> null
        }

        if (items.isNullOrEmpty() && excludes.isNullOrEmpty() && modifier != CriterionModifier.IS_NULL && modifier != CriterionModifier.NOT_NULL) {
            return null
        }

        return MultiCriterionInput(
            value = if (!items.isNullOrEmpty()) Optional.present(items) else Optional.absent(),
            excludes = if (!excludes.isNullOrEmpty()) Optional.present(excludes) else Optional.absent(),
            modifier = modifier
        )
    }

    fun convertHierarchicalMultiCriterionInput(it: Map<String, *>?): HierarchicalMultiCriterionInput? {
        if (it == null) return null
        val modifierStr = it["modifier"] as? String ?: "INCLUDES"
        val modifier = try { CriterionModifier.valueOf(modifierStr) } catch (e: Exception) { CriterionModifier.INCLUDES }

        val valObj = it["value"]
        val items = when (valObj) {
            is Map<*, *> -> mapToIds(valObj["items"]) ?: mapToIds(valObj["value"])
            else -> mapToIds(valObj)
        }
        val excludes = when (valObj) {
            is Map<*, *> -> mapToIds(valObj["excluded"]) ?: mapToIds(valObj["excludes"])
            else -> null
        }
        val depth = when (valObj) {
            is Map<*, *> -> valObj["depth"]?.toString()?.toIntOrNull()
            else -> null
        }

        if (items.isNullOrEmpty() && excludes.isNullOrEmpty() && modifier != CriterionModifier.IS_NULL && modifier != CriterionModifier.NOT_NULL) {
            return null
        }

        return HierarchicalMultiCriterionInput(
            value = if (!items.isNullOrEmpty()) Optional.present(items) else Optional.absent(),
            excludes = if (!excludes.isNullOrEmpty()) Optional.present(excludes) else Optional.absent(),
            depth = if (depth != null) Optional.present(depth) else Optional.absent(),
            modifier = modifier
        )
    }

    fun SceneFilterType.isEmpty(): Boolean {
        return performers is Optional.Absent &&
               tags is Optional.Absent &&
               studios is Optional.Absent &&
               groups is Optional.Absent &&
               rating100 is Optional.Absent &&
               o_counter is Optional.Absent &&
               play_count is Optional.Absent &&
               duration is Optional.Absent &&
               date is Optional.Absent &&
               created_at is Optional.Absent &&
               updated_at is Optional.Absent &&
               organized is Optional.Absent &&
               has_markers is Optional.Absent &&
               is_missing is Optional.Absent &&
               title is Optional.Absent &&
               code is Optional.Absent &&
               details is Optional.Absent &&
               director is Optional.Absent &&
               url is Optional.Absent &&
               path is Optional.Absent &&
               resolution is Optional.Absent &&
               orientation is Optional.Absent &&
               framerate is Optional.Absent &&
               bitrate is Optional.Absent &&
               file_count is Optional.Absent &&
               video_codec is Optional.Absent &&
               audio_codec is Optional.Absent &&
               oshash is Optional.Absent &&
               checksum is Optional.Absent &&
               phash_distance is Optional.Absent &&
               duplicated is Optional.Absent &&
               tag_count is Optional.Absent &&
               performer_tags is Optional.Absent &&
               performer_favorite is Optional.Absent &&
               performer_age is Optional.Absent &&
               performer_count is Optional.Absent &&
               AND is Optional.Absent &&
               OR is Optional.Absent &&
               NOT is Optional.Absent
    }

    @Suppress("UNCHECKED_CAST")
    fun convertSceneFilterType(f: Any?): SceneFilterType? {
        if (f == null) return null
        if (f is SceneFilterType) return if (f.isEmpty()) null else f

        val map: Map<String, Map<String, *>>? = when (f) {
            is Map<*, *> -> f as? Map<String, Map<String, *>>
            is String -> {
                if (f.trim().startsWith("{")) {
                    try {
                        JSONObject(f).toMap() as? Map<String, Map<String, *>>
                    } catch (e: Exception) {
                        null
                    }
                } else null
            }
            else -> null
        }

        if (map == null) return null

        val andFilter = convertSceneFilterType(map["AND"])
        val orFilter = convertSceneFilterType(map["OR"])
        val notFilter = convertSceneFilterType(map["NOT"])

        val filter = SceneFilterType(
            AND = if (andFilter != null) Optional.present(andFilter) else Optional.absent(),
            OR = if (orFilter != null) Optional.present(orFilter) else Optional.absent(),
            NOT = if (notFilter != null) Optional.present(notFilter) else Optional.absent(),
            performers = Optional.presentIfNotNull(convertMultiCriterionInput(map["performers"])),
            tags = Optional.presentIfNotNull(convertHierarchicalMultiCriterionInput(map["tags"])),
            studios = Optional.presentIfNotNull(convertHierarchicalMultiCriterionInput(map["studios"])),
            groups = Optional.presentIfNotNull(convertHierarchicalMultiCriterionInput(map["groups"])),
            movies = Optional.presentIfNotNull(convertMultiCriterionInput(map["movies"])),
            galleries = Optional.presentIfNotNull(convertMultiCriterionInput(map["galleries"])),
            rating100 = Optional.presentIfNotNull(convertIntCriterionInput(map["rating100"])),
            o_counter = Optional.presentIfNotNull(convertIntCriterionInput(map["o_counter"])),
            play_count = Optional.presentIfNotNull(convertIntCriterionInput(map["play_count"])),
            play_duration = Optional.presentIfNotNull(convertIntCriterionInput(map["play_duration"])),
            duration = Optional.presentIfNotNull(convertIntCriterionInput(map["duration"])),
            date = Optional.presentIfNotNull(convertDateCriterionInput(map["date"])),
            created_at = Optional.presentIfNotNull(convertTimestampCriterionInput(map["created_at"])),
            updated_at = Optional.presentIfNotNull(convertTimestampCriterionInput(map["updated_at"])),
            last_played_at = Optional.presentIfNotNull(convertTimestampCriterionInput(map["last_played_at"])),
            organized = Optional.presentIfNotNull(convertBoolean(map["organized"])),
            has_markers = Optional.presentIfNotNull(convertString(map["has_markers"])),
            is_missing = Optional.presentIfNotNull(convertString(map["is_missing"])),
            title = Optional.presentIfNotNull(convertStringCriterionInput(map["title"])),
            code = Optional.presentIfNotNull(convertStringCriterionInput(map["code"])),
            details = Optional.presentIfNotNull(convertStringCriterionInput(map["details"])),
            director = Optional.presentIfNotNull(convertStringCriterionInput(map["director"])),
            url = Optional.presentIfNotNull(convertStringCriterionInput(map["url"])),
            path = Optional.presentIfNotNull(convertStringCriterionInput(map["path"])),
            resolution = Optional.presentIfNotNull(convertResolutionCriterionInput(map["resolution"])),
            orientation = Optional.presentIfNotNull(convertOrientationCriterionInput(map["orientation"])),
            framerate = Optional.presentIfNotNull(convertIntCriterionInput(map["framerate"])),
            bitrate = Optional.presentIfNotNull(convertIntCriterionInput(map["bitrate"])),
            file_count = Optional.presentIfNotNull(convertIntCriterionInput(map["file_count"])),
            video_codec = Optional.presentIfNotNull(convertStringCriterionInput(map["video_codec"])),
            audio_codec = Optional.presentIfNotNull(convertStringCriterionInput(map["audio_codec"])),
            oshash = Optional.presentIfNotNull(convertStringCriterionInput(map["oshash"])),
            checksum = Optional.presentIfNotNull(convertStringCriterionInput(map["checksum"])),
            phash_distance = Optional.presentIfNotNull(convertPhashDistanceCriterionInput(map["phash_distance"])),
            duplicated = Optional.presentIfNotNull(convertDuplicationCriterionInput(map["duplicated"])),
            tag_count = Optional.presentIfNotNull(convertIntCriterionInput(map["tag_count"])),
            performer_tags = Optional.presentIfNotNull(convertHierarchicalMultiCriterionInput(map["performer_tags"])),
            performer_favorite = Optional.presentIfNotNull(convertBoolean(map["performer_favorite"])),
            performer_age = Optional.presentIfNotNull(convertIntCriterionInput(map["performer_age"])),
            performer_count = Optional.presentIfNotNull(convertIntCriterionInput(map["performer_count"]))
        )

        return if (filter.isEmpty()) null else filter
    }
}
