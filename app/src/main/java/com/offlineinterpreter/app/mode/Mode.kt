package com.offlineinterpreter.app.mode

enum class Mode {
    /** 模式A：纯手机同声传译（持续收音→翻译→手机扬声器播译文） */
    SIMULTANEOUS,
    /** 模式B：蓝牙耳机双声道（左右声道各一种语言译文） */
    BT_STEREO,
    /** 模式C：耳机+扬声器分离（耳机一种语言，扬声器另一种语言） */
    BT_SPEAKER_SPLIT,
    /** 模式D：文本翻译 */
    TEXT_TRANSLATION,
    /** 模式E：按住说话语音翻译 */
    PUSH_TO_TALK,
}

enum class TranslationDirection {
    ZH_EN,  // 中文 → 英文
    EN_ZH,  // 英文 → 中文
    ;

    val srcLang: String get() = if (this == ZH_EN) "Chinese" else "English"
    val tgtLang: String get() = if (this == ZH_EN) "English" else "Chinese"
    val srcCode:  String get() = if (this == ZH_EN) "zh" else "en"
    val tgtCode:  String get() = if (this == ZH_EN) "en" else "zh"

    fun toggle() = if (this == ZH_EN) EN_ZH else ZH_EN
}
