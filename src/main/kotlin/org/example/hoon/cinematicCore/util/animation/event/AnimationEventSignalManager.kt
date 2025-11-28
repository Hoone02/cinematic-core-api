package org.example.hoon.cinematicCore.util.animation.event

import org.bukkit.entity.LivingEntity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

typealias AnimationEventHandler = (AnimationEventContext) -> Unit

class AnimationEventReceiver internal constructor(
    private val entityId: UUID
) {
    private val handlers: MutableMap<String, MutableList<AnimationEventHandler>> = ConcurrentHashMap()

    fun on(script: String, handler: AnimationEventHandler) {
        val key = normalize(script)
        val list = handlers.getOrPut(key) { mutableListOf() }
        list.add(handler)
    }

    fun off(script: String, handler: AnimationEventHandler? = null) {
        val key = normalize(script)
        val existing = handlers[key] ?: return
        if (handler == null) {
            handlers.remove(key)
            return
        }
        existing.removeIf { it == handler }
        if (existing.isEmpty()) {
            handlers.remove(key)
        }
    }

    fun clear() {
        handlers.clear()
    }

    internal fun dispatch(script: String, context: AnimationEventContext) {
        val key = normalize(script)
        val listeners = handlers[key] ?: return
        listeners.forEach { listener ->
            runCatching { listener(context) }
        }
    }

    private fun normalize(value: String): String {
        return value.trim().lowercase()
    }
}

object AnimationEventSignalManager {
    private val receivers: MutableMap<UUID, AnimationEventReceiver> = ConcurrentHashMap()

    fun register(entity: LivingEntity): AnimationEventReceiver {
        return receivers.getOrPut(entity.uniqueId) {
            AnimationEventReceiver(entity.uniqueId)
        }
    }

    fun get(entity: LivingEntity): AnimationEventReceiver? {
        return receivers[entity.uniqueId]
    }

    fun unregister(entity: LivingEntity) {
        receivers.remove(entity.uniqueId)?.clear()
    }

    fun dispatch(
        entity: LivingEntity,
        script: String,
        context: AnimationEventContext
    ) {
        receivers[entity.uniqueId]?.dispatch(script, context)
    }
}

