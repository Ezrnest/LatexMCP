package com.github.ezrnest.latexmcp

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

private const val BUNDLE = "messages.MyMessageBundle"

/**
 * Central accessor for localized plugin messages.
 *
 * Keeping all lookups here avoids scattering bundle name strings across UI code.
 */
internal object MyMessageBundle {
    private val instance = DynamicBundle(MyMessageBundle::class.java, BUNDLE)

    /**
     * Resolve a message immediately with optional format parameters.
     */
    @JvmStatic
    fun message(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any?): @Nls String {
        return instance.getMessage(key, *params)
    }

    /**
     * Resolve a message lazily for APIs that defer string computation.
     */
    @JvmStatic
    fun lazyMessage(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any?): Supplier<@Nls String> {
        return instance.getLazyMessage(key, *params)
    }
}
