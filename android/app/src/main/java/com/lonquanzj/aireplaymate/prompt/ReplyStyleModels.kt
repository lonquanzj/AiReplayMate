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
        promptGuide = "表达直率犀利、吐槽不留情、敢于怼人且不掉线，有个性有脾气。但要保持逻辑清晰和原则性，不刻意贬低、不越过安全底线。"
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

data class ReplyStyleProfile(
    val mode: ReplyStyleMode = ReplyStyleMode.QUICK_REPLY,
    val persona: ReplyPersona = ReplyPersona.default,
    val playbookScene: ReplyPlaybookScene? = ReplyStyleCatalog.defaultScene,
    val polishGoal: PolishGoal = PolishGoal.default
) {
    val displayLabel: String
        get() = when (mode) {
            ReplyStyleMode.QUICK_REPLY -> "${persona.label} · 快速回复"
            ReplyStyleMode.PLAYBOOK -> "${persona.label} · ${playbookScene?.sceneLabel ?: "话术宝典"}"
            ReplyStyleMode.POLISH -> "${persona.label} · ${polishGoal.label}"
        }

    val shortLabel: String
        get() = when (mode) {
            ReplyStyleMode.QUICK_REPLY -> persona.label
            ReplyStyleMode.PLAYBOOK -> playbookScene?.sceneLabel ?: "话术宝典"
            ReplyStyleMode.POLISH -> polishGoal.label
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

    fun sceneFromId(sceneId: String?): ReplyPlaybookScene {
        return scenes.firstOrNull { it.sceneId == sceneId } ?: defaultScene
    }

    fun profile(
        modeId: String?,
        personaId: String?,
        sceneId: String?,
        polishGoalId: String?
    ): ReplyStyleProfile {
        return ReplyStyleProfile(
            mode = ReplyStyleMode.fromId(modeId),
            persona = ReplyPersona.fromId(personaId),
            playbookScene = sceneFromId(sceneId),
            polishGoal = PolishGoal.fromId(polishGoalId)
        )
    }
}
