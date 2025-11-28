package org.example.hoon.cinematicCore.util.animation

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.Location
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.inventory.meta.components.CustomModelDataComponent
import org.example.hoon.cinematicCore.model.domain.BlockbenchModel
import org.example.hoon.cinematicCore.model.domain.Bone
import org.example.hoon.cinematicCore.model.domain.getBone
import org.example.hoon.cinematicCore.model.domain.getBoneByUuid
import org.example.hoon.cinematicCore.model.domain.getChildBones
import org.example.hoon.cinematicCore.model.domain.getParentBone
import org.example.hoon.cinematicCore.model.domain.getParentBoneChain
import org.example.hoon.cinematicCore.model.domain.getTags
import org.example.hoon.cinematicCore.util.display.updateTransformation
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.abs

/**
 * Forward Kinematics 알고리즘을 구현한 클래스
 * Bone 계층 구조를 따라 변환을 누적하여 적용
 */
class ForwardKinematics(
    private val model: BlockbenchModel,
    private val mapper: BoneDisplayMapper,
    private val transforms: MutableMap<String, BoneTransform>,
    private val initialTransforms: MutableMap<String, BoneTransform>,
    private val displayInitialScales: MutableMap<String, Vector3f>,
    private val displayInitialTranslations: MutableMap<String, Vector3f>,
    private var baseEntity: LivingEntity? = null
) {
    // bone 교체 정보: boneUuid -> (replacementModel, replacementBoneName)
    private val boneReplacements: MutableMap<String, Pair<BlockbenchModel, String>> = mutableMapOf()
    
    // 성능 최적화: 초기화 시 계산하여 매 틱마다 계산하지 않음
    private val rootBones: List<org.example.hoon.cinematicCore.model.domain.Bone>
    private val boneHasHTag: Map<String, Boolean> // boneUuid -> h 태그 여부
    private val isPlayerHeadBone: Boolean // player 모델의 head bone 여부
    private val boneNameLowercase: Map<String, String> // boneUuid -> lowercase bone name (문자열 연산 최적화)
    private val isHandleBone: Map<String, Boolean> // boneUuid -> handle bone 여부 (righthandle, lefthandle)
    
    init {
        // 루트 bone 찾기 (초기화 시 한 번만 계산)
        val allChildUuids = model.bones.flatMap { it.children }.toSet()
        rootBones = model.bones.filter { bone ->
            bone.parent == null && bone.uuid !in allChildUuids
        }
        
        // bone의 h 태그 여부 캐싱 (초기화 시 한 번만 계산)
        boneHasHTag = model.bones.associate { bone ->
            val isHeadBone = model.modelName.equals("player", ignoreCase = true) && 
                            bone.name.equals("head", ignoreCase = true)
            val hasHTag = bone.getTags().contains("h") || 
                         model.getParentBoneChain(bone).any { it.getTags().contains("h") } ||
                         isHeadBone
            bone.uuid to hasHTag
        }
        
        // player 모델의 head bone 여부 캐싱
        isPlayerHeadBone = model.modelName.equals("player", ignoreCase = true)
        
        // bone 이름 lowercase 캐싱 (문자열 연산 최적화)
        boneNameLowercase = model.bones.associate { bone ->
            bone.uuid to bone.name.lowercase()
        }
        
        // handle bone 여부 캐싱 (righthandle, lefthandle)
        isHandleBone = model.bones.associate { bone ->
            val nameLower = bone.name.lowercase()
            bone.uuid to (nameLower == "righthandle" || nameLower == "lefthandle")
        }
    }
    
    /**
     * bone 교체 정보 설정
     */
    fun setBoneReplacement(boneUuid: String, replacementModel: BlockbenchModel, replacementBoneName: String) {
        boneReplacements[boneUuid] = Pair(replacementModel, replacementBoneName)
    }
    
    /**
     * bone 교체 정보 가져오기
     */
    fun getBoneReplacement(boneUuid: String): Pair<BlockbenchModel, String>? {
        return boneReplacements[boneUuid]
    }
    
    /**
     * bone이 h 태그를 가지고 있는지 확인 (캐시된 결과 사용)
     */
    private fun boneHasHTag(boneUuid: String): Boolean {
        return boneHasHTag[boneUuid] ?: false
    }
    // 이전 위치를 저장하여 실제 이동 여부 확인 (Location.clone() 대신 좌표값만 저장하여 GC 부하 감소)
    private var previousX: Double = 0.0
    private var previousY: Double = 0.0
    private var previousZ: Double = 0.0
    private var hasPreviousLocation: Boolean = false
    // h 태그가 없는 bone들에 적용할 yaw 오프셋 (도 단위)
    private var nonHTagYawOffset: Float = 0f
    private var targetNonHTagYawOffset: Float = 0f
    private var hasMovement: Boolean = false // 이동 중인지 여부
    private var noMovementTicks: Int = 0 // 이동이 없었던 틱 수
    private val noMovementResetTicks: Int = 5 // 5틱 동안 이동이 없으면 targetNonHTagYawOffset을 0으로 리셋
    private val yawSmoothingFactor = 0.55f //yaw 회전 보간 정도
    private val yawDeadband = 0.35f //nonHTagYawOffset의 절대값이 이 값보다 작으면 0으로 강제 설정
    // 히스테리시스: 좌우 이동 감지 임계값 (끊김 방지)
    private val crossProductThresholdHigh: Float = 0.2f // 0 -> 45도로 전환하는 임계값
    private val crossProductThresholdLow: Float = 0.15f // 45도 -> 0으로 전환하는 임계값 (낮게 설정하여 끊김 방지)
    private var smoothedBaseYaw: Float? = null
    private val nonPlayerYawSmoothingFactor = 0.12f // 기본 보간 속도 (더 부드럽게)
    private val minSmoothingFactor = 0.08f // 최소 보간 속도
    private val maxSmoothingFactor = 0.20f // 최대 보간 속도
    // freecam 모델용: 플레이어 이동 방향 yaw (도 단위)
    private var freecamMovementYaw: Float? = null
    
    /**
     * baseEntity 설정 (h 태그 bone이 시야를 따라가도록)
     */
    fun setBaseEntity(entity: LivingEntity?) {
        baseEntity = entity
        hasPreviousLocation = false // baseEntity가 변경되면 이전 위치 초기화
        nonHTagYawOffset = 0f // yaw 오프셋 초기화
        targetNonHTagYawOffset = 0f
        hasMovement = false
        noMovementTicks = 0
        smoothedBaseYaw = null
        freecamMovementYaw = null // freecam 이동 방향 yaw 초기화
    }
    
    /**
     * 모든 Bone의 변환을 계산하고 Display 엔티티에 적용
     */
    fun update() {
        // 모델이 적용된 엔티티는 실제로 이동할 때만 bodyYaw = yaw가 적용되도록
        // (고개를 돌리는 것은 제외)
        if (baseEntity != null && baseEntity!!.isValid) {
            val currentLocation = baseEntity!!.location
            val currentX = currentLocation.x
            val currentY = currentLocation.y
            val currentZ = currentLocation.z
            
            if (hasPreviousLocation) {
                // distance 계산 대신 직접 비교하여 GC 부하 감소
                val dx = currentX - previousX
                val dy = currentY - previousY
                val dz = currentZ - previousZ
                val distanceSquared = dx * dx + dy * dy + dz * dz
                
                if (distanceSquared > 0.0001) { // 0.01^2 = 0.0001
                    hasMovement = true
                    noMovementTicks = 0
                    baseEntity!!.bodyYaw = baseEntity!!.yaw
                    
                    // freecam 모델이고 baseEntity가 Player인 경우 이동 방향 yaw 계산
                    if (model.isFreecam && baseEntity is Player) {
                        val moveLengthSquared = dx * dx + dz * dz
                        if (moveLengthSquared > 0.000225) { // 0.015^2 = 0.000225
                            val moveLength = Math.sqrt(moveLengthSquared)
                            // 이동 방향의 yaw 계산 (마인크래프트 좌표계: -dx, dz)
                            val movementYaw = Math.toDegrees(Math.atan2(-dx, dz)).toFloat()
                            freecamMovementYaw = normalizeYaw(movementYaw)
                        }
                        // 이동하지 않을 때는 이전 이동 방향 yaw 유지 (freecamMovementYaw가 null이 아니면 유지)
                    } else if (!model.isFreecam) {
                        // freecam 모델이 아닌 경우 초기화
                        freecamMovementYaw = null
                    }
                    
                    val bodyYawRad = Math.toRadians(baseEntity!!.bodyYaw.toDouble())
                    val forwardX = -Math.sin(bodyYawRad)
                    val forwardZ = Math.cos(bodyYawRad)
                    
                    val moveLengthSquared = dx * dx + dz * dz
                    if (moveLengthSquared > 0.000225) { // 0.015^2 = 0.000225
                        val moveLength = Math.sqrt(moveLengthSquared)
                        val moveX = dx / moveLength
                        val moveZ = dz / moveLength
                        
                        val dotProduct = forwardX * moveX + forwardZ * moveZ
                        val crossProduct = forwardX * moveZ - forwardZ * moveX
                        val isMovingForward = dotProduct > 0
                        
                        // 히스테리시스 적용: 현재 상태에 따라 다른 임계값 사용하여 끊김 방지
                        val currentTarget = targetNonHTagYawOffset
                        val isCurrentlyOffset = abs(currentTarget) > 20f // 현재 45도 또는 -45도인지
                        
                        // 우회전/좌회전 임계값 결정 (히스테리시스)
                        val rightTurnThreshold = if (isCurrentlyOffset && currentTarget > 0) {
                            crossProductThresholdLow  // 이미 우회전 중이면 더 낮은 임계값으로 유지 (끊김 방지)
                        } else {
                            crossProductThresholdHigh  // 우회전 시작: 높은 임계값 사용
                        }
                        
                        val leftTurnThreshold = if (isCurrentlyOffset && currentTarget < 0) {
                            crossProductThresholdLow  // 이미 좌회전 중이면 더 낮은 임계값으로 유지 (끊김 방지)
                        } else {
                            crossProductThresholdHigh  // 좌회전 시작: 높은 임계값 사용
                        }
                        
                        targetNonHTagYawOffset = when {
                            // 우회전: crossProduct가 양수이고 임계값을 넘으면
                            crossProduct > rightTurnThreshold -> if (isMovingForward) 45f else -45f
                            
                            // 좌회전: crossProduct가 음수이고 임계값을 넘으면
                            crossProduct < -leftTurnThreshold -> if (isMovingForward) -45f else 45f
                            
                            // 직진: crossProduct가 임계값 미만이면 0으로 설정
                            else -> 0f
                        }
                    } else {
                        // 좌우 이동이 거의 없으면 직진으로 간주
                        targetNonHTagYawOffset = 0f
                    }
                } else {
                    // 이동이 없음
                    hasMovement = false
                    noMovementTicks++
                    if (noMovementTicks >= noMovementResetTicks) {
                        // 일정 시간 동안 이동이 없으면 targetNonHTagYawOffset을 0으로 리셋
                        targetNonHTagYawOffset = 0f
                    }
                }
            } else {
                // 첫 틱이거나 이전 위치가 없음
                hasMovement = false
                noMovementTicks++
                targetNonHTagYawOffset = 0f
            }
            
            previousX = currentX
            previousY = currentY
            previousZ = currentZ
            hasPreviousLocation = true
        } else {
            // baseEntity가 없거나 유효하지 않음
            targetNonHTagYawOffset = 0f
        }
        
        // 좌우 회전 오프셋을 부드럽게 보간하여 떨림 방지
        nonHTagYawOffset += (targetNonHTagYawOffset - nonHTagYawOffset) * yawSmoothingFactor
        if (abs(nonHTagYawOffset) < yawDeadband) {
            nonHTagYawOffset = 0f
        }
        
        // 루트 Bone이 없으면 모든 Bone을 평면적으로 처리
        if (rootBones.isEmpty()) {
            // parent 정보가 없거나 모든 Bone이 독립적인 경우
            // children 배열을 통해 계층 구조 파악 시도 (캐싱된 결과 재사용)
            // bonesWithChildren은 동적으로 변경될 수 있으므로 매 틱마다 확인해야 함
            val bonesWithChildren = model.bones.filter { it.children.any { childUuid -> 
                model.getBoneByUuid(childUuid) != null 
            } }
            
            if (bonesWithChildren.isNotEmpty()) {
                // children이 있는 Bone부터 시작
                bonesWithChildren.forEach { bone ->
                    val childBones = model.getChildBones(bone)
                    if (childBones.isNotEmpty()) {
                        val transform = transforms[bone.uuid] ?: return@forEach
                        val rootTranslation = Vector3f(transform.translation)
                        val rootRotation = Quaternionf(transform.rotation)
                        val rootScale = Vector3f(transform.scale)
                        updateRecursive(bone, rootTranslation, rootRotation, rootScale, null)
                    }
                }
            } else {
                // 모든 Bone을 독립적으로 처리
                model.bones.forEach { bone ->
                    val transform = transforms[bone.uuid] ?: return@forEach
                    val hasHTag = boneHasHTag(bone.uuid) // 캐시된 결과 사용
                    applyTransformToDisplay(bone, transform, transform.translation, transform.rotation, transform.scale, hasHTag)
                }
            }
        } else {
            // 루트 Bone부터 시작하여 DFS로 순회 (캐시된 rootBones 사용)
            rootBones.forEach { rootBone ->
                val transform = transforms[rootBone.uuid] ?: return@forEach
                val rootTranslation = Vector3f(transform.translation)
                val rootRotation = Quaternionf(transform.rotation)
                val rootScale = Vector3f(transform.scale)
                updateRecursive(rootBone, rootTranslation, rootRotation, rootScale, null)
            }
        }
    }
    
    /**
     * 재귀적으로 Bone 계층 구조를 순회하며 변환을 적용
     * 
     * @param bone 현재 처리할 Bone
     * @param parentWorldTranslation 부모의 월드 pivot 위치 (부모의 월드 translation)
     * @param parentWorldRotation 부모의 월드 회전
     * @param parentWorldScale 부모의 월드 scale
     * @param parentBone 부모 Bone (null이면 루트)
     */
    private fun updateRecursive(
        bone: Bone,
        parentWorldTranslation: Vector3f,
        parentWorldRotation: Quaternionf,
        parentWorldScale: Vector3f,
        parentBone: Bone?
    ) {
        val transform = transforms[bone.uuid] ?: return
        
        var localTranslation = Vector3f(transform.translation)
        val localRotation = Quaternionf(transform.rotation)
        val localScale = Vector3f(transform.scale)

        var animationOffset = Vector3f(0f, 0f, 0f)

        if (parentBone != null) {
            val parentInitialTransform = initialTransforms[parentBone.uuid]
            val childInitialTransform = initialTransforms[bone.uuid]
            if (parentInitialTransform != null && childInitialTransform != null) {
                val baseOffset = Vector3f(childInitialTransform.translation)
                    .sub(parentInitialTransform.translation)

                animationOffset = Vector3f(transform.translation)
                    .sub(childInitialTransform.translation)

                localTranslation = baseOffset
            }
        }
        
        // Forward Kinematics: 부모의 변환을 자식에 적용
        // 부모의 pivot (origin) 기준으로 scale이 적용되어야 함
        // 부모의 월드 translation = 부모의 pivot 위치
        
        // 1. 자식의 로컬 위치를 부모의 회전된 좌표계로 변환
        //    부모가 회전하면 자식의 위치도 부모의 회전된 좌표계에서 변환됨
        val rotatedLocalTranslation = Vector3f(localTranslation)
        parentWorldRotation.transform(rotatedLocalTranslation)
        
        // 2. 부모의 scale을 자식의 로컬 위치에 적용
        //    부모가 커지면 자식의 pivot 위치도 부모의 pivot 기준으로 scale에 비례하여 멀어짐
        //    pivot → 자식 방향 벡터가 scale만큼 늘어남
        //    이렇게 하면 부모의 pivot을 기준으로 자식이 커짐
        rotatedLocalTranslation.mul(parentWorldScale)
        
        // 3. 월드 위치 = 부모 pivot 월드 위치 + 회전되고 스케일된 자식 로컬 위치
        //    부모의 pivot 기준으로 자식이 위치함
        //    부모가 이동/회전/스케일되면 자식도 함께 변환됨
        val rotatedAnimationOffset = Vector3f(animationOffset)
        parentWorldRotation.transform(rotatedAnimationOffset)
        rotatedAnimationOffset.mul(parentWorldScale)

        val worldTranslation = Vector3f(parentWorldTranslation)
            .add(rotatedLocalTranslation)
            .add(rotatedAnimationOffset)
        
        // 4. 월드 회전 = 부모 회전 * 자식 로컬 회전 (누적)
        //    부모가 회전하면 자식도 부모의 회전을 상속받고, 자식의 로컬 회전이 추가됨
        val worldRotation = Quaternionf(parentWorldRotation).mul(localRotation)
        
        // 5. 월드 scale = 부모 월드 scale * 자식 로컬 scale (누적)
        //    부모가 커지면 자식도 부모의 scale을 상속받고, 자식의 로컬 scale이 추가됨
        //    이렇게 하면 자식의 크기도 부모의 크기에 비례하여 변함
        val worldScale = Vector3f(parentWorldScale).mul(localScale)
        
        // h 태그 bone 여부 확인 (캐시된 결과 사용)
        val hasHTag = boneHasHTag(bone.uuid)
        
        // h 태그 bone인 경우 baseEntity의 yaw/pitch를 애니메이션 rotation에 합성
        // 애니메이션의 rotation은 유지하면서 baseEntity의 시선도 따라가도록 함
        val shouldFollowGaze = hasHTag && baseEntity != null && 
                               (bone.getTags().contains("h") || (isPlayerHeadBone && bone.name.equals("head", ignoreCase = true)))
        val finalRotation = if (shouldFollowGaze) {
            // 애니메이션에서 계산된 localRotation을 오일러 각도로 변환
            val animationEulerAngles = Vector3f()
            localRotation.getEulerAnglesXYZ(animationEulerAngles)
            
            // 애니메이션의 yaw/pitch/roll 추출
            val animationPitch = animationEulerAngles.x  // X축 (pitch)
            val animationYaw = animationEulerAngles.y      // Y축 (yaw)
            val animationRoll = animationEulerAngles.z    // Z축 (roll)
            
            // baseEntity의 yaw에서 bodyYaw를 뺀 상대적 회전값 (고개 돌리기)
            val relativeYaw = baseEntity!!.yaw - baseEntity!!.bodyYaw
            val baseEntityYawRad = Math.toRadians(relativeYaw.toDouble()).toFloat()
            
            // baseEntity의 pitch
            val baseEntityPitchRad = Math.toRadians(baseEntity!!.pitch.toDouble()).toFloat()
            
            // 애니메이션 rotation과 baseEntity 시선을 합성
            // 애니메이션의 yaw/pitch에 baseEntity의 yaw/pitch를 추가로 적용
            val combinedPitch = animationPitch + baseEntityPitchRad
            val combinedYaw = animationYaw + (-baseEntityYawRad)  // 방향 반대를 위해 음수
            
            // 합성된 오일러 각도를 쿼터니언으로 변환
            // Z (roll) -> Y (yaw) -> X (pitch) 순서로 적용
            val combinedLocalRotation = Quaternionf()
                .rotateZ(animationRoll)      // 애니메이션 roll 유지
                .rotateY(combinedYaw)      // 애니메이션 yaw + baseEntity yaw
                .rotateX(combinedPitch)     // 애니메이션 pitch + baseEntity pitch
            
            // 부모의 회전 * 합성된 로컬 회전 (FK 유지)
            Quaternionf(parentWorldRotation).mul(combinedLocalRotation)
        } else {
            // h 태그가 없거나 baseEntity가 없는 경우 기존 FK rotation 유지
            worldRotation
        }
        
        // Display 엔티티에 변환 적용 (finalRotation 사용)
        applyTransformToDisplay(bone, transform, worldTranslation, finalRotation, worldScale, hasHTag)
        
        // 자식 Bone들에 대해 재귀적으로 반복
        // 자식들에게는 finalRotation을 부모의 worldRotation으로 전달하여 FK가 올바르게 적용되도록 함
        val childBones = model.getChildBones(bone)
        childBones.forEach { childBone ->
            updateRecursive(childBone, worldTranslation, finalRotation, worldScale, bone)
        }
    }
    
    /**
     * 월드 변환을 Display 엔티티에 적용
     */
    private fun applyTransformToDisplay(
        bone: Bone,
        transform: BoneTransform,
        worldTranslation: Vector3f,
        worldRotation: Quaternionf,
        worldScale: Vector3f,
        hasHTag: Boolean
    ) {
        val display = mapper.getDisplay(bone) ?: return
        
        // handle bone 처리 (righthandle, lefthandle) - 캐시된 결과 사용
        val isHandle = isHandleBone[bone.uuid] ?: false
        
        if (isHandle) {
            // Display 엔티티의 초기 scale과 translation 가져오기
            val initialScale = displayInitialScales[bone.uuid] ?: Vector3f(1f, 1f, 1f)
            val initialTranslation = displayInitialTranslations[bone.uuid] ?: Vector3f(0f, 0f, 0f)
            
            // 최종 scale = FK에서 계산한 worldScale * Display 엔티티의 초기 scale
            val finalScale = Vector3f(worldScale).mul(initialScale)
            
            // 최종 translation = FK에서 계산한 worldTranslation + Display 엔티티의 초기 translation
            val finalTranslation = Vector3f(worldTranslation).add(initialTranslation)
            
            // worldRotation은 이미 updateRecursive에서 h 태그가 적용된 finalRotation으로 전달됨
            val finalRotation = worldRotation
            
            // 모든 bone에 yaw 오프셋이 있을 경우 Display 엔티티의 setRotation으로 yaw 회전
            if (baseEntity != null) {
                val newYaw = if (model.isFreecam && baseEntity is Player && freecamMovementYaw != null) {
                    // freecam 모델인 경우 플레이어 이동 방향으로 yaw 설정
                    computeDisplayYaw(freecamMovementYaw!!)
                } else {
                    // baseEntity의 bodyYaw를 기준으로 yaw 오프셋 적용 (상대값)
                    val baseYaw = baseEntity!!.bodyYaw
                    computeDisplayYaw(baseYaw + nonHTagYawOffset)
                }
                
                // Display 엔티티의 setRotation으로 yaw 설정
                display.setRotation(newYaw, 0.0f)
            }
            
            // bone 교체 정보 확인 (교체된 bone인 경우 교체된 모델의 ItemStack 사용)
            val replacement = getBoneReplacement(bone.uuid)
            var itemScaleMultiplier = 1.0f
            var itemSet = false
            
            if (replacement != null) {
                // bone 교체가 적용된 경우 교체된 모델의 bone ItemStack 사용
                val (replacementModel, replacementBoneName) = replacement
                // 문자열 연산 최적화: replacementBoneName이 이미 lowercase인 경우도 있지만, 
                // getBone은 내부적으로 처리하므로 그대로 전달
                val replacementBone = replacementModel.getBone(replacementBoneName.lowercase())
                
                if (replacementBone != null) {
                    val material = if (replacementModel.modelName.equals("player", ignoreCase = true)) {
                        Material.PLAYER_HEAD
                    } else {
                        Material.LEATHER_HORSE_ARMOR
                    }
                    
                    val item = ItemStack(material)
                    val meta = item.itemMeta
                    if (meta != null) {
                        val component: CustomModelDataComponent = meta.customModelDataComponent
                        component.strings = listOf("${replacementModel.modelName}:${replacementBone.name}")
                        meta.setCustomModelDataComponent(component)
                        
                        // Player_Head인 경우 추가 설정
                        if (material == Material.PLAYER_HEAD && meta is SkullMeta) {
                            // ItemDisplayTransform 설정
                            display.itemDisplayTransform = ItemDisplay.ItemDisplayTransform.THIRDPERSON_RIGHTHAND
                            
                            // itemModel 설정 (bone 이름 사용)
                            meta.itemModel = NamespacedKey.minecraft("blueprint/player_display/${replacementBone.name.lowercase()}")
                        }
                        
                        item.itemMeta = meta
                        display.setItemStack(item)
                        itemSet = true
                    }
                }
            }
            
            // bone 교체가 적용되지 않았거나 플레이어가 아이템을 들고 있는 경우
            if (!itemSet && baseEntity is Player) {
                val player = baseEntity as Player
                
                // bone 이름에 따라 올바른 손의 아이템 가져오기 (캐시된 결과 사용)
                val boneNameLower = boneNameLowercase[bone.uuid] ?: bone.name.lowercase()
                val heldItem = when (boneNameLower) {
                    "righthandle" -> player.inventory.itemInMainHand
                    "lefthandle" -> player.inventory.itemInOffHand
                    else -> null
                }
                
                // 아이템이 있고 AIR가 아닌 경우에만 표시
                // clone() 대신 직접 ItemStack을 재사용하여 GC 부하 감소
                if (heldItem != null && heldItem.type != Material.AIR) {
                    // ItemStack이 변경되었을 때만 새로 설정 (불필요한 clone() 방지)
                    val currentItemStack = display.itemStack
                    if (currentItemStack == null || 
                        currentItemStack.type != heldItem.type || 
                        currentItemStack.amount != heldItem.amount ||
                        currentItemStack.hasItemMeta() != heldItem.hasItemMeta()) {
                        // 변경이 있을 때만 clone (실제로는 Bukkit API가 내부적으로 처리하므로 필요 없을 수도 있음)
                        // 하지만 setItemStack은 내부적으로 clone을 하므로 그냥 직접 전달
                        display.setItemStack(heldItem)
                    }
                    itemSet = true
                    
                    // 블록인 경우 scale을 0.2배로 줄임
                    if (heldItem.type.isBlock) {
                        itemScaleMultiplier = 0.2f
                    }
                }
            }
            
            // 아이템이 설정되지 않은 경우 AIR로 설정
            if (!itemSet) {
                display.setItemStack(ItemStack(Material.AIR))
            }
            
            // Display 엔티티의 Transformation 업데이트
            display.updateTransformation {
                // Translation 적용 (초기 translation 유지)
                translation {
                    x = finalTranslation.x
                    y = finalTranslation.y
                    z = finalTranslation.z
                }
                
                // Rotation 적용 (leftRotation 사용)
                leftRotation {
                    x = finalRotation.x.toDouble()
                    y = finalRotation.y.toDouble()
                    z = finalRotation.z.toDouble()
                    w = finalRotation.w.toDouble()
                }
                
                // Scale 적용 (블록인 경우 0.2배로 줄임)
                scale {
                    x = finalScale.x * itemScaleMultiplier
                    y = finalScale.y * itemScaleMultiplier
                    z = finalScale.z * itemScaleMultiplier
                }
            }
            
            return // handle bone 처리는 여기서 종료
        }
        
        // 일반 bone 처리 (기존 로직)
        // Display 엔티티의 초기 scale과 translation 가져오기
        val initialScale = displayInitialScales[bone.uuid] ?: Vector3f(1f, 1f, 1f)
        val initialTranslation = displayInitialTranslations[bone.uuid] ?: Vector3f(0f, 0f, 0f)
        
        // 최종 scale = FK에서 계산한 worldScale * Display 엔티티의 초기 scale
        // 이렇게 하면 모델의 기본 크기 조정과 FK 애니메이션 scale이 분리됨
        val finalScale = Vector3f(worldScale).mul(initialScale)
        
        // 최종 translation = FK에서 계산한 worldTranslation + Display 엔티티의 초기 translation
        // 이렇게 하면 모델의 기본 위치 조정과 FK 애니메이션 translation이 분리됨
        val finalTranslation = Vector3f(worldTranslation).add(initialTranslation)
        
        // worldRotation은 이미 updateRecursive에서 h 태그가 적용된 finalRotation으로 전달됨
        val finalRotation = worldRotation
        
        // bone 교체 정보 확인 및 ItemStack 업데이트
        val replacement = getBoneReplacement(bone.uuid)
        if (replacement != null) {
            val (replacementModel, replacementBoneName) = replacement
            val replacementBone = replacementModel.getBone(replacementBoneName.lowercase())
            
            if (replacementBone != null) {
                val material = if (replacementModel.modelName.equals("player", ignoreCase = true)) {
                    Material.PLAYER_HEAD
                } else {
                    Material.LEATHER_HORSE_ARMOR
                }
                
                val item = ItemStack(material)
                val meta = item.itemMeta
                if (meta != null) {
                    val component: CustomModelDataComponent = meta.customModelDataComponent
                    component.strings = listOf("${replacementModel.modelName}:${replacementBone.name}")
                    meta.setCustomModelDataComponent(component)
                    
                    // Player_Head인 경우 추가 설정
                    if (material == Material.PLAYER_HEAD && meta is SkullMeta) {
                        // ItemDisplayTransform 설정
                        display.itemDisplayTransform = ItemDisplay.ItemDisplayTransform.THIRDPERSON_RIGHTHAND
                        
                        // itemModel 설정 (bone 이름 사용)
                        meta.itemModel = NamespacedKey.minecraft("blueprint/player_display/${replacementBone.name.lowercase()}")
                    }
                    
                    item.itemMeta = meta
                    display.setItemStack(item)
                }
            }
        }
        
        // 모든 bone에 yaw 오프셋이 있을 경우 Display 엔티티의 setRotation으로 yaw 회전
        if (baseEntity != null) {
            val newYaw = if (model.isFreecam && baseEntity is Player && freecamMovementYaw != null) {
                // freecam 모델인 경우 플레이어 이동 방향으로 yaw 설정
                computeDisplayYaw(freecamMovementYaw!!)
            } else {
                // baseEntity의 bodyYaw를 기준으로 yaw 오프셋 적용 (상대값)
                val baseYaw = baseEntity!!.bodyYaw
                computeDisplayYaw(baseYaw + nonHTagYawOffset)
            }
            
            // Display 엔티티의 setRotation으로 yaw 설정
            display.setRotation(newYaw, 0.0f)
        }
        
        // Display 엔티티의 Transformation 업데이트
        display.updateTransformation {
            // Translation 적용 (초기 translation 유지)
            translation {
                x = finalTranslation.x
                y = finalTranslation.y
                z = finalTranslation.z
            }
            
            // Rotation 적용 (leftRotation 사용)
            // h 태그 bone인 경우 baseEntity yaw가 절대값으로 적용됨
            leftRotation {
                x = finalRotation.x.toDouble()
                y = finalRotation.y.toDouble()
                z = finalRotation.z.toDouble()
                w = finalRotation.w.toDouble()
            }
            
            // Scale 적용 (초기 scale 유지)
            scale {
                x = finalScale.x
                y = finalScale.y
                z = finalScale.z
            }
        }
    }
    
    /**
     * 월드 변환 행렬을 Display 엔티티에 적용 (기존 메서드 호환성 유지)
     */
    private fun applyTransformToDisplay(
        bone: Bone,
        transform: BoneTransform,
        worldMatrix: Matrix4f
    ) {
        val translation = Vector3f()
        val rotation = Quaternionf()
        val scale = Vector3f()
        worldMatrix.getTranslation(translation)
        worldMatrix.getNormalizedRotation(rotation)
        worldMatrix.getScale(scale)
        val hasHTag = boneHasHTag(bone.uuid) // 캐시된 결과 사용
        applyTransformToDisplay(bone, transform, translation, rotation, scale, hasHTag)
    }
    
    /**
     * 특정 Bone의 변환을 설정
     */
    fun setBoneTransform(boneUuid: String, transform: BoneTransform) {
        transforms[boneUuid] = transform
    }
    
    /**
     * 특정 Bone의 변환을 가져옴
     */
    fun getBoneTransform(boneUuid: String): BoneTransform? {
        return transforms[boneUuid]
    }

    private fun computeDisplayYaw(targetYaw: Float): Float {
        val normalizedTarget = normalizeYaw(targetYaw)

        if (baseEntity is Player) {
            smoothedBaseYaw = normalizedTarget
            return normalizedTarget
        }

        val currentYaw = smoothedBaseYaw ?: normalizedTarget
        val diff = wrapDegreeDiff(normalizedTarget - currentYaw)
        
        // 회전 각도 차이에 따라 동적으로 보간 속도 조정
        // 큰 각도 차이는 조금 더 빠르게, 작은 각도 차이는 더 천천히 회전
        val absDiff = abs(diff)
        val dynamicFactor = when {
            absDiff > 90f -> maxSmoothingFactor // 큰 회전은 조금 더 빠르게
            absDiff > 45f -> nonPlayerYawSmoothingFactor * 1.2f
            absDiff > 10f -> nonPlayerYawSmoothingFactor
            else -> minSmoothingFactor // 작은 회전은 더 천천히
        }.coerceIn(minSmoothingFactor, maxSmoothingFactor)
        
        val updatedYaw = currentYaw + diff * dynamicFactor
        val normalizedUpdated = normalizeYaw(updatedYaw)
        smoothedBaseYaw = normalizedUpdated
        return normalizedUpdated
    }

    private fun normalizeYaw(yaw: Float): Float {
        var result = yaw % 360f
        if (result > 180f) {
            result -= 360f
        } else if (result <= -180f) {
            result += 360f
        }
        return result
    }

    private fun wrapDegreeDiff(diff: Float): Float {
        var result = diff % 360f
        if (result > 180f) {
            result -= 360f
        } else if (result < -180f) {
            result += 360f
        }
        return result
    }
}

