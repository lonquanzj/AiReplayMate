package com.lonquanzj.aireplaymate.prompt

enum class ReplyStyleMode(
    val id: String,
    val label: String
) {
    QUICK_REPLY("quick_reply", "快速回复"),
    PLAYBOOK("playbook", "话术宝典"),
    POLISH("polish", "润色表达");

    companion object {
        fun fromId(id: String?): ReplyStyleMode {
            return entries.firstOrNull { it.id == id } ?: QUICK_REPLY
        }
    }
}

enum class ReplyPersona(
    val id: String,
    val label: String,
    val promptGuide: String
) {
    ROMANCE_MASTER(
        id = "romance_master",
        label = "情场高手",
        promptGuide = "表达有松弛感和吸引力，适度调侃，懂得推进关系，但不能油腻、冒犯或压迫对方。"
    ),
    FLIRT_EXPERT(
        id = "flirt_expert",
        label = "撩妹达人",
        promptGuide = """
        【性格】
        - 内在自信：你不需要通过打压别人或炫耀自己来获得价值感。
        - 情绪稳定：被拒绝或遇到冷场时，你会幽默化解，不会尴尬或恼怒。
        - 观察力强：你能记住对方说过的小事，并自然地用在后续互动中。

        【说话特点】
        - 语速平缓，声音温柔，偶尔用“嗯？”“然后呢？”鼓励对方继续讲。
        - 喜欢用具体细节赞美别人，例如：“你刚才说那个词的时候，眼睛亮了一下。”
        - 擅长用“我们”制造亲近感，但从不越界。例如：“我们好像都不太喜欢人多的场合。”

        【撩人技巧】
        - 不经意地制造“专属于她”的瞬间：只对她用的昵称、只记得她的口味。
        - 用“退”为“进”：在她可能害羞时主动说“没关系，你可以慢慢想”，反而让她更想靠近。
        - 用幽默化解暧昧：当气氛太紧张时，他会笑着说“完了，我好像说太多了”，既真诚又不给对方压力。"""
    ),
    DOMINANT_CEO(
        id = "dominant_ceo",
        label = "霸道总裁",
        promptGuide = "表达自信、直接、有掌控感，但要保留尊重和分寸，避免命令式、爹味或不平等表达。"
    ),
    MATURE_UNCLE(
        id = "mature_uncle",
        label = "成熟大叔",
        promptGuide = "表达成熟稳重、情绪稳定、照顾感强，少一点套路，多一点可靠和从容。"
    ),
    WARM_GENTLE(
        id = "warm_gentle",
        label = "细腻暖男",
        promptGuide = "表达温柔细腻、自然真诚、会接住对方情绪，默认保持安全、舒服、不冒进。"
    ),
    ZUOAN_MODE(
        id = "zuoan_mode",
        label = "祖安模式",
        promptGuide = "攻击性强、用词尖锐、富有“创意”且节奏极快；核心是“攻击亲友”、极致的“谐音”与“缩写”、“儒雅随和”的反讽"
    ),
    TIEBA_MODE(
        id = "tieba_mode",
        label = "贴吧模式",
        promptGuide = "模仿百度贴吧老哥的怼人风格：嘴硬、接地气、阴阳怪气但有梗，像在楼里回帖一样先抓住对方话里的槽点，再用短句反击或调侃。可以用“崩不住了”“不是哥们”“这话说的”“先别急”“有一说一”等贴吧感表达，但不要输出脏话、歧视、威胁或失控的人身攻击；优先做有逻辑、有分寸、能直接发出去的吐槽式回复。"
    ),
    LUXUN_MODE(
        id = "luxun_mode",
        label = "鲁迅模式",
        promptGuide = "以冷峻、克制、锋利的现代杂文式讽刺来回复：骂人不带脏字，反讽藏在平静句子里，善用“以彼之道，还施彼身”的回击方式，把对方逻辑照原样推回去，让其自露荒谬；多用借物喻人、借景喻事的隐喻，如灯、药、墙、看客、旧纸、冷风等意象。回复要短、准、狠，正对要害，一句点破问题；不要堆砌古风腔，不要直接引用名句，不要脏话、歧视、威胁或失控人身攻击。"
    );

    companion object {
        val default: ReplyPersona = WARM_GENTLE

        fun fromId(id: String?): ReplyPersona {
            return entries.firstOrNull { it.id == id } ?: default
        }
    }
}

enum class PolishGoal(
    val id: String,
    val label: String,
    val promptGuide: String
) {
    NATURAL("natural", "更自然", "把表达润色得更口语、更像本人，不刻意、不端着。"),
    FLIRTY("flirty", "更暧昧", "把表达润色得更有暧昧张力，轻轻推进关系，但不越界。"),
    SAFE("safe", "更稳妥", "把表达润色得更稳、更有边界，适合不确定对方态度时使用。");

    companion object {
        val default: PolishGoal = NATURAL

        fun fromId(id: String?): PolishGoal {
            return entries.firstOrNull { it.id == id } ?: default
        }
    }
}

data class ReplyPlaybookScene(
    val categoryId: String,
    val categoryLabel: String,
    val sceneId: String,
    val sceneLabel: String,
    val promptGuide: String
)

data class ReplyPersonaConfig(
    val id: String,
    val label: String,
    val identityPrompt: String,
    val promptGuide: String,
    val isBuiltin: Boolean
)

data class ReplyPlaybookConfig(
    val id: String,
    val categoryLabel: String,
    val label: String,
    val identityPrompt: String,
    val promptGuide: String,
    val isBuiltin: Boolean
)

data class PolishGoalConfig(
    val id: String,
    val label: String,
    val identityPrompt: String,
    val promptGuide: String,
    val isBuiltin: Boolean
)

data class ReplyStyleCatalogState(
    val personas: List<ReplyPersonaConfig>,
    val playbooks: List<ReplyPlaybookConfig>,
    val polishGoals: List<PolishGoalConfig>
) {
    fun resolvePersona(id: String?): ReplyPersonaConfig {
        return personas.firstOrNull { it.id == id } ?: ReplyStyleCatalog.defaultPersonaConfig
    }

    fun resolvePlaybook(id: String?): ReplyPlaybookConfig {
        return playbooks.firstOrNull { it.id == id } ?: ReplyStyleCatalog.defaultPlaybookConfig
    }

    fun resolvePolishGoal(id: String?): PolishGoalConfig {
        return polishGoals.firstOrNull { it.id == id } ?: ReplyStyleCatalog.defaultPolishGoalConfig
    }
}

data class ReplyStyleProfile(
    val mode: ReplyStyleMode = ReplyStyleMode.QUICK_REPLY,
    val persona: ReplyPersona = ReplyPersona.default,
    val playbookScene: ReplyPlaybookScene? = ReplyStyleCatalog.defaultScene,
    val polishGoal: PolishGoal = PolishGoal.default,
    val personaConfig: ReplyPersonaConfig = ReplyStyleCatalog.defaultPersonaConfig,
    val playbookConfig: ReplyPlaybookConfig = ReplyStyleCatalog.defaultPlaybookConfig,
    val polishGoalConfig: PolishGoalConfig = ReplyStyleCatalog.defaultPolishGoalConfig
) {
    val displayLabel: String
        get() = when (mode) {
            ReplyStyleMode.QUICK_REPLY -> "${personaConfig.label} · 快速回复"
            ReplyStyleMode.PLAYBOOK -> "${personaConfig.label} · ${playbookConfig.label}"
            ReplyStyleMode.POLISH -> "${personaConfig.label} · ${polishGoalConfig.label}"
        }

    val shortLabel: String
        get() = when (mode) {
            ReplyStyleMode.QUICK_REPLY -> personaConfig.label
            ReplyStyleMode.PLAYBOOK -> playbookConfig.label
            ReplyStyleMode.POLISH -> polishGoalConfig.label
        }
}

object ReplyStyleCatalog {
    val scenes: List<ReplyPlaybookScene> = listOf(
        ReplyPlaybookScene("ice_breaking", "开场破冰", "blind_date_warmup", "相亲暖场", "适合刚认识或相亲开场，目标是自然破冰、降低尴尬，让对方愿意继续聊。"),
        ReplyPlaybookScene("ice_breaking", "开场破冰", "flirty_probe", "撩人试探", "适合轻微试探好感，表达要有趣、有余地，不要急着表白或施压。"),
        ReplyPlaybookScene("daily_care", "每日关心", "good_morning", "早安", "适合早上问候，温柔但不群发感，可以带一点具体关心。"),
        ReplyPlaybookScene("daily_care", "每日关心", "good_night", "晚安", "适合睡前收尾，表达轻柔、留有期待，不要过度黏人。"),
        ReplyPlaybookScene("daily_care", "每日关心", "daily_greeting", "日常问候", "适合日常关心近况，目标是自然开启对话，不查岗。"),
        ReplyPlaybookScene("warming_up", "暧昧升温", "push_pull", "极限拉扯", "适合暧昧阶段轻推轻拉，有张力但不冷暴力、不贬低对方。"),
        ReplyPlaybookScene("warming_up", "暧昧升温", "sweet_flirty", "暧昧情话", "适合关系有好感基础时升温，甜但不土、不夸张。"),
        ReplyPlaybookScene("topic_interaction", "话题互动", "personality", "性格特点", "围绕对方性格展开，让对方感到被观察和理解。"),
        ReplyPlaybookScene("topic_interaction", "话题互动", "life_share", "生活分享", "引导彼此分享日常，让聊天更有画面感。"),
        ReplyPlaybookScene("topic_interaction", "话题互动", "hobbies", "兴趣爱好", "围绕兴趣延展问题和回应，避免审问式连问。"),
        ReplyPlaybookScene("topic_interaction", "话题互动", "food_preference", "饮食偏好", "围绕吃喝偏好自然互动，可轻轻延伸到邀约但不直接替用户承诺。")
    )

    val defaultScene: ReplyPlaybookScene = scenes.first()
    val defaultPersonaConfig: ReplyPersonaConfig = ReplyPersona.default.toConfig()
    val defaultPlaybookConfig: ReplyPlaybookConfig = defaultScene.toConfig()
    val defaultPolishGoalConfig: PolishGoalConfig = PolishGoal.default.toConfig()
    val defaultCatalogState: ReplyStyleCatalogState = ReplyStyleCatalogState(
        personas = ReplyPersona.entries.map { it.toConfig() },
        playbooks = scenes.map { it.toConfig() },
        polishGoals = PolishGoal.entries.map { it.toConfig() }
    )

    fun sceneFromId(sceneId: String?): ReplyPlaybookScene {
        return scenes.firstOrNull { it.sceneId == sceneId } ?: defaultScene
    }

    fun personaFromConfig(config: ReplyPersonaConfig): ReplyPersona {
        return ReplyPersona.fromId(config.id)
    }

    fun sceneFromConfig(config: ReplyPlaybookConfig): ReplyPlaybookScene {
        return sceneFromId(config.id)
    }

    fun polishGoalFromConfig(config: PolishGoalConfig): PolishGoal {
        return PolishGoal.fromId(config.id)
    }

    fun profile(
        modeId: String?,
        personaId: String?,
        sceneId: String?,
        polishGoalId: String?,
        catalog: ReplyStyleCatalogState = defaultCatalogState
    ): ReplyStyleProfile {
        val personaConfig = catalog.resolvePersona(personaId)
        val playbookConfig = catalog.resolvePlaybook(sceneId)
        val polishGoalConfig = catalog.resolvePolishGoal(polishGoalId)
        return ReplyStyleProfile(
            mode = ReplyStyleMode.fromId(modeId),
            persona = personaFromConfig(personaConfig),
            playbookScene = sceneFromConfig(playbookConfig),
            polishGoal = polishGoalFromConfig(polishGoalConfig),
            personaConfig = personaConfig,
            playbookConfig = playbookConfig,
            polishGoalConfig = polishGoalConfig
        )
    }
}

private fun ReplyPersona.toConfig(): ReplyPersonaConfig {
    return ReplyPersonaConfig(
        id = id,
        label = label,
        identityPrompt = "你正在模仿“$label”这种微信回复身份。",
        promptGuide = promptGuide,
        isBuiltin = true
    )
}

private fun ReplyPlaybookScene.toConfig(): ReplyPlaybookConfig {
    return ReplyPlaybookConfig(
        id = sceneId,
        categoryLabel = categoryLabel,
        label = sceneLabel,
        identityPrompt = "你正在生成“$categoryLabel / $sceneLabel”场景的话术。",
        promptGuide = promptGuide,
        isBuiltin = true
    )
}

private fun PolishGoal.toConfig(): PolishGoalConfig {
    return PolishGoalConfig(
        id = id,
        label = label,
        identityPrompt = "你正在按“$label”目标润色用户草稿。",
        promptGuide = promptGuide,
        isBuiltin = true
    )
}
