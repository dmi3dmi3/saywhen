package com.dmi3dmi3.saywhen.parser

internal enum class Confidence(val weight: Int) {
    EXPLICIT(100),
    STRONG(70),
    WEAK(30),
}
