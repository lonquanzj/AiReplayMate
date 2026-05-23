package com.lonquanzj.aireplaymate.ui

import com.lonquanzj.aireplaymate.prompt.ReplyStyleMode
import com.lonquanzj.aireplaymate.prompt.ReplyStyleProfile

internal fun styleExample(profile: ReplyStyleProfile): String {
    return "短按气泡：按“${profile.personaConfig.label}”生成默认回复；长按气泡：话术宝典按“${profile.playbookConfig.label}”单次出文案，润色按“${profile.polishGoalConfig.label}”读取输入框草稿并回写候选。"
}

internal fun ReplyStyleProfile.asDefaultReply(): ReplyStyleProfile {
    return copy(mode = ReplyStyleMode.QUICK_REPLY)
}
