package com.lagradost.cloudstream3.utils

import kotlin.reflect.KClass

/**
 * Headless Injekt / Dependency Injection container compatibility bridge for CloudStream extensions.
 * Provides service resolution, singleton scoping, and factory providers.
 */
object Injekt {
    private val instances = mutableMapOf<KClass<*>, Any>()
    private val factories = mutableMapOf<KClass<*>, () -> Any>()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(clazz: KClass<T>): T {
        instances[clazz]?.let { return it as T }
        factories[clazz]?.let { factory ->
            val instance = factory() as T
            instances[clazz] = instance
            return instance
        }
        throw IllegalArgumentException("No instance or factory found for ${clazz.qualifiedName}")
    }

    inline fun <reified T : Any> get(): T = get(T::class)

    fun <T : Any> addSingleton(clazz: KClass<T>, instance: T) {
        instances[clazz] = instance
    }

    inline fun <reified T : Any> addSingleton(instance: T) {
        addSingleton(T::class, instance)
    }

    fun <T : Any> addFactory(clazz: KClass<T>, factory: () -> T) {
        factories[clazz] = factory
    }

    inline fun <reified T : Any> addFactory(noinline factory: () -> T) {
        addFactory(T::class, factory)
    }
}
