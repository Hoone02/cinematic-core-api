package org.example.hoon.cinematicCore.api.animation

import org.bukkit.entity.LivingEntity
import org.example.hoon.cinematicCore.util.animation.event.AnimationEventHandler
import org.example.hoon.cinematicCore.util.animation.event.AnimationEventReceiver
import org.example.hoon.cinematicCore.util.animation.event.AnimationEventSignalManager

/**
 * 애니메이션 EventKeyframe에 반응하기 위한 API
 */
object AnimationEventAPI {

    /**
     * 지정한 엔티티에 대한 이벤트 리시버를 등록합니다.
     */
    fun register(entity: LivingEntity): AnimationEventReceiver {
        return AnimationEventSignalManager.register(entity)
    }

    /**
     * 스크립트 이름으로 이벤트 리스너를 추가합니다.
     */
    fun on(entity: LivingEntity, script: String, handler: AnimationEventHandler) {
        AnimationEventSignalManager.register(entity).on(script, handler)
    }

    /**
     * 스크립트 이름으로 등록된 리스너를 제거합니다.
     * handler가 null이면 해당 스크립트에 등록된 모든 리스너를 제거합니다.
     */
    fun off(entity: LivingEntity, script: String, handler: AnimationEventHandler? = null) {
        AnimationEventSignalManager.get(entity)?.off(script, handler)
    }

    /**
     * 엔티티에 등록된 리스너를 모두 제거합니다.
     */
    fun clear(entity: LivingEntity) {
        AnimationEventSignalManager.get(entity)?.clear()
    }

    /**
     * 엔티티가 제거될 때 호출하여 리시버를 정리합니다.
     */
    fun unregister(entity: LivingEntity) {
        AnimationEventSignalManager.unregister(entity)
    }
}

