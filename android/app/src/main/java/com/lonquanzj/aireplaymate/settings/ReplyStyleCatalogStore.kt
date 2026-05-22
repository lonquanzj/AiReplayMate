package com.lonquanzj.aireplaymate.settings

import android.app.backup.BackupManager
import android.content.Context
import com.lonquanzj.aireplaymate.prompt.PolishGoalConfig
import com.lonquanzj.aireplaymate.prompt.ReplyPersonaConfig
import com.lonquanzj.aireplaymate.prompt.ReplyPlaybookConfig
import com.lonquanzj.aireplaymate.prompt.ReplyStyleCatalog
import com.lonquanzj.aireplaymate.prompt.ReplyStyleCatalogState
import org.json.JSONArray
import org.json.JSONObject

object ReplyStyleCatalogStore {
    private const val PREFS_NAME = "ai_replay_mate_reply_style_catalog"
    private const val KEY_CATALOG_JSON = "catalog_json"

    fun load(context: Context): ReplyStyleCatalogState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CATALOG_JSON, null)
        return raw?.let(::decodeCatalog)
            ?.mergeWithCurrentBuiltins()
            ?: ReplyStyleCatalog.defaultCatalogState
    }

    fun save(
        context: Context,
        catalog: ReplyStyleCatalogState
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CATALOG_JSON, encodeCatalog(catalog))
            .apply()
        BackupManager(context.applicationContext).dataChanged()
    }

    fun defaultCatalog(): ReplyStyleCatalogState = ReplyStyleCatalog.defaultCatalogState

    fun encodeToJson(catalog: ReplyStyleCatalogState): JSONObject {
        return JSONObject(encodeCatalog(catalog))
    }

    fun decodeFromJson(root: JSONObject): ReplyStyleCatalogState {
        return decodeCatalog(root.toString())?.mergeWithCurrentBuiltins()
            ?: ReplyStyleCatalog.defaultCatalogState
    }

    fun resetBuiltins(
        context: Context,
        current: ReplyStyleCatalogState = load(context)
    ): ReplyStyleCatalogState {
        val customPersonas = current.personas.filterNot { it.isBuiltin }
        val customPlaybooks = current.playbooks.filterNot { it.isBuiltin }
        val customPolishGoals = current.polishGoals.filterNot { it.isBuiltin }
        val next = ReplyStyleCatalog.defaultCatalogState.copy(
            personas = ReplyStyleCatalog.defaultCatalogState.personas + customPersonas,
            playbooks = ReplyStyleCatalog.defaultCatalogState.playbooks + customPlaybooks,
            polishGoals = ReplyStyleCatalog.defaultCatalogState.polishGoals + customPolishGoals
        )
        save(context, next)
        return next
    }

    fun newCustomId(prefix: String): String {
        return "${prefix}_${System.currentTimeMillis().toString(36)}"
    }

    private fun ReplyStyleCatalogState.mergeWithCurrentBuiltins(): ReplyStyleCatalogState {
        return ReplyStyleCatalogState(
            personas = mergeBuiltins(
                defaults = ReplyStyleCatalog.defaultCatalogState.personas,
                current = personas,
                idOf = ReplyPersonaConfig::id
            ),
            playbooks = mergeBuiltins(
                defaults = ReplyStyleCatalog.defaultCatalogState.playbooks,
                current = playbooks,
                idOf = ReplyPlaybookConfig::id
            ),
            polishGoals = mergeBuiltins(
                defaults = ReplyStyleCatalog.defaultCatalogState.polishGoals,
                current = polishGoals,
                idOf = PolishGoalConfig::id
            )
        )
    }

    private fun <T> mergeBuiltins(
        defaults: List<T>,
        current: List<T>,
        idOf: (T) -> String
    ): List<T> {
        val currentById = current.associateBy(idOf)
        val mergedBuiltins = defaults.map { defaultItem ->
            currentById[idOf(defaultItem)] ?: defaultItem
        }
        val customItems = current.filter { currentItem ->
            defaults.none { idOf(it) == idOf(currentItem) }
        }
        return mergedBuiltins + customItems
    }

    private fun encodeCatalog(catalog: ReplyStyleCatalogState): String {
        return JSONObject()
            .put("personas", JSONArray(catalog.personas.map { it.toJson() }))
            .put("playbooks", JSONArray(catalog.playbooks.map { it.toJson() }))
            .put("polishGoals", JSONArray(catalog.polishGoals.map { it.toJson() }))
            .toString()
    }

    private fun decodeCatalog(raw: String): ReplyStyleCatalogState? {
        return runCatching {
            val root = JSONObject(raw)
            ReplyStyleCatalogState(
                personas = root.optJSONArray("personas").toList { it.toPersonaConfig() },
                playbooks = root.optJSONArray("playbooks").toList { it.toPlaybookConfig() },
                polishGoals = root.optJSONArray("polishGoals").toList { it.toPolishGoalConfig() }
            )
        }.getOrNull()
    }

    private fun ReplyPersonaConfig.toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("label", label)
            .put("identityPrompt", identityPrompt)
            .put("promptGuide", promptGuide)
            .put("isBuiltin", isBuiltin)
    }

    private fun ReplyPlaybookConfig.toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("categoryLabel", categoryLabel)
            .put("label", label)
            .put("identityPrompt", identityPrompt)
            .put("promptGuide", promptGuide)
            .put("isBuiltin", isBuiltin)
    }

    private fun PolishGoalConfig.toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("label", label)
            .put("identityPrompt", identityPrompt)
            .put("promptGuide", promptGuide)
            .put("isBuiltin", isBuiltin)
    }

    private fun JSONObject.toPersonaConfig(): ReplyPersonaConfig {
        return ReplyPersonaConfig(
            id = optString("id"),
            label = optString("label"),
            identityPrompt = optString("identityPrompt"),
            promptGuide = optString("promptGuide"),
            isBuiltin = optBoolean("isBuiltin")
        )
    }

    private fun JSONObject.toPlaybookConfig(): ReplyPlaybookConfig {
        return ReplyPlaybookConfig(
            id = optString("id"),
            categoryLabel = optString("categoryLabel"),
            label = optString("label"),
            identityPrompt = optString("identityPrompt"),
            promptGuide = optString("promptGuide"),
            isBuiltin = optBoolean("isBuiltin")
        )
    }

    private fun JSONObject.toPolishGoalConfig(): PolishGoalConfig {
        return PolishGoalConfig(
            id = optString("id"),
            label = optString("label"),
            identityPrompt = optString("identityPrompt"),
            promptGuide = optString("promptGuide"),
            isBuiltin = optBoolean("isBuiltin")
        )
    }

    private fun <T> JSONArray?.toList(mapper: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optJSONObject(index)?.let { add(mapper(it)) }
            }
        }
    }
}
