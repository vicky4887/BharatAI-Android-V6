package com.bharatai.education.localai

/**
 * Local-only llama.cpp JNI entry points.
 * Native inference is OPTIONAL at compile time; the original offline solvers and OCR
 * remain fully usable when the native library is absent. Never run on the UI thread.
 */
object NativeLlama {
    val available: Boolean by lazy {
        try {
            System.loadLibrary("bharatai_llm")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    external fun initialize(path: String, contextTokens: Int, threads: Int): String
    external fun generate(prompt: String, maxNewTokens: Int): String
    external fun release()
}
