package org.example.hoon.cinematicCore.util.animation.event

import org.bukkit.entity.LivingEntity
import org.example.hoon.cinematicCore.model.domain.BlockbenchModel
import org.example.hoon.cinematicCore.model.domain.animation.BlockbenchAnimation
import org.example.hoon.cinematicCore.model.domain.animation.EventKeyframe

/**
 * 애니메이션 이벤트가 발생할 때 전달되는 컨텍스트 정보
 */
data class AnimationEventContext(
    val entity: LivingEntity,
    val model: BlockbenchModel,
    val animation: BlockbenchAnimation,
    val keyframe: EventKeyframe,
    val animationTimeSeconds: Float
)

