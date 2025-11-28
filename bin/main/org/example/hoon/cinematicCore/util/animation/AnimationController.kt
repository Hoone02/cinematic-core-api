package org.example.hoon.cinematicCore.util.animation

import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.entity.Player
import org.example.hoon.cinematicCore.model.domain.BlockbenchModel
import org.example.hoon.cinematicCore.model.domain.animation.AnimationKeyframe
import org.example.hoon.cinematicCore.model.domain.animation.AnimationLoopMode
import org.example.hoon.cinematicCore.model.domain.animation.BlockbenchAnimation
import org.example.hoon.cinematicCore.model.domain.animation.BoneAnimation
import org.example.hoon.cinematicCore.model.domain.animation.BezierHandles
import org.example.hoon.cinematicCore.model.domain.animation.EventKeyframe
import org.example.hoon.cinematicCore.model.domain.animation.KeyframeInterpolation
import org.example.hoon.cinematicCore.model.domain.animation.RotationKeyframe
import org.example.hoon.cinematicCore.model.domain.animation.RotationBezierHandles
import org.bukkit.entity.LivingEntity
import org.example.hoon.cinematicCore.model.domain.getBone
import org.example.hoon.cinematicCore.model.domain.getBonesByTag
import org.example.hoon.cinematicCore.model.domain.getParentBoneChain
import org.example.hoon.cinematicCore.model.store.ModelRotationRegistry
import org.example.hoon.cinematicCore.util.animation.event.AnimationEventContext
import org.example.hoon.cinematicCore.util.animation.event.AnimationEventSignalManager
import org.example.hoon.cinematicCore.util.math.InterpolationUtil
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 애니메이션 상태를 관리하고 Forward Kinematics를 호출하는 컨트롤러
 */
class AnimationController(
    private val plugin: Plugin,
    private val model: BlockbenchModel,
    private val mapper: BoneDisplayMapper
) {
    private val transforms: MutableMap<String, BoneTransform> = mutableMapOf()
    private val initialTransforms: MutableMap<String, BoneTransform> = mutableMapOf()
    private val displayInitialScales: MutableMap<String, Vector3f> = mutableMapOf()
    private val displayInitialTranslations: MutableMap<String, Vector3f> = mutableMapOf()
    private val forwardKinematics: ForwardKinematics
    private var animationTask: BukkitRunnable? = null
    private var isRunning = false
    private var useIntegratedTask: Boolean = false  // 통합 태스크 사용 여부

    private var currentAnimation: BlockbenchAnimation? = null
    private var isAnimationPlaying: Boolean = false
    private var isAnimationFinished: Boolean = false
    private var animationTimeSeconds: Float = 0f
    private var animationSpeed: Float = 1f
    private var updateIntervalSeconds: Float = DEFAULT_TICK_SECONDS
    private var lastFrameTransforms: MutableMap<String, BoneTransform> = mutableMapOf()
    private val preparedAnimationCache: MutableMap<String, BlockbenchAnimation> = mutableMapOf()
    
    // 애니메이션 끊김 제어 관련 변수
    private var isInterruptible: Boolean = true  // 현재 애니메이션이 끊겨도 되는지 여부
    private var pendingAnimation: BlockbenchAnimation? = null  // 끊기면 안되는 애니메이션 재생 중에 요청된 다음 애니메이션
    private var pendingAnimationSpeed: Float = 1f  // pendingAnimation의 재생 속도
    
    // 애니메이션 블렌딩 관련 변수
    private var isBlending: Boolean = false
    private var blendTime: Float = 0f
    private var blendDuration: Float = 0.3f // 블렌딩 시간 (초)
    private val blendFromTransforms: MutableMap<String, BoneTransform> = mutableMapOf()
    private var blendFromAnimation: BlockbenchAnimation? = null
    private var blendFromAnimationTime: Float = 0f
    private var blendFromAnimationSpeed: Float = 1f
    private val animationSampleBuffer: MutableMap<String, BoneTransform> = mutableMapOf()
    private val firedEventKeyframeIds: MutableSet<String> = mutableSetOf()
    
    // baseEntity 참조 (h 태그 bone이 시야를 따라가도록)
    private var baseEntity: LivingEntity? = null
    private var isBaseEntityDead: Boolean = false  // baseEntity가 죽었는지 여부 (death 애니메이션 재생을 위해)
    
    // 스마트 업데이트 스킵 관련 변수
    private var needsUpdate: Boolean = true  // 업데이트 필요 여부
    // Location.clone() 대신 좌표값만 저장하여 GC 부하 감소
    private var previousBaseEntityX: Double = 0.0
    private var previousBaseEntityY: Double = 0.0
    private var previousBaseEntityZ: Double = 0.0
    private var hasPreviousBaseEntityLocation: Boolean = false
    private var previousBaseEntityYaw: Float? = null  // baseEntity 이전 yaw
    private var previousBaseEntityPitch: Float? = null  // baseEntity 이전 pitch
    private var interpolationInitialized: Boolean = false  // Display Interpolation 초기화 여부

    init {
        initializeBoneTransforms()
        forwardKinematics = ForwardKinematics(model, mapper, transforms, initialTransforms, displayInitialScales, displayInitialTranslations)
    }
    
    /**
     * baseEntity 설정 (h 태그 bone이 시야를 따라가도록)
     */
    fun setBaseEntity(entity: LivingEntity?) {
        baseEntity = entity
        forwardKinematics.setBaseEntity(entity)
        // baseEntity 변경 시 업데이트 필요
        needsUpdate = true
        hasPreviousBaseEntityLocation = false
        previousBaseEntityYaw = null
        previousBaseEntityPitch = null
        isBaseEntityDead = false  // baseEntity 변경 시 죽음 상태 초기화
    }
    
    /**
     * baseEntity가 죽었음을 표시 (death 애니메이션 재생을 위해)
     */
    fun markBaseEntityDead() {
        isBaseEntityDead = true
        needsUpdate = true  // death 애니메이션 재생을 위해 업데이트 필요
    }
    
    /**
     * bone 교체 정보 설정
     */
    fun setBoneReplacement(boneUuid: String, replacementModel: BlockbenchModel, replacementBoneName: String) {
        forwardKinematics.setBoneReplacement(boneUuid, replacementModel, replacementBoneName)
    }

    /**
     * Bone의 초기 변환을 설정
     */
    private fun initializeBoneTransforms() {
        model.bones.forEach { bone ->
            val rotationOverride = ModelRotationRegistry.getBoneRotation(model.modelName, bone.uuid)
            val baseTransform = BoneTransform.fromBone(bone, rotationOverride)

            val display = mapper.getDisplay(bone)
            val displayInitialScale = if (display != null && display.transformation != null) {
                Vector3f(display.transformation.scale)
            } else {
                Vector3f(1f, 1f, 1f)
            }
            displayInitialScales[bone.uuid] = displayInitialScale
            
            val displayInitialTranslation = if (display != null && display.transformation != null) {
                Vector3f(display.transformation.translation)
            } else {
                Vector3f(0f, 0f, 0f)
            }
            displayInitialTranslations[bone.uuid] = displayInitialTranslation

            val finalTransform = baseTransform.copy(scale = Vector3f(1f, 1f, 1f))
            transforms[bone.uuid] = finalTransform
            initialTransforms[bone.uuid] = BoneTransform(
                translation = Vector3f(finalTransform.translation),
                rotation = Quaternionf(finalTransform.rotation),
                scale = Vector3f(finalTransform.scale)
            )
            
            // Display Interpolation 초기화 (한 번만 설정)
            if (!interpolationInitialized && display != null) {
                display.interpolationDuration = 1
                display.interpolationDelay = 0
            }
        }
        interpolationInitialized = true
    }

    fun setBoneRotation(boneUuid: String, rotation: Quaternionf) {
        val transform = transforms[boneUuid] ?: return
        transforms[boneUuid] = transform.copy(rotation = Quaternionf(rotation))
        needsUpdate = true
    }

    fun setBoneRotationEuler(boneUuid: String, pitch: Float, yaw: Float, roll: Float) {
        val rotation = Quaternionf()
        rotation.rotateX(Math.toRadians(pitch.toDouble()).toFloat())
            .rotateY(Math.toRadians(yaw.toDouble()).toFloat())
            .rotateZ(Math.toRadians(roll.toDouble()).toFloat())
        setBoneRotation(boneUuid, rotation)
    }

    fun setBoneTranslation(boneUuid: String, translation: Vector3f) {
        val transform = transforms[boneUuid] ?: return
        transforms[boneUuid] = transform.copy(translation = Vector3f(translation))
        needsUpdate = true
    }

    fun setBoneScale(boneUuid: String, scale: Vector3f) {
        val transform = transforms[boneUuid] ?: return
        transforms[boneUuid] = transform.copy(scale = Vector3f(scale))
        needsUpdate = true
    }

    fun setBoneRotationByName(boneName: String, rotation: Quaternionf) {
        val bone = model.getBone(boneName) ?: return
        setBoneRotation(bone.uuid, rotation)
    }

    fun setBoneRotationEulerByName(boneName: String, pitch: Float, yaw: Float, roll: Float) {
        val bone = model.getBone(boneName) ?: return
        setBoneRotationEuler(bone.uuid, pitch, yaw, roll)
    }

    fun getBoneTransform(boneUuid: String): BoneTransform? = transforms[boneUuid]

    fun getBoneTransformByName(boneName: String): BoneTransform? {
        val bone = model.getBone(boneName) ?: return null
        return transforms[bone.uuid]
    }

    fun overrideDisplayInitialScale(boneUuid: String, scale: Vector3f) {
        displayInitialScales[boneUuid] = Vector3f(scale)
    }

    fun update() {
        if (currentAnimation != null) {
            evaluateAnimationAtCurrentTime()
        } else {
            // 애니메이션이 없을 때는 초기 상태로 리셋
            resetTransformsToInitial()
        }
        forwardKinematics.update()
        needsUpdate = false
    }
    
    /**
     * 통합 태스크에서 호출하는 업데이트 메서드
     */
    fun updateFromTask() {
        // Display Interpolation 설정 (매 틱마다 필요할 수 있음)
        if (!interpolationInitialized) {
            model.bones.forEach { bone ->
                val display = mapper.getDisplay(bone) ?: return@forEach
                display.interpolationDuration = 1
                display.interpolationDelay = 0
            }
            interpolationInitialized = true
        }
        
        // baseEntity가 죽었지만 death 애니메이션이 재생 중인 경우 계속 업데이트
        // 애니메이션이 끝났을 때는 완전히 정지 (아무것도 하지 않음)
        if (!isAnimationFinished) {
            advanceAnimation(updateIntervalSeconds)
            forwardKinematics.update()
        }
        needsUpdate = false
    }
    
    /**
     * 업데이트 필요 여부 확인
     */
    fun needsUpdate(): Boolean {
        // 이미 업데이트가 필요하다고 마킹되어 있으면 true
        if (needsUpdate) {
            return true
        }
        
        // 애니메이션이 재생 중이면 업데이트 필요
        if (isAnimationPlaying && !isAnimationFinished) {
            return true
        }
        
        // baseEntity가 죽었지만 death 애니메이션이 재생 중인 경우 업데이트 필요
        if (isBaseEntityDead && isAnimationPlaying && !isAnimationFinished) {
            return true
        }
        
        // baseEntity가 Player인 경우 (disguise) 항상 업데이트 필요 (시야 추적)
        if (baseEntity is Player && !isBaseEntityDead) {
            return true
        }
        
        // baseEntity의 위치나 회전이 변경되었는지 확인 (h 태그 bone용)
        // baseEntity가 죽었으면 위치/회전 추적 불필요
        if (baseEntity != null && baseEntity!!.isValid && !isBaseEntityDead) {
            val currentLocation = baseEntity!!.location
            val currentX = currentLocation.x
            val currentY = currentLocation.y
            val currentZ = currentLocation.z
            val currentYaw = baseEntity!!.yaw
            val currentPitch = baseEntity!!.pitch
            
            val prevYaw = previousBaseEntityYaw
            val prevPitch = previousBaseEntityPitch
            
            // 위치 변경 확인 (distance 계산 대신 직접 비교하여 GC 부하 감소)
            if (!hasPreviousBaseEntityLocation || 
                abs(currentX - previousBaseEntityX) > 0.01 || 
                abs(currentY - previousBaseEntityY) > 0.01 || 
                abs(currentZ - previousBaseEntityZ) > 0.01) {
                previousBaseEntityX = currentX
                previousBaseEntityY = currentY
                previousBaseEntityZ = currentZ
                hasPreviousBaseEntityLocation = true
                return true
            }
            
            // 회전 변경 확인 (h 태그 bone이 시야를 따라가므로)
            if (prevYaw == null || prevPitch == null || 
                abs(currentYaw - prevYaw) > 0.1f || abs(currentPitch - prevPitch) > 0.1f) {
                previousBaseEntityYaw = currentYaw
                previousBaseEntityPitch = currentPitch
                return true
            }
        }
        
        return false
    }
    
    /**
     * 컨트롤러가 유효한지 확인
     */
    fun isValid(): Boolean {
        // baseEntity가 죽었지만 death 애니메이션이 재생 중인 경우는 유효
        if (isBaseEntityDead && isAnimationPlaying && !isAnimationFinished) {
            // display들이 유효한지만 확인
            model.bones.forEach { bone ->
                val display = mapper.getDisplay(bone) ?: return@forEach
                if (!display.isValid) {
                    return false
                }
            }
            return true
        }
        
        // baseEntity가 유효하지 않으면 false
        if (baseEntity != null && !baseEntity!!.isValid) {
            return false
        }
        
        // mapper의 display들이 유효한지 확인
        model.bones.forEach { bone ->
            val display = mapper.getDisplay(bone) ?: return@forEach
            if (!display.isValid) {
                return false
            }
        }
        
        return true
    }
    
    /**
     * Forward Kinematics만 업데이트 (애니메이션 평가 없이)
     * bone의 transform이 변경된 후 FK만 적용할 때 사용
     */
    fun updateForwardKinematics() {
        forwardKinematics.update()
    }

    fun start(period: Long = 1L) {
        if (isRunning) {
            stop()
        }

        isRunning = true
        updateIntervalSeconds = period.coerceAtLeast(1L).toFloat() * DEFAULT_TICK_SECONDS
        
        // disguise인 경우 (baseEntity가 Player) 기존 방식 유지
        val isDisguise = baseEntity is Player
        
        if (isDisguise) {
            // disguise는 기존 방식대로 독립 태스크 사용
            useIntegratedTask = false
            animationTask = object : BukkitRunnable() {
                override fun run() {
                    if (!isRunning) {
                        cancel()
                        return
                    }
                    model.bones.forEach { bone ->
                        val display = mapper.getDisplay(bone) ?: return@forEach
                        display.interpolationDuration = 1
                        display.interpolationDelay = 0
                    }

                    // 애니메이션이 끝났을 때는 완전히 정지 (아무것도 하지 않음)
                    if (!isAnimationFinished) {
                        advanceAnimation(updateIntervalSeconds)
                        forwardKinematics.update()
                    }
                    // isAnimationFinished가 true일 때는 아무것도 하지 않음 (애니메이션 완전 정지)
                }
            }
            animationTask?.runTaskTimer(plugin, 0L, period)
        } else {
            // 일반 모델은 통합 태스크 사용
            useIntegratedTask = true
            val taskManager = AnimationTaskManager.getInstance(plugin)
            taskManager.register(this)
        }
    }

    fun stop() {
        isRunning = false
        
        if (useIntegratedTask) {
            // 통합 태스크에서 해제
            val taskManager = AnimationTaskManager.getInstance(plugin)
            taskManager.unregister(this)
        } else {
            // 독립 태스크 취소
            animationTask?.cancel()
            animationTask = null
        }
    }

    fun isRunning(): Boolean = isRunning

    fun tick(deltaSeconds: Float) {
        advanceAnimation(deltaSeconds)
        forwardKinematics.update()
    }

    fun reset() {
        initializeBoneTransforms()
        currentAnimation = null
        isAnimationPlaying = false
        animationTimeSeconds = 0f
        firedEventKeyframeIds.clear()
        isInterruptible = true  // 리셋 시 끊겨도 되는 상태로 초기화
        pendingAnimation = null  // pendingAnimation도 초기화
        forwardKinematics.update()
    }

    fun adjustAllBoneTranslationsY(yOffset: Float) {
        model.bones.forEach { bone ->
            val transform = transforms[bone.uuid] ?: return@forEach
            val adjustedTranslation = Vector3f(transform.translation).apply { y += yOffset }
            transforms[bone.uuid] = transform.copy(translation = adjustedTranslation)

            val initialTransform = initialTransforms[bone.uuid]
            if (initialTransform != null) {
                val adjustedInitialTranslation = Vector3f(initialTransform.translation).apply { y += yOffset }
                initialTransforms[bone.uuid] = initialTransform.copy(translation = adjustedInitialTranslation)
            }
        }
        needsUpdate = true
    }

    fun playAnimationByName(animationName: String, speed: Float = 1f, interruptible: Boolean = true) {
        val normalizedName = animationName.trim()
        val animation = model.animations.firstOrNull {
            it.name.equals(normalizedName, ignoreCase = true)
        } ?: return
        playAnimation(animation, speed, interruptible)
    }

    fun playAnimation(animation: BlockbenchAnimation, speed: Float = 1f, interruptible: Boolean = true) {
        // 같은 애니메이션이 이미 재생 중이면 중복 재생 방지 (이름으로 비교)
        val animationName = animation.name.trim()
        val previousAnimation = currentAnimation
        val previousAnimationTime = animationTimeSeconds
        val previousAnimationSpeed = animationSpeed
        
        if (previousAnimation != null && isAnimationPlaying) {
            val currentAnimationName = previousAnimation.name.trim()
            if (currentAnimationName.equals(animationName, ignoreCase = true)) {
                // 같은 애니메이션이 재생 중이면 아무것도 하지 않음
                return
            }
        }
        
        // 끊기면 안되는 애니메이션이 재생 중이면 새 애니메이션을 pendingAnimation에 저장하고 무시
        if (!isInterruptible && isAnimationPlaying && previousAnimation != null) {
            pendingAnimation = animation
            pendingAnimationSpeed = speed
            return
        }
        
        val hadPreviousAnimation = previousAnimation != null && isAnimationPlaying
        
        // 블렌딩을 위한 현재 상태 저장
        if (hadPreviousAnimation) {
            blendFromAnimation = previousAnimation
            blendFromAnimationTime = previousAnimationTime
            blendFromAnimationSpeed = previousAnimationSpeed
            computeAnimationTransforms(previousAnimation!!, blendFromAnimationTime, blendFromTransforms)
        } else {
            blendFromAnimation = null
            blendFromTransforms.clear()
        }
        
        val preparedAnimation = prepareAnimation(animation)
        currentAnimation = preparedAnimation
        animationSpeed = speed
        animationTimeSeconds = 0f
        isAnimationPlaying = true
        isAnimationFinished = false
        isInterruptible = interruptible  // 새 애니메이션의 끊김 가능 여부 설정
        lastFrameTransforms.clear()
        firedEventKeyframeIds.clear()
        
        // 이전 애니메이션이 존재했다면 즉시 블렌딩 시작
        isBlending = hadPreviousAnimation
        blendTime = 0f
        
        // 새 애니메이션의 첫 프레임 상태 계산 (블렌딩 여부에 따라 즉시 적용 또는 준비)
        evaluateAnimationAtCurrentTime()
        dispatchInitialEventKeyframes(preparedAnimation)
        
        forwardKinematics.update()
        needsUpdate = true  // 애니메이션 재생 시 업데이트 필요
    }

    fun pauseAnimation() {
        isAnimationPlaying = false
    }

    fun resumeAnimation() {
        if (currentAnimation != null) {
            isAnimationPlaying = true
        isAnimationFinished = false
        lastFrameTransforms.clear()
        }
    }

    fun stopAnimation(resetPose: Boolean = true, applyUpdate: Boolean = true) {
        isAnimationPlaying = false
        isAnimationFinished = false
        animationTimeSeconds = 0f
        firedEventKeyframeIds.clear()
        isInterruptible = true  // 애니메이션 정지 시 끊겨도 되는 상태로 리셋
        pendingAnimation = null  // pendingAnimation도 초기화
        if (resetPose) {
            currentAnimation = null
            resetTransformsToInitial()
            if (applyUpdate) {
                forwardKinematics.update()
            }
        }
    }

    fun isAnimationPlaying(): Boolean = isAnimationPlaying

    fun currentAnimation(): BlockbenchAnimation? = currentAnimation

    /**
     * idle 애니메이션을 재생 시도
     * @return idle 애니메이션이 있으면 true, 없으면 false
     */
    private fun tryPlayIdleAnimation(): Boolean {
        val idleAnimation = model.animations.firstOrNull {
            it.name.equals("idle", ignoreCase = true)
        } ?: return false
        
        playAnimation(idleAnimation, 1.0f)
        return true
    }

    /**
     * 마지막 프레임 상태를 가상의 정지 애니메이션으로 변환하여 재생
     * 이렇게 하면 보간 없이 마지막 프레임 상태를 정확히 유지
     */
    private fun playFreezeAnimation() {
        // 마지막 프레임 상태를 한 번만 계산
        evaluateAnimationAtCurrentTime(isLastFrame = true)
        forwardKinematics.update()
        
        // 현재 transform 상태를 initial 기준으로 오프셋으로 변환하여 키프레임 생성
        val boneAnimations = mutableMapOf<String, BoneAnimation>()
        
        model.bones.forEach { bone ->
            val boneUuid = bone.uuid
            val currentTransform = transforms[boneUuid] ?: return@forEach
            val initialTransform = initialTransforms[boneUuid] ?: return@forEach
            
            // 현재 상태에서 initial 상태를 뺀 오프셋 계산
            val positionOffset = Vector3f(currentTransform.translation).sub(initialTransform.translation)
            val scaleOffset = Vector3f(currentTransform.scale).div(initialTransform.scale)
            
            // 회전 오프셋 계산 (quaternion): initial * rotationOffset = current
            // rotationOffset = inverse(initial) * current
            val rotationOffset = Quaternionf(initialTransform.rotation).conjugate()
            rotationOffset.mul(currentTransform.rotation)
            
            // 오프셋이 0이 아닌 경우에만 키프레임 생성
            val positionKeyframes = mutableListOf<AnimationKeyframe>()
            val rotationKeyframes = mutableListOf<RotationKeyframe>()
            val scaleKeyframes = mutableListOf<AnimationKeyframe>()
            
            // 위치 오프셋을 Blockbench 좌표계로 변환
            if (positionOffset.lengthSquared() > APPROX_EPSILON * APPROX_EPSILON) {
                val blockbenchOffset = Vector3f(
                    -positionOffset.x * BLOCK_SCALE,
                    positionOffset.y * BLOCK_SCALE,
                    -positionOffset.z * BLOCK_SCALE
                )
                positionKeyframes.add(AnimationKeyframe(
                    time = 0f,
                    value = blockbenchOffset,
                    interpolation = KeyframeInterpolation.STEP
                ))
            }
            
            // 회전 오프셋
            val identityDot = rotationOffset.dot(IDENTITY_QUATERNION)
            if (abs(identityDot - 1.0f) > APPROX_EPSILON) {
                // quaternion을 euler로 변환
                val euler = Vector3f()
                rotationOffset.getEulerAnglesXYZ(euler)
                euler.mul(180f / Math.PI.toFloat()) // 라디안을 도로 변환
                
                rotationKeyframes.add(RotationKeyframe(
                    time = 0f,
                    euler = Vector3f(-euler.x, euler.y, -euler.z), // Blockbench 좌표계 변환
                    quaternion = Quaternionf(rotationOffset),
                    interpolation = KeyframeInterpolation.STEP
                ))
            }
            
            // 스케일 오프셋
            val scaleDiff = Vector3f(scaleOffset).sub(1f, 1f, 1f)
            if (scaleDiff.lengthSquared() > APPROX_EPSILON * APPROX_EPSILON) {
                scaleKeyframes.add(AnimationKeyframe(
                    time = 0f,
                    value = scaleOffset,
                    interpolation = KeyframeInterpolation.STEP
                ))
            }
            
            // 키프레임이 하나라도 있으면 BoneAnimation 생성
            if (positionKeyframes.isNotEmpty() || rotationKeyframes.isNotEmpty() || scaleKeyframes.isNotEmpty()) {
                boneAnimations[boneUuid] = BoneAnimation(
                    boneUuid = boneUuid,
                    boneName = bone.name,
                    positionKeyframes = positionKeyframes,
                    rotationKeyframes = rotationKeyframes,
                    scaleKeyframes = scaleKeyframes
                )
            }
        }
        
        // 가상의 정지 애니메이션 생성 (길이 0, HOLD 모드, STEP 보간)
        val freezeAnimation = BlockbenchAnimation(
            name = "__freeze__",
            length = 0f,
            loopMode = AnimationLoopMode.HOLD,
            boneAnimations = boneAnimations
        )
        
        // 정지 애니메이션 재생 (블렌딩 없이)
        isBlending = false
        blendFromAnimation = null
        blendFromTransforms.clear()
        currentAnimation = freezeAnimation
        firedEventKeyframeIds.clear()
        animationTimeSeconds = 0f
        isAnimationPlaying = true
        isAnimationFinished = true  // 정지 애니메이션은 이미 계산했으므로 true로 설정 (이후 transform 변경 안 함)
        isInterruptible = true  // 정지 애니메이션은 끊겨도 되는 애니메이션
        lastFrameTransforms.clear()
        
        // 정지 애니메이션 상태는 이미 위에서 evaluateAnimationAtCurrentTime(isLastFrame = true)로 계산됨
        // forwardKinematics.update()도 이미 위에서 호출됨
    }

    private fun advanceAnimation(deltaSeconds: Float) {
        val animation = currentAnimation ?: return
        if (!isAnimationPlaying) {
            return
        }

        // 블렌딩 시간 업데이트
        if (isBlending) {
            blendTime += deltaSeconds
            updateBlendFromAnimation(deltaSeconds)
            if (blendTime >= blendDuration) {
                isBlending = false
                blendTime = blendDuration
                clearBlendFromState()
            }
        }

        val length = animation.length.coerceAtLeast(0f)
        
        // 길이가 0인 정지 애니메이션은 이미 계산되었으므로 transform을 변경하지 않음
        // (부동소수점 누적 오차로 인한 부들거림 방지)
        if (length == 0f && animation.loopMode == AnimationLoopMode.HOLD && isAnimationFinished) {
            // 정지 애니메이션은 이미 한 번 계산되었으므로 아무것도 하지 않음 (transform 상태 유지)
            return
        }
        
        val previousTime = animationTimeSeconds
        var nextTime = previousTime + deltaSeconds * animationSpeed
        var loopWrapped = false

        if (length > 0f) {
            when (animation.loopMode) {
                AnimationLoopMode.LOOP -> {
                    if (nextTime >= length || nextTime < 0f) {
                        loopWrapped = true
                    }
                    nextTime = wrapLoopTime(nextTime, length)
                }
                AnimationLoopMode.HOLD, AnimationLoopMode.ONCE -> {
                    nextTime = nextTime.coerceIn(0f, length)
                }
            }
        } else {
            nextTime = nextTime.coerceAtLeast(0f)
        }

        processEventKeyframes(animation, previousTime, nextTime, length, loopWrapped)

        animationTimeSeconds = nextTime

        if (length > 0f) {
            when (animation.loopMode) {
                AnimationLoopMode.LOOP -> {
                    // loop 모드는 wrap 처리만 필요 (이미 적용됨)
                }
                AnimationLoopMode.HOLD -> {
                    if (animationTimeSeconds >= length) {
                        // 끊기면 안되는 애니메이션이 끝났으므로 다시 끊겨도 되는 상태로 리셋
                        isInterruptible = true
                        
                        // pendingAnimation이 있으면 재생, 없으면 idle 애니메이션이 있으면 재생, 없으면 정지 애니메이션으로 변환
                        val pending = pendingAnimation
                        if (pending != null) {
                            pendingAnimation = null
                            playAnimation(pending, pendingAnimationSpeed, interruptible = true)
                        } else {
                        tryPlayIdleAnimation() ?: playFreezeAnimation()
                        }
                        return
                    }
                }
                AnimationLoopMode.ONCE -> {
                    if (animationTimeSeconds >= length) {
                        // 끊기면 안되는 애니메이션이 끝났으므로 다시 끊겨도 되는 상태로 리셋
                        isInterruptible = true
                        
                        // pendingAnimation이 있으면 재생, 없으면 idle 애니메이션이 있으면 재생, 없으면 정지 애니메이션으로 변환
                        val pending = pendingAnimation
                        if (pending != null) {
                            pendingAnimation = null
                            playAnimation(pending, pendingAnimationSpeed, interruptible = true)
                        } else {
                        tryPlayIdleAnimation() ?: playFreezeAnimation()
                        }
                        return
                    }
                }
            }
        }

        evaluateAnimationAtCurrentTime()
    }

    private fun evaluateAnimationAtCurrentTime(isLastFrame: Boolean = false) {
        val animation = currentAnimation ?: return
        
        // 정지 애니메이션(길이 0, HOLD 모드)이고 이미 계산된 경우 transform을 변경하지 않음
        // (부동소수점 누적 오차로 인한 부들거림 방지)
        val length = animation.length.coerceAtLeast(0f)
        if (length == 0f && animation.loopMode == AnimationLoopMode.HOLD && isAnimationFinished) {
            return
        }
        
        computeAnimationTransforms(animation, animationTimeSeconds, animationSampleBuffer, isLastFrame)

        model.bones.forEach { bone ->
            val boneUuid = bone.uuid
            val targetTransform = animationSampleBuffer[boneUuid] ?: return@forEach
            val existingTransform = transforms[boneUuid]

            if (isBlending) {
                val fromTransform = blendFromTransforms[boneUuid]
                if (fromTransform != null) {
                    val blendFactor = (blendTime / blendDuration).coerceIn(0f, 1f)
                    val easedFactor = blendFactor * blendFactor * (3f - 2f * blendFactor)
                    
                    // 기존 transform이 있으면 재사용하여 새 객체 생성 최소화
                    if (existingTransform != null) {
                        fromTransform.translation.lerp(targetTransform.translation, easedFactor, existingTransform.translation)
                        fromTransform.rotation.slerp(targetTransform.rotation, easedFactor, existingTransform.rotation)
                        fromTransform.scale.lerp(targetTransform.scale, easedFactor, existingTransform.scale)
                    } else {
                        val blendedTranslation = Vector3f(fromTransform.translation).lerp(targetTransform.translation, easedFactor)
                        val blendedRotation = Quaternionf(fromTransform.rotation).slerp(targetTransform.rotation, easedFactor, Quaternionf())
                        val blendedScale = Vector3f(fromTransform.scale).lerp(targetTransform.scale, easedFactor)
                        transforms[boneUuid] = BoneTransform(
                            translation = blendedTranslation,
                            rotation = blendedRotation,
                            scale = blendedScale
                        )
                    }
                } else {
                    // 블렌딩 없을 때는 targetTransform 값을 직접 복사
                    if (existingTransform != null) {
                        existingTransform.translation.set(targetTransform.translation)
                        existingTransform.rotation.set(targetTransform.rotation)
                        existingTransform.scale.set(targetTransform.scale)
                    } else {
                        transforms[boneUuid] = BoneTransform(
                            translation = Vector3f(targetTransform.translation),
                            rotation = Quaternionf(targetTransform.rotation),
                            scale = Vector3f(targetTransform.scale)
                        )
                    }
                }
            } else {
                // 블렌딩 없을 때는 targetTransform 값을 직접 복사 (copy() 대신)
                if (existingTransform != null) {
                    existingTransform.translation.set(targetTransform.translation)
                    existingTransform.rotation.set(targetTransform.rotation)
                    existingTransform.scale.set(targetTransform.scale)
                } else {
                    transforms[boneUuid] = BoneTransform(
                        translation = Vector3f(targetTransform.translation),
                        rotation = Quaternionf(targetTransform.rotation),
                        scale = Vector3f(targetTransform.scale)
                    )
                }
            }
        }
    }

    private fun computeAnimationTransforms(
        animation: BlockbenchAnimation,
        timeSeconds: Float,
        destination: MutableMap<String, BoneTransform>,
        isLastFrame: Boolean = false
    ) {
        destination.clear()

        model.bones.forEach { bone ->
            val boneUuid = bone.uuid
            val initial = initialTransforms[boneUuid] ?: return@forEach
            val boneAnimation = animation.boneAnimations[boneUuid]

            val translation = Vector3f(initial.translation)
            val rotation = Quaternionf(initial.rotation)
            val scale = Vector3f(initial.scale)

            if (boneAnimation != null) {
                var hasPositionChange = false
                var hasRotationChange = false
                var hasScaleChange = false

                if (boneAnimation.positionKeyframes.isNotEmpty()) {
                    val positionOffset = if (isLastFrame) {
                        // 마지막 프레임에서는 마지막 키프레임 값을 직접 사용 (보간 없이)
                        val lastKeyframe = boneAnimation.positionKeyframes.lastOrNull()
                        if (lastKeyframe != null) {
                            Vector3f(lastKeyframe.value)
                        } else {
                            Vector3f()
                        }
                    } else {
                        evaluateKeyframes(
                            boneAnimation.positionKeyframes,
                            timeSeconds,
                            animation.loopMode,
                            animation.length
                        )
                    }
                    if (positionOffset.lengthSquared() > APPROX_EPSILON * APPROX_EPSILON) {
                        translation.add(convertPositionToMinecraft(positionOffset))
                        hasPositionChange = true
                    }
                }

                if (boneAnimation.rotationKeyframes.isNotEmpty()) {
                    val isSingleKeyframe = boneAnimation.rotationKeyframes.size == 1
                    val rotationDeltaQuat = if (isLastFrame) {
                        // 마지막 프레임에서는 마지막 키프레임 값을 직접 사용 (보간 없이)
                        // evaluateRotationKeyframes를 마지막 시간으로 호출하여 마지막 키프레임 값 얻기
                        evaluateRotationKeyframes(
                            boneAnimation.rotationKeyframes,
                            animation.length, // 마지막 시간으로 고정
                            animation.loopMode,
                            animation.length,
                            initial.rotation
                        )
                    } else {
                        evaluateRotationKeyframes(
                            boneAnimation.rotationKeyframes,
                            timeSeconds,
                            animation.loopMode,
                            animation.length,
                            initial.rotation
                        )
                    }
                    if (rotationDeltaQuat != null) {
                        val identityDot = rotationDeltaQuat.dot(IDENTITY_QUATERNION)
                        if (abs(identityDot - 1.0f) > APPROX_EPSILON) {
                            rotation.mul(rotationDeltaQuat)
                            if (!isSingleKeyframe) {
                                hasRotationChange = true
                            }
                        }
                    }
                }

                if (boneAnimation.scaleKeyframes.isNotEmpty()) {
                    val scaleDelta = if (isLastFrame) {
                        // 마지막 프레임에서는 마지막 키프레임 값을 직접 사용 (보간 없이)
                        val lastKeyframe = boneAnimation.scaleKeyframes.lastOrNull()
                        if (lastKeyframe != null) {
                            Vector3f(lastKeyframe.value)
                        } else {
                            Vector3f(1f, 1f, 1f)
                        }
                    } else {
                        evaluateKeyframes(
                            boneAnimation.scaleKeyframes,
                            timeSeconds,
                            animation.loopMode,
                            animation.length
                        )
                    }
                    val scaleDiff = Vector3f(scaleDelta).sub(1f, 1f, 1f)
                    if (scaleDiff.lengthSquared() > APPROX_EPSILON * APPROX_EPSILON) {
                        scale.mul(scaleDelta.x, scaleDelta.y, scaleDelta.z)
                        hasScaleChange = true
                    }
                }

                if (!hasPositionChange && !hasRotationChange && !hasScaleChange) {
                    val microRotation = Quaternionf()
                    val microAngle = timeSeconds * MICRO_ROTATION_SPEED
                    microRotation.rotateZ(microAngle)
                    rotation.mul(microRotation)
                }
            }

            destination[boneUuid] = BoneTransform(
                translation = translation,
                rotation = rotation,
                scale = scale
            )
        }
    }

    private fun updateBlendFromAnimation(deltaSeconds: Float) {
        val animation = blendFromAnimation ?: return
        var time = blendFromAnimationTime + deltaSeconds * blendFromAnimationSpeed
        val length = animation.length.coerceAtLeast(0f)

        if (length > 0f) {
            when (animation.loopMode) {
                AnimationLoopMode.LOOP -> time = wrapLoopTime(time, length)
                AnimationLoopMode.HOLD -> if (time >= length) {
                    time = length
                }
                AnimationLoopMode.ONCE -> if (time >= length) {
                    time = length
                }
            }
        }

        blendFromAnimationTime = time
        computeAnimationTransforms(animation, time, blendFromTransforms)
    }

    private fun clearBlendFromState() {
        blendFromAnimation = null
        blendFromTransforms.clear()
    }

    private fun dispatchInitialEventKeyframes(animation: BlockbenchAnimation) {
        if (animation.eventKeyframes.isEmpty()) {
            return
        }
        val entity = baseEntity ?: return
        if (!entity.isValid) {
            return
        }
        for (keyframe in animation.eventKeyframes) {
            if (keyframe.time > EVENT_TIME_EPSILON) {
                break
            }
            if (!firedEventKeyframeIds.add(keyframe.uuid)) {
                continue
            }
            dispatchEventKeyframe(entity, animation, keyframe)
        }
    }

    private fun processEventKeyframes(
        animation: BlockbenchAnimation,
        previousTime: Float,
        currentTime: Float,
        animationLength: Float,
        loopWrapped: Boolean
    ) {
        if (animation.eventKeyframes.isEmpty()) {
            return
        }
        val entity = baseEntity ?: return
        if (!entity.isValid) {
            return
        }

        val clampedPrevious = previousTime.coerceAtLeast(0f)
        val clampedCurrent = currentTime.coerceAtLeast(0f)

        if (loopWrapped && animation.loopMode == AnimationLoopMode.LOOP && animationLength > 0f) {
            if (clampedPrevious < animationLength) {
                handleEventRange(animation.eventKeyframes, clampedPrevious, animationLength, entity, animation)
            }
            firedEventKeyframeIds.clear()
            if (clampedCurrent >= 0f) {
                handleEventRange(animation.eventKeyframes, 0f, clampedCurrent, entity, animation)
            }
        } else {
            val start = min(clampedPrevious, clampedCurrent)
            val end = max(clampedPrevious, clampedCurrent)
            handleEventRange(animation.eventKeyframes, start, end, entity, animation)
        }
    }

    private fun handleEventRange(
        keyframes: List<EventKeyframe>,
        startTime: Float,
        endTime: Float,
        entity: LivingEntity,
        animation: BlockbenchAnimation
    ) {
        if (keyframes.isEmpty()) {
            return
        }
        if (endTime < startTime - EVENT_TIME_EPSILON) {
            return
        }

        val adjustedStart = startTime - EVENT_TIME_EPSILON
        val adjustedEnd = endTime + EVENT_TIME_EPSILON

        for (keyframe in keyframes) {
            val keyframeTime = keyframe.time
            if (keyframeTime < adjustedStart) {
                continue
            }
            if (keyframeTime > adjustedEnd) {
                break
            }
            if (!firedEventKeyframeIds.add(keyframe.uuid)) {
                continue
            }
            dispatchEventKeyframe(entity, animation, keyframe)
        }
    }

    private fun dispatchEventKeyframe(
        entity: LivingEntity,
        animation: BlockbenchAnimation,
        keyframe: EventKeyframe
    ) {
        if (!entity.isValid) {
            return
        }
        val context = AnimationEventContext(
            entity = entity,
            model = model,
            animation = animation,
            keyframe = keyframe,
            animationTimeSeconds = keyframe.time
        )
        AnimationEventSignalManager.dispatch(entity, keyframe.script, context)
    }

    private fun evaluateKeyframes(
        keyframes: List<AnimationKeyframe>,
        timeSeconds: Float,
        loopMode: AnimationLoopMode,
        animationLength: Float
    ): Vector3f {
        if (keyframes.isEmpty()) {
            return Vector3f()
        }

        // 원본 키프레임이 1개만 있으면 loop 키프레임 추가 없이 바로 반환 (보간 불필요)
        if (keyframes.size == 1) {
            return Vector3f(keyframes.first().value)
        }

        val isLooping = loopMode == AnimationLoopMode.LOOP && animationLength > 0f && keyframes.size > 1
        val effectiveKeyframes = when {
            isLooping -> appendLoopKeyframe(keyframes, animationLength)
            // HOLD나 ONCE 모드에서는 마지막 키프레임 이후에 동일한 값을 가진 가상 키프레임 추가
            (loopMode == AnimationLoopMode.HOLD || loopMode == AnimationLoopMode.ONCE) && animationLength > 0f && keyframes.isNotEmpty() -> {
                appendHoldKeyframe(keyframes, animationLength)
            }
            else -> keyframes
        }

        val firstTime = effectiveKeyframes.first().time
        val lastTime = effectiveKeyframes.last().time
        val clampedTime = timeSeconds.coerceIn(firstTime, lastTime)

        if (clampedTime <= firstTime + APPROX_EPSILON) {
            return Vector3f(effectiveKeyframes.first().value)
        }

        // 마지막 프레임 근처에서는 부동소수점 오차를 고려하여 정확히 마지막 키프레임 값 사용
        // 이렇게 하면 마지막 프레임에서 보간이 일어나지 않아 부들거림 방지
        if (clampedTime >= lastTime - APPROX_EPSILON) {
            return Vector3f(effectiveKeyframes.last().value)
        }

        var segmentIndex = 0
        for (i in 0 until effectiveKeyframes.size - 1) {
            val current = effectiveKeyframes[i]
            val next = effectiveKeyframes[i + 1]
            if (clampedTime >= current.time && clampedTime <= next.time) {
                segmentIndex = i
                break
            }
        }

        val prev = effectiveKeyframes[segmentIndex]
        val next = effectiveKeyframes[segmentIndex + 1]
        
        // 키프레임 값이 같아도 시간에 따라 미세한 변화를 추가 (이전 애니메이션 상태와 충돌 방지)
        val baseValue = Vector3f(prev.value)
        if (prev.value.distance(next.value) < APPROX_EPSILON) {
            // 값이 같아도 시간에 따라 미세한 변화 추가
            val duration = (next.time - prev.time).coerceAtLeast(MIN_SEGMENT_DURATION)
            val t = ((clampedTime - prev.time) / duration).coerceIn(0f, 1f)
            // 시각적으로 보이지 않을 정도로 미세한 변화 (0.0001 단위)
            val microOffset = Vector3f(
                Math.sin(t * Math.PI * 2.0).toFloat() * 0.0001f,
                Math.cos(t * Math.PI * 2.0).toFloat() * 0.0001f,
                Math.sin(t * Math.PI * 3.0).toFloat() * 0.0001f
            )
            baseValue.add(microOffset)
            return baseValue
        }
        
        val duration = (next.time - prev.time).coerceAtLeast(MIN_SEGMENT_DURATION)
        val t = ((clampedTime - prev.time) / duration).coerceIn(0f, 1f)

        return interpolateKeyframes(effectiveKeyframes, segmentIndex, t, isLooping)
    }

    private fun evaluateRotationKeyframes(
        keyframes: List<RotationKeyframe>,
        timeSeconds: Float,
        loopMode: AnimationLoopMode,
        animationLength: Float,
        initialRotation: Quaternionf
    ): Quaternionf? {
        if (keyframes.isEmpty()) {
            return null
        }

        // 원본 키프레임이 1개만 있으면 loop 키프레임 추가 없이 바로 처리
        if (keyframes.size == 1) {
            val keyframeQuat = keyframes.first().quaternion
            // 키프레임 quaternion이 초기 rotation과 거의 같으면 identity quaternion 반환 (부동소수점 오차 방지)
            val dot = keyframeQuat.dot(initialRotation)
            if (abs(dot - 1.0f) < APPROX_EPSILON || abs(dot + 1.0f) < APPROX_EPSILON) {
                return Quaternionf(IDENTITY_QUATERNION) // identity quaternion 재사용
            }
            return Quaternionf(keyframeQuat)
        }

        val isLooping = loopMode == AnimationLoopMode.LOOP && animationLength > 0f && keyframes.size > 1
        val effectiveKeyframes = when {
            isLooping -> appendLoopRotationKeyframe(keyframes, animationLength)
            // HOLD나 ONCE 모드에서는 마지막 키프레임 이후에 동일한 값을 가진 가상 키프레임 추가
            (loopMode == AnimationLoopMode.HOLD || loopMode == AnimationLoopMode.ONCE) && animationLength > 0f && keyframes.isNotEmpty() -> {
                appendHoldRotationKeyframe(keyframes, animationLength)
            }
            else -> keyframes
        }

        val firstTime = effectiveKeyframes.first().time
        val lastTime = effectiveKeyframes.last().time
        val clampedTime = timeSeconds.coerceIn(firstTime, lastTime)

        if (clampedTime <= firstTime + APPROX_EPSILON) {
            return Quaternionf(effectiveKeyframes.first().quaternion)
        }

        // 마지막 프레임 근처에서는 부동소수점 오차를 고려하여 정확히 마지막 키프레임 값 사용
        // 이렇게 하면 마지막 프레임에서 보간이 일어나지 않아 부들거림 방지
        if (clampedTime >= lastTime - APPROX_EPSILON) {
            return Quaternionf(effectiveKeyframes.last().quaternion)
        }

        var segmentIndex = 0
        for (i in 0 until effectiveKeyframes.size - 1) {
            val current = effectiveKeyframes[i]
            val next = effectiveKeyframes[i + 1]
            if (clampedTime >= current.time && clampedTime <= next.time) {
                segmentIndex = i
                break
            }
        }

        val prev = effectiveKeyframes[segmentIndex]
        val next = effectiveKeyframes[segmentIndex + 1]
        
        // 회전 키프레임이 같아도 시간에 따라 미세한 변화를 추가 (이전 애니메이션 상태와 충돌 방지)
        val dot = prev.quaternion.dot(next.quaternion)
        val hasMeaningfulEulerDifference = hasSignificantEulerDifference(prev.euler, next.euler)
        if (!hasMeaningfulEulerDifference &&
            (abs(dot - 1.0f) < APPROX_EPSILON || abs(dot + 1.0f) < APPROX_EPSILON)
        ) {
            // 값이 같아도 시간에 따라 미세한 rotation 변화 추가
            val duration = (next.time - prev.time).coerceAtLeast(MIN_SEGMENT_DURATION)
            val t = ((clampedTime - prev.time) / duration).coerceIn(0f, 1f)
            // 시각적으로 보이지 않을 정도로 미세한 rotation (0.0001 라디안 정도)
            val microRotation = Quaternionf()
            val microAngle = (t * Math.PI * 2.0).toFloat() * 0.0001f
            microRotation.rotateZ(microAngle)
            val result = Quaternionf(prev.quaternion)
            result.mul(microRotation)
            return result
        }
        
        val duration = (next.time - prev.time).coerceAtLeast(MIN_SEGMENT_DURATION)
        val t = ((clampedTime - prev.time) / duration).coerceIn(0f, 1f)

        return interpolateRotationKeyframes(effectiveKeyframes, segmentIndex, t, isLooping)
    }

    private fun resetTransformsToInitial() {
        initialTransforms.forEach { (boneUuid, initial) ->
            transforms[boneUuid] = BoneTransform(
                translation = Vector3f(initial.translation),
                rotation = Quaternionf(initial.rotation),
                scale = Vector3f(initial.scale)
            )
        }
    }

    private fun convertPositionToMinecraft(offset: Vector3f): Vector3f {
        return Vector3f(
            -offset.x / BLOCK_SCALE,
            offset.y / BLOCK_SCALE,
            -offset.z / BLOCK_SCALE
        )
    }

    private fun convertRotationToMinecraft(rotationDegrees: Vector3f): Vector3f {
        return Vector3f(
            rotationDegrees.x,
            rotationDegrees.y,
            -rotationDegrees.z
        )
    }

    private fun quaternionFromDegrees(rotationDegrees: Vector3f): Quaternionf {
        val quaternion = Quaternionf()
        quaternion.rotateZ(Math.toRadians(rotationDegrees.z.toDouble()).toFloat())
            .rotateY(Math.toRadians(rotationDegrees.y.toDouble()).toFloat())
            .rotateX(Math.toRadians(rotationDegrees.x.toDouble()).toFloat())
        return quaternion
    }
    
    /**
     * 각도 간 선형 보간 (360도 경계 처리)
     * 0도에서 360도로 보간할 때 올바르게 360도 회전하도록 처리
     *
     * 예: 0도에서 360도로 보간하면 0 -> 90 -> 180 -> 270 -> 360 순서로 회전
     */
    private fun lerpAngle(from: Float, to: Float, t: Float): Float {
        return from + (to - from) * t
    }

    /**
     * 두 Euler 각 벡터가 의미 있는 차이가 있는지 확인
     * 0/360도처럼 값은 다르지만 실질적으로 같은 경우를 구분하기 위해 사용
     */
    private fun hasSignificantEulerDifference(a: Vector3f, b: Vector3f, epsilon: Float = 0.01f): Boolean {
        val dx = abs(a.x - b.x)
        val dy = abs(a.y - b.y)
        val dz = abs(a.z - b.z)
        return dx > epsilon || dy > epsilon || dz > epsilon
    }

    private fun cubicBezier(p0: Vector3f, p1: Vector3f, p2: Vector3f, p3: Vector3f, t: Float): Vector3f {
        val u = 1f - t
        val tt = t * t
        val uu = u * u
        val uuu = uu * u
        val ttt = tt * t

        val result = Vector3f(p0).mul(uuu)
        result.add(Vector3f(p1).mul(3f * uu * t))
        result.add(Vector3f(p2).mul(3f * u * tt))
        result.add(Vector3f(p3).mul(ttt))
        return result
    }

    private fun wrapLoopTime(time: Float, length: Float): Float {
        if (length <= 0f) {
            return 0f
        }
        var wrapped = time % length
        if (wrapped < 0f) {
            wrapped += length
        }
        return wrapped
    }

    private fun Vector3f.isZero(epsilon: Float): Boolean {
        return (x * x + y * y + z * z) <= epsilon
    }

    private fun prepareAnimation(animation: BlockbenchAnimation): BlockbenchAnimation {
        if (animation.loopMode != AnimationLoopMode.LOOP || animation.length <= 0f) {
            return animation
        }
        preparedAnimationCache[animation.name]?.let { return it }

        val preparedBoneAnimations = animation.boneAnimations.mapValues { (_, boneAnimation) ->
            boneAnimation.copy(
                // 키프레임이 1개만 있으면 loop 키프레임 추가하지 않음 (부들거림 방지)
                positionKeyframes = if (boneAnimation.positionKeyframes.size == 1) {
                    boneAnimation.positionKeyframes
                } else {
                    appendLoopKeyframe(boneAnimation.positionKeyframes, animation.length)
                },
                rotationKeyframes = if (boneAnimation.rotationKeyframes.size == 1) {
                    boneAnimation.rotationKeyframes
                } else {
                    appendLoopRotationKeyframe(boneAnimation.rotationKeyframes, animation.length)
                },
                scaleKeyframes = if (boneAnimation.scaleKeyframes.size == 1) {
                    boneAnimation.scaleKeyframes
                } else {
                    appendLoopKeyframe(boneAnimation.scaleKeyframes, animation.length)
                }
            )
        }.toMap()

        val preparedAnimation = animation.copy(boneAnimations = preparedBoneAnimations)
        preparedAnimationCache[animation.name] = preparedAnimation
        return preparedAnimation
    }

    private fun appendLoopKeyframe(
        keyframes: List<AnimationKeyframe>,
        animationLength: Float
    ): List<AnimationKeyframe> {
        if (keyframes.isEmpty()) {
            return keyframes
        }
        val last = keyframes.last()
        if (abs(last.time - animationLength) <= MIN_SEGMENT_DURATION) {
            return keyframes
        }
        val first = keyframes.first()
        val appended = AnimationKeyframe(
            time = animationLength,
            value = Vector3f(first.value),
            interpolation = last.interpolation,
            bezier = first.bezier?.let { BezierHandles(Vector3f(it.left), Vector3f(it.right)) }
        )
        return keyframes + appended
    }

    private fun appendLoopRotationKeyframe(
        keyframes: List<RotationKeyframe>,
        animationLength: Float
    ): List<RotationKeyframe> {
        if (keyframes.isEmpty()) {
            return keyframes
        }
        val last = keyframes.last()
        if (abs(last.time - animationLength) <= MIN_SEGMENT_DURATION) {
            return keyframes
        }
        val first = keyframes.first()
        val appended = RotationKeyframe(
            time = animationLength,
            euler = Vector3f(first.euler),
            quaternion = Quaternionf(first.quaternion),
            interpolation = last.interpolation,
            handles = first.handles?.let { RotationBezierHandles(Vector3f(it.left), Vector3f(it.right)) }
        )
        return keyframes + appended
    }

    /**
     * HOLD/ONCE 모드에서 마지막 키프레임 이후에 동일한 값을 가진 가상 키프레임 추가
     * 마지막 키프레임이 반복되도록 하여 보간이 일어나지 않게 함
     */
    private fun appendHoldKeyframe(
        keyframes: List<AnimationKeyframe>,
        animationLength: Float
    ): List<AnimationKeyframe> {
        if (keyframes.isEmpty()) {
            return keyframes
        }
        val last = keyframes.last()
        // 마지막 키프레임 이후에 동일한 값을 가진 가상 키프레임 추가
        // 마지막 키프레임과 가상 키프레임이 같은 값이므로 보간 결과도 항상 같은 값이 됨
        val holdKeyframe = AnimationKeyframe(
            time = animationLength + 1.0f,
            value = Vector3f(last.value),
            interpolation = KeyframeInterpolation.STEP, // STEP 보간으로 값이 변하지 않도록
            bezier = last.bezier?.let { BezierHandles(Vector3f(it.left), Vector3f(it.right)) }
        )
        return keyframes + holdKeyframe
    }

    /**
     * HOLD/ONCE 모드에서 마지막 회전 키프레임 이후에 동일한 값을 가진 가상 키프레임 추가
     */
    private fun appendHoldRotationKeyframe(
        keyframes: List<RotationKeyframe>,
        animationLength: Float
    ): List<RotationKeyframe> {
        if (keyframes.isEmpty()) {
            return keyframes
        }
        val last = keyframes.last()
        // 마지막 키프레임 이후에 동일한 값을 가진 가상 키프레임 추가
        // 마지막 키프레임과 가상 키프레임이 같은 값이므로 보간 결과도 항상 같은 값이 됨
        val holdKeyframe = RotationKeyframe(
            time = animationLength + 1.0f,
            euler = Vector3f(last.euler),
            quaternion = Quaternionf(last.quaternion),
            interpolation = KeyframeInterpolation.STEP, // STEP 보간으로 값이 변하지 않도록
            handles = last.handles?.let { RotationBezierHandles(Vector3f(it.left), Vector3f(it.right)) }
        )
        return keyframes + holdKeyframe
    }

    private fun interpolateKeyframes(
        keyframes: List<AnimationKeyframe>,
        segmentIndex: Int,
        t: Float,
        isLooping: Boolean
    ): Vector3f {
        val prev = keyframes[segmentIndex]
        val next = keyframes[segmentIndex + 1]
        return when (prev.interpolation) {
            KeyframeInterpolation.LINEAR -> InterpolationUtil.lerp(prev.value, next.value, t)
            KeyframeInterpolation.STEP -> Vector3f(prev.value)
            KeyframeInterpolation.CATMULL_ROM -> {
                val p0 = when {
                    segmentIndex - 1 >= 0 -> keyframes[segmentIndex - 1].value
                    isLooping && keyframes.size > 2 -> keyframes[keyframes.size - 2].value
                    else -> prev.value
                }
                val p1 = prev.value
                val p2 = next.value
                val p3 = when {
                    segmentIndex + 2 < keyframes.size -> keyframes[segmentIndex + 2].value
                    isLooping && keyframes.size > 2 -> keyframes[1].value
                    else -> next.value
                }
                InterpolationUtil.catmullRom(p0, p1, p2, p3, t)
            }
            KeyframeInterpolation.BEZIER -> {
                val p0 = prev.value
                val p3 = next.value
                val p1 = prev.bezier?.right ?: p0
                val p2 = next.bezier?.left ?: if (isLooping && segmentIndex + 1 == keyframes.size - 1) {
                    keyframes.first().bezier?.left ?: p3
                } else {
                    p3
                }
                cubicBezier(p0, p1, p2, p3, t)
            }
        }
    }

    private fun interpolateRotationKeyframes(
        keyframes: List<RotationKeyframe>,
        segmentIndex: Int,
        t: Float,
        isLooping: Boolean
    ): Quaternionf {
        val prev = keyframes[segmentIndex]
        val next = keyframes[segmentIndex + 1]
        return when (prev.interpolation) {
            KeyframeInterpolation.STEP -> Quaternionf(prev.quaternion)
            KeyframeInterpolation.LINEAR -> {
                // Euler 각도로 보간하여 360도 경계 문제 해결
                val prevEuler = prev.euler
                val nextEuler = next.euler
                
                // 각 축별로 360도 경계 처리
                val interpolatedEuler = Vector3f(
                    lerpAngle(prevEuler.x, nextEuler.x, t),
                    lerpAngle(prevEuler.y, nextEuler.y, t),
                    lerpAngle(prevEuler.z, nextEuler.z, t)
                )
                
                quaternionFromDegrees(interpolatedEuler)
            }
            KeyframeInterpolation.CATMULL_ROM -> {
                val p0 = when {
                    segmentIndex - 1 >= 0 -> keyframes[segmentIndex - 1].euler
                    isLooping && keyframes.size > 2 -> keyframes[keyframes.size - 2].euler
                    else -> prev.euler
                }
                val p1 = prev.euler
                val p2 = next.euler
                val p3 = when {
                    segmentIndex + 2 < keyframes.size -> keyframes[segmentIndex + 2].euler
                    isLooping && keyframes.size > 2 -> keyframes[1].euler
                    else -> next.euler
                }
                val interpolated = InterpolationUtil.catmullRom(Vector3f(p0), Vector3f(p1), Vector3f(p2), Vector3f(p3), t)
                quaternionFromDegrees(interpolated)
            }
            KeyframeInterpolation.BEZIER -> {
                val handles = prev.handles
                val nextHandles = next.handles
                val p0 = prev.euler
                val p3 = next.euler
                val p1 = handles?.right ?: p0
                val p2 = nextHandles?.left ?: if (isLooping && segmentIndex + 1 == keyframes.size - 1) {
                    keyframes.first().handles?.left ?: p3
                } else {
                    p3
                }
                val interpolated = cubicBezier(Vector3f(p0), Vector3f(p1), Vector3f(p2), Vector3f(p3), t)
                quaternionFromDegrees(interpolated)
            }
        }
    }

    companion object {
        private const val DEFAULT_TICK_SECONDS = 0.05f
        private const val BLOCK_SCALE = 16f
        private const val MIN_SEGMENT_DURATION = 0.0001f
        private const val APPROX_EPSILON = 1e-6f
        private const val EVENT_TIME_EPSILON = 0.0001f
        // bone이 움직이지 않을 때 추가하는 미세한 변화의 속도 (라디안/초)
        // 시각적으로 보이지 않을 정도로 매우 작은 값
        private const val MICRO_ROTATION_SPEED = 0.000001f // 약 0.000057도/초
        
        // Identity Quaternion 상수화 (매번 생성하지 않도록)
        private val IDENTITY_QUATERNION = Quaternionf(0f, 0f, 0f, 1f)
        
        @JvmStatic
        fun getIdentityQuaternion(): Quaternionf = IDENTITY_QUATERNION
    }
}

