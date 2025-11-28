package org.example.hoon.cinematicCore.api.model

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.plugin.Plugin
import org.example.hoon.cinematicCore.CinematicCore
import org.example.hoon.cinematicCore.model.domain.BlockbenchModel
import org.example.hoon.cinematicCore.model.domain.getBone
import org.example.hoon.cinematicCore.model.loader.ModelFileLoader
import org.example.hoon.cinematicCore.model.service.EntityModelUtil
import org.example.hoon.cinematicCore.model.service.ModelDisplaySpawner
import org.example.hoon.cinematicCore.model.service.ModelService
import java.io.File
import java.util.UUID

/**
 * 모델 관련 기능을 제공하는 API 클래스
 */
object ModelAPI {
    private val plugin: Plugin = CinematicCore.instance
    
    /**
     * 엔티티에 모델을 적용합니다
     * 
     * @param entity 모델을 적용할 엔티티 (LivingEntity)
     * @param modelName 적용할 모델 이름 (파일명, 확장자 포함 또는 제외 가능)
     * @param location 모델을 소환할 위치 (null이면 엔티티의 현재 위치 사용)
     * @param enableDamageColorChange 데미지 받을 때 빨간색으로 변하는 기능 활성화 여부 (기본값: true)
     * @return 모델 적용 성공 여부
     * 
     * @example
     * ```kotlin
     * val entity = // LivingEntity
     * val location = entity.location
     * // 기본값 (빨간색 효과 활성화)
     * ModelAPI.applyModel(entity, "player_battle", location)
     * 
     * // 빨간색 효과 비활성화
     * ModelAPI.applyModel(entity, "player_battle", location, enableDamageColorChange = false)
     * ```
     */
    fun applyModel(
        entity: LivingEntity,
        modelName: String,
        location: Location? = null,
        enableDamageColorChange: Boolean = true
    ): Boolean {
        if (!entity.isValid) return false
        
        val spawnLocation = location ?: entity.location
        val scaledBoneCache = getScaledBoneCache()
        
        val spawnResult = ModelDisplaySpawner.spawnByName(
            plugin = plugin,
            modelName = modelName,
            location = spawnLocation,
            player = null,
            baseEntity = entity,
            modelsDirectory = null,
            scaledBoneCache = scaledBoneCache,
            enableDamageColorChange = enableDamageColorChange
        )
        
        return spawnResult != null
    }
    
    /**
     * 엔티티에 적용된 모델을 가져옵니다
     * 
     * @param entity 모델 정보를 가져올 엔티티
     * @return 적용된 BlockbenchModel, 없으면 null
     * 
     * @example
     * ```kotlin
     * val entity = // 엔티티
     * val model = ModelAPI.getModel(entity)
     * model?.let {
     *     println("모델 이름: ${it.modelName}")
     *     println("Bone 개수: ${it.bones.size}")
     * }
     * ```
     */
    fun getModel(entity: Entity): BlockbenchModel? {
        if (!entity.isValid) return null
        
        return EntityModelUtil.getModel(entity)
    }
    
    /**
     * 특정 bone의 모델을 가져옵니다
     * bone이 교체된 경우 교체된 모델을 반환하고, 교체되지 않은 경우 기본 모델을 반환합니다
     * 
     * @param entity 모델이 적용된 엔티티
     * @param boneName 모델을 가져올 bone의 이름
     * @return bone에 적용된 모델, 없으면 null
     * 
     * @example
     * ```kotlin
     * val entity = // 모델이 적용된 엔티티
     * val boneModel = ModelAPI.getBoneModel(entity, "head")
     * boneModel?.let {
     *     println("Bone 모델 이름: ${it.modelName}")
     * }
     * ```
     */
    fun getBoneModel(entity: Entity, boneName: String): BlockbenchModel? {
        if (!entity.isValid) return null
        if (entity !is LivingEntity) return null
        
        val session = EntityModelUtil.getSession(entity) ?: return null
        val model = EntityModelUtil.getModel(entity) ?: return null
        
        val bone = model.getBone(boneName.lowercase()) ?: return null
        
        // bone 교체 정보 확인
        val replacement = session.getBoneReplacement(bone.uuid)
        if (replacement != null) {
            // 교체된 모델 반환
            return replacement.first
        }
        
        // 교체되지 않은 경우 기본 모델 반환
        return model
    }
    
    /**
     * 특정 bone의 baseEntity를 가져옵니다
     * 
     * @param entity 모델이 적용된 엔티티
     * @param boneName baseEntity를 가져올 bone의 이름
     * @return bone의 baseEntity (모델이 적용된 엔티티), 없으면 null
     * 
     * @example
     * ```kotlin
     * val entity = // 모델이 적용된 엔티티
     * val baseEntity = ModelAPI.getBoneBaseEntity(entity, "head")
     * baseEntity?.let {
     *     println("Base Entity: ${it.type}")
     * }
     * ```
     */
    fun getBoneBaseEntity(entity: Entity, boneName: String): LivingEntity? {
        if (!entity.isValid) return null
        if (entity !is LivingEntity) return null
        
        val session = EntityModelUtil.getSession(entity) ?: return null
        val model = EntityModelUtil.getModel(entity) ?: return null
        
        val bone = model.getBone(boneName.lowercase()) ?: return null
        
        // bone이 교체된 경우 교체된 모델의 baseEntity를 찾아야 하지만,
        // 현재 구조상 bone의 baseEntity는 항상 원본 엔티티의 baseEntity와 동일
        // 따라서 세션의 baseEntity를 반환
        return session.baseEntity
    }
    
    /**
     * 특정 모델이 적용된 모든 엔티티를 가져옵니다
     * 
     * @param modelName 찾을 모델 이름
     * @param world 특정 월드에서만 찾을 경우 지정 (null이면 모든 월드)
     * @return 해당 모델이 적용된 엔티티 리스트
     * 
     * @example
     * ```kotlin
     * val entities = ModelAPI.getEntitiesWithModel("player_battle")
     * println("player_battle 모델이 적용된 엔티티: ${entities.size}개")
     * ```
     */
    fun getEntitiesWithModel(modelName: String, world: World? = null): List<Entity> {
        val entityUuids = EntityModelUtil.getAllEntityUuids()
        val result = mutableListOf<Entity>()
        
        for (uuid in entityUuids) {
            // UUID로 엔티티 찾기
            val entity = findEntityByUuid(uuid, world) ?: continue
            
            // 모델 이름 확인
            val entityModelName = EntityModelUtil.getModelName(entity)
            if (entityModelName != null && entityModelName.equals(modelName, ignoreCase = true)) {
                result.add(entity)
            }
        }
        
        return result
    }
    
    /**
     * 엔티티에 모델이 적용되어 있는지 확인합니다
     * 
     * @param entity 확인할 엔티티
     * @return 모델이 적용되어 있으면 true, 아니면 false
     * 
     * @example
     * ```kotlin
     * val entity = // 엔티티
     * if (ModelAPI.hasModel(entity)) {
     *     println("모델이 적용되어 있습니다")
     * }
     * ```
     */
    fun hasModel(entity: Entity): Boolean {
        if (!entity.isValid) return false
        
        return EntityModelUtil.hasModel(entity)
    }
    
    /**
     * 엔티티에 적용된 모델 정보를 가져옵니다 (ModelInfo)
     * 
     * @param entity 모델 정보를 가져올 엔티티
     * @return ModelInfo (session, model), 없으면 null
     * 
     * @example
     * ```kotlin
     * val entity = // 엔티티
     * val modelInfo = ModelAPI.getModelInfo(entity)
     * modelInfo?.let {
     *     println("모델: ${it.model.modelName}")
     *     println("Base Entity: ${it.session.baseEntity.type}")
     * }
     * ```
     */
    fun getModelInfo(entity: Entity): ModelInfo? {
        if (!entity.isValid) return null
        
        return EntityModelUtil.getModelInfo(entity)?.let { info ->
            ModelInfo(info.session, info.model)
        }
    }
    
    /**
     * 데미지 받을 때 빨간색으로 변하는 기능을 활성화/비활성화합니다
     * 
     * @param entity 모델이 적용된 엔티티
     * @param enable 활성화 여부 (true: 빨간색으로 변함, false: 변하지 않음)
     * @return 설정 성공 여부
     * 
     * @example
     * ```kotlin
     * val entity = // 모델이 적용된 엔티티
     * // 데미지 색상 변경 비활성화
     * ModelAPI.setDamageColorChangeEnabled(entity, false)
     * 
     * // 데미지 색상 변경 활성화
     * ModelAPI.setDamageColorChangeEnabled(entity, true)
     * ```
     */
    fun setDamageColorChangeEnabled(entity: Entity, enable: Boolean): Boolean {
        if (!entity.isValid) return false
        if (entity !is LivingEntity) return false
        
        val session = EntityModelUtil.getSession(entity) ?: return false
        
        session.enableDamageColorChange = enable
        return true
    }
    
    /**
     * 데미지 받을 때 빨간색으로 변하는 기능이 활성화되어 있는지 확인합니다
     * 
     * @param entity 모델이 적용된 엔티티
     * @return 활성화 여부 (활성화: true, 비활성화: false, 모델이 없으면 null)
     * 
     * @example
     * ```kotlin
     * val entity = // 모델이 적용된 엔티티
     * val enabled = ModelAPI.isDamageColorChangeEnabled(entity)
     * enabled?.let {
     *     println("데미지 색상 변경: ${if (it) "활성화" else "비활성화"}")
     * }
     * ```
     */
    fun isDamageColorChangeEnabled(entity: Entity): Boolean? {
        if (!entity.isValid) return null
        if (entity !is LivingEntity) return null
        
        val session = EntityModelUtil.getSession(entity) ?: return null
        
        return session.enableDamageColorChange
    }
    
    /**
     * ScaledBoneCache를 가져옵니다 (내부 사용)
     */
    private fun getScaledBoneCache(): org.example.hoon.cinematicCore.model.store.ScaledBoneCache {
        // ModelService에서 가져오기
        return org.example.hoon.cinematicCore.CinematicCore.instance.modelService.scaledBoneCache
    }
    
    /**
     * UUID로 엔티티를 찾습니다
     */
    private fun findEntityByUuid(uuid: UUID, world: World?): Entity? {
        if (world != null) {
            // 특정 월드에서 찾기
            return world.getEntity(uuid)
        } else {
            // 모든 월드에서 찾기
            for (w in plugin.server.worlds) {
                val entity = w.getEntity(uuid)
                if (entity != null) {
                    return entity
                }
            }
        }
        return null
    }
}

