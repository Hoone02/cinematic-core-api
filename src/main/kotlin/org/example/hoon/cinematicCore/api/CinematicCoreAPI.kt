package org.example.hoon.cinematicCore.api

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.LivingEntity
import org.bukkit.scheduler.BukkitTask
import org.bukkit.entity.Player
import org.example.hoon.cinematicCore.api.animation.AnimationAPI
import org.example.hoon.cinematicCore.api.bone.BoneAPI
import org.example.hoon.cinematicCore.api.bone.BoneHierarchy
import org.example.hoon.cinematicCore.api.cutscene.CutsceneAPI
import org.example.hoon.cinematicCore.api.model.ModelAPI
import org.example.hoon.cinematicCore.api.model.ModelInfo
import org.example.hoon.cinematicCore.cutscene.Cutscene
import org.example.hoon.cinematicCore.model.domain.BlockbenchModel
import org.example.hoon.cinematicCore.model.domain.animation.BlockbenchAnimation

/**
 * CinematicCore의 모든 기능을 통합하는 메인 API 클래스
 * 
 * 이 클래스는 모델, 애니메이션, bone 관련 모든 기능을 제공합니다.
 * 
 * @example
 * ```kotlin
 * // 모델 적용
 * CinematicCoreAPI.Model.applyModel(entity, "player_battle", location)
 * 
 * // 애니메이션 재생
 * CinematicCoreAPI.Animation.playAnimationByName(entity, "walk")
 * 
 * // Bone 위치 가져오기
 * val location = CinematicCoreAPI.Bone.getBoneLocation(entity, "head")
 * ```
 */
object CinematicCoreAPI {
    
    /**
     * 모델 관련 API
     * 
     * 엔티티에 모델을 적용하고, 모델 정보를 조회하는 기능을 제공합니다.
     */
    object Model {
        /**
         * 엔티티에 모델을 적용합니다
         * 
         * @see ModelAPI.applyModel
         */
        fun applyModel(
            entity: LivingEntity,
            modelName: String,
            location: Location? = null,
            enableDamageColorChange: Boolean = true
        ): Boolean {
            return ModelAPI.applyModel(entity, modelName, location, enableDamageColorChange)
        }
        
        /**
         * 엔티티에 적용된 모델을 가져옵니다
         * 
         * @see ModelAPI.getModel
         */
        fun getModel(entity: Entity): BlockbenchModel? {
            return ModelAPI.getModel(entity)
        }
        
        /**
         * 특정 bone의 모델을 가져옵니다
         * 
         * @see ModelAPI.getBoneModel
         */
        fun getBoneModel(entity: Entity, boneName: String): BlockbenchModel? {
            return ModelAPI.getBoneModel(entity, boneName)
        }
        
        /**
         * 특정 bone의 baseEntity를 가져옵니다
         * 
         * @see ModelAPI.getBoneBaseEntity
         */
        fun getBoneBaseEntity(entity: Entity, boneName: String): LivingEntity? {
            return ModelAPI.getBoneBaseEntity(entity, boneName)
        }
        
        /**
         * 특정 모델이 적용된 모든 엔티티를 가져옵니다
         * 
         * @see ModelAPI.getEntitiesWithModel
         */
        fun getEntitiesWithModel(modelName: String, world: World? = null): List<Entity> {
            return ModelAPI.getEntitiesWithModel(modelName, world)
        }
        
        /**
         * 엔티티에 모델이 적용되어 있는지 확인합니다
         * 
         * @see ModelAPI.hasModel
         */
        fun hasModel(entity: Entity): Boolean {
            return ModelAPI.hasModel(entity)
        }
        
        /**
         * 엔티티에 적용된 모델 정보를 가져옵니다
         * 
         * @see ModelAPI.getModelInfo
         */
        fun getModelInfo(entity: Entity): ModelInfo? {
            return ModelAPI.getModelInfo(entity)
        }
        
        /**
         * 데미지 받을 때 빨간색으로 변하는 기능을 활성화/비활성화합니다
         * 
         * @see ModelAPI.setDamageColorChangeEnabled
         */
        fun setDamageColorChangeEnabled(entity: Entity, enable: Boolean): Boolean {
            return ModelAPI.setDamageColorChangeEnabled(entity, enable)
        }
        
        /**
         * 데미지 받을 때 빨간색으로 변하는 기능이 활성화되어 있는지 확인합니다
         * 
         * @see ModelAPI.isDamageColorChangeEnabled
         */
        fun isDamageColorChangeEnabled(entity: Entity): Boolean? {
            return ModelAPI.isDamageColorChangeEnabled(entity)
        }
    }
    
    /**
     * 애니메이션 관련 API
     * 
     * 애니메이션 재생, 정지, 일시정지 등의 기능을 제공합니다.
     */
    object Animation {
        /**
         * 애니메이션을 재생합니다 (BlockbenchAnimation 객체로)
         * 
         * @see AnimationAPI.playAnimation
         */
        fun playAnimation(
            entity: Entity,
            animation: BlockbenchAnimation,
            speed: Float = 1f,
            interruptible: Boolean = true
        ): Boolean {
            return AnimationAPI.playAnimation(entity, animation, speed, interruptible)
        }
        
        /**
         * 애니메이션을 재생합니다 (이름으로)
         * 
         * @see AnimationAPI.playAnimationByName
         */
        fun playAnimationByName(
            entity: Entity,
            animationName: String,
            speed: Float = 1f,
            interruptible: Boolean = true
        ): Boolean {
            return AnimationAPI.playAnimationByName(entity, animationName, speed, interruptible)
        }
        
        /**
         * 애니메이션을 정지합니다
         * 
         * @see AnimationAPI.stopAnimation
         */
        fun stopAnimation(entity: Entity, resetPose: Boolean = true): Boolean {
            return AnimationAPI.stopAnimation(entity, resetPose)
        }
        
        /**
         * 애니메이션을 일시정지합니다
         * 
         * @see AnimationAPI.pauseAnimation
         */
        fun pauseAnimation(entity: Entity): Boolean {
            return AnimationAPI.pauseAnimation(entity)
        }
        
        /**
         * 일시정지된 애니메이션을 재개합니다
         * 
         * @see AnimationAPI.resumeAnimation
         */
        fun resumeAnimation(entity: Entity): Boolean {
            return AnimationAPI.resumeAnimation(entity)
        }
        
        /**
         * 특정 모델에서 애니메이션을 찾습니다
         * 
         * @see AnimationAPI.findAnimation
         */
        fun findAnimation(model: BlockbenchModel, animationName: String): BlockbenchAnimation? {
            return AnimationAPI.findAnimation(model, animationName)
        }
        
        /**
         * 현재 재생 중인 애니메이션을 가져옵니다
         * 
         * @see AnimationAPI.getCurrentAnimation
         */
        fun getCurrentAnimation(entity: Entity): BlockbenchAnimation? {
            return AnimationAPI.getCurrentAnimation(entity)
        }
        
        /**
         * 애니메이션이 재생 중인지 확인합니다
         * 
         * @see AnimationAPI.isAnimationPlaying
         */
        fun isAnimationPlaying(entity: Entity): Boolean {
            return AnimationAPI.isAnimationPlaying(entity)
        }
    }
    
    /**
     * Bone 관련 API
     * 
     * Bone 위치, 태그, 계층 구조 등의 기능을 제공합니다.
     */
    object Bone {
        /**
         * 엔티티에 passenger를 커스텀 오프셋으로 탑승시킵니다 (PassengerPositionManager.mountWithOffset와 동일)
         * 
         * @param vehicle 탈것 엔티티
         * @param passenger 탑승할 엔티티
         * @param forward 전방 오프셋 (양수: 앞, 음수: 뒤) - vehicle 방향 기준
         * @param up 상하 오프셋 (양수: 위, 음수: 아래) - 절대 Y축
         * @param right 좌우 오프셋 (양수: 우측, 음수: 좌측) - vehicle 방향 기준
         * @param rotateWithVehicle vehicle 방향에 따라 오프셋도 회전할지 여부 (기본값: false, 절대 오프셋)
         * @param boneUuid bone의 UUID (지정하면 해당 bone의 pivot 위치를 탑승 위치로 사용)
         * @param canDismount 내릴 수 있는지 여부 (기본값: true, false로 설정하면 자동으로 내려가지 않음)
         * @return 위치 업데이트 태스크 (필요시 취소 가능)
         * 
         * @see BoneAPI.mountWithOffset
         */
        fun mountWithOffset(
            vehicle: Entity,
            passenger: Entity,
            forward: Double = 0.0,
            up: Double = 0.0,
            right: Double = 0.0,
            rotateWithVehicle: Boolean = false,
            boneUuid: String? = null,
            canDismount: Boolean = true
        ): BukkitTask? {
            return BoneAPI.mountWithOffset(vehicle, passenger, forward, up, right, rotateWithVehicle, boneUuid, canDismount)
        }
        
        /**
         * bone 이름으로 엔티티를 탑승시킵니다
         * 
         * @param vehicle 탈것 엔티티 (모델이 적용된 엔티티)
         * @param boneName 탑승시킬 bone의 이름
         * @param passenger 탑승할 엔티티
         * @param forward 전방 오프셋 (양수: 앞, 음수: 뒤) - vehicle 방향 기준
         * @param up 상하 오프셋 (양수: 위, 음수: 아래) - 절대 Y축
         * @param right 좌우 오프셋 (양수: 우측, 음수: 좌측) - vehicle 방향 기준
         * @param rotateWithVehicle vehicle 방향에 따라 오프셋도 회전할지 여부 (기본값: false, 절대 오프셋)
         * @param canDismount 내릴 수 있는지 여부 (기본값: true, false로 설정하면 자동으로 내려가지 않음)
         * @return 위치 업데이트 태스크 (필요시 취소 가능), 실패 시 null
         * 
         * @see BoneAPI.mountWithOffsetByBoneName
         */
        fun mountWithOffsetByBoneName(
            vehicle: Entity,
            boneName: String,
            passenger: Entity,
            forward: Double = 0.0,
            up: Double = 0.0,
            right: Double = 0.0,
            rotateWithVehicle: Boolean = false,
            canDismount: Boolean = true
        ): BukkitTask? {
            return BoneAPI.mountWithOffsetByBoneName(vehicle, boneName, passenger, forward, up, right, rotateWithVehicle, canDismount)
        }
        
        /**
         * 탑승한 엔티티를 강제로 내립니다
         * 
         * @see BoneAPI.dismountEntityFromBone
         */
        fun dismountEntityFromBone(entity: Entity, passenger: Entity): Boolean {
            return BoneAPI.dismountEntityFromBone(entity, passenger)
        }
        
        /**
         * 엔티티가 특정 bone에 탑승했는지 확인합니다
         * 
         * @see BoneAPI.isEntityMountedOnBone
         */
        fun isEntityMountedOnBone(entity: Entity, passenger: Entity): Boolean {
            return BoneAPI.isEntityMountedOnBone(entity, passenger)
        }
        
        /**
         * 탑승한 엔티티가 내릴 수 있는지 확인합니다
         * 
         * @see BoneAPI.canPassengerDismount
         */
        fun canPassengerDismount(entity: Entity, passenger: Entity): Boolean? {
            return BoneAPI.canPassengerDismount(entity, passenger)
        }
        
        /**
         * 특정 bone을 다른 모델의 bone으로 교체합니다
         * 
         * @see BoneAPI.replaceBone
         */
        fun replaceBone(
            entity: Entity,
            targetBoneName: String,
            replacementModelName: String,
            replacementBoneName: String = targetBoneName
        ): Boolean {
            return BoneAPI.replaceBone(entity, targetBoneName, replacementModelName, replacementBoneName)
        }
        
        /**
         * 특정 bone의 위치를 Location으로 가져옵니다
         * 
         * @see BoneAPI.getBoneLocation
         */
        fun getBoneLocation(entity: Entity, boneName: String): Location? {
            return BoneAPI.getBoneLocation(entity, boneName)
        }
        
        /**
         * bone의 태그를 가져옵니다
         * 
         * @see BoneAPI.getBoneTags
         */
        fun getBoneTags(entity: Entity, boneName: String): List<String> {
            return BoneAPI.getBoneTags(entity, boneName)
        }
        
        /**
         * bone의 계층 구조를 가져옵니다
         * 
         * @see BoneAPI.getBoneHierarchy
         */
        fun getBoneHierarchy(entity: Entity, boneName: String): BoneHierarchy? {
            return BoneAPI.getBoneHierarchy(entity, boneName)
        }
        
        /**
         * bone의 디스플레이 엔티티를 가져옵니다
         * 
         * @see BoneAPI.getBoneDisplay
         */
        fun getBoneDisplay(entity: Entity, boneName: String): ItemDisplay? {
            return BoneAPI.getBoneDisplay(entity, boneName)
        }
    }
    
    /**
     * 컷씬 관련 API
     * 
     * 컷씬 생성, 재생, 저장 등의 기능을 제공합니다.
     */
    object Cutscene {
        /**
         * 컷씬 재생
         * 
         * @param sceneName 컷씬 이름
         * @param player 플레이어
         * @return 재생 성공 여부
         */
        fun play(sceneName: String, player: Player): Boolean {
            return CutsceneAPI.play(sceneName, player)
        }
        
        /**
         * 컷씬 생성
         * 
         * @param sceneName 컷씬 이름
         * @param player 플레이어
         * @return 생성 성공 여부
         */
        fun create(sceneName: String, player: Player): Boolean {
            return CutsceneAPI.create(sceneName, player)
        }
        
        /**
         * 컷씬 로드
         * 
         * @param sceneName 컷씬 이름
         * @return 로드된 컷씬, 없으면 null
         */
        fun load(sceneName: String): org.example.hoon.cinematicCore.cutscene.Cutscene? {
            return CutsceneAPI.load(sceneName)
        }
        
        /**
         * 컷씬 저장
         * 
         * @param cutscene 저장할 컷씬
         * @return 저장 성공 여부
         */
        fun save(cutscene: org.example.hoon.cinematicCore.cutscene.Cutscene): Boolean {
            return CutsceneAPI.save(cutscene)
        }
        
        /**
         * 컷씬 삭제
         * 
         * @param sceneName 컷씬 이름
         * @return 삭제 성공 여부
         */
        fun delete(sceneName: String): Boolean {
            return CutsceneAPI.delete(sceneName)
        }
        
        /**
         * 컷씬 목록 반환
         * 
         * @return 컷씬 이름 리스트
         */
        fun list(): List<String> {
            return CutsceneAPI.list()
        }
        
        /**
         * 재생 중인 컷씬 강제 종료
         * 
         * @param player 플레이어
         * @param restoreOriginalLocation true면 재생 전 위치로 복원, false면 현재 위치에서 멈춤 (기본값: true)
         * @return 종료 성공 여부 (재생 중이었으면 true, 아니면 false)
         */
        fun stop(player: Player, restoreOriginalLocation: Boolean = true): Boolean {
            return CutsceneAPI.stop(player, restoreOriginalLocation)
        }
        
        /**
         * 플레이어가 컷씬을 재생 중인지 확인
         * 
         * @param player 플레이어
         * @return 재생 중이면 true
         */
        fun isPlaying(player: Player): Boolean {
            return CutsceneAPI.isPlaying(player)
        }
    }
}

