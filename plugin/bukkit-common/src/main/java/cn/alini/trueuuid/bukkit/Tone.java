package cn.alini.trueuuid.bukkit;

/** Semantic colour of one player- or operator-facing line. Renderers own the actual palette. */
public enum Tone {
    SUCCESS,
    INFO,
    WARNING,
    ERROR,
    /** A secondary detail line under a prefixed message. It is rendered without the prefix. */
    DETAIL
}
