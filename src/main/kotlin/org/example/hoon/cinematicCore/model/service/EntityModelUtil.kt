package org.example.hoon.cinematicCore.model.service

import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Entity
import org.bukkit.persistence.PersistentDataType
import org.example.hoon.cinematicCore.CinematicCore
import org.example.hoon.cinematicCore.model.domain.BlockbenchModel
import org.example.hoon.cinematicCore.model.service.display.DisplaySession
import java.util.*

/**
 * 엔티티에 모델 정보를 저장하고 조회하는 유틸리티
 * - PersistentDataContainer: 모델 이름 저장 (서버 재시작 후에도 유지)
 * - 인메모리 레지스트리: DisplaySession과 BlockbenchModel 저장 (빠른 접근)
 */
object EntityModelUtil {
    private val MODEL_NAME_KEY = NamespacedKey(CinematicCore.instance, "model_name")
    private val MODEL_ID_KEY = NamespacedKey(CinematicCore.instance, "model_id")
    
    // 인메모리 레지스트리: 엔티티 UUID -> (DisplaySession, BlockbenchModel)
    private val entityToSession: MutableMap<UUID, DisplaySession> = mutableMapOf()
    private val entityToModel: MutableMap<UUID, BlockbenchModel> = mutableMapOf()

    /**
     * 엔티티에 모델 정보 등록
     * @param entity 모델이 적용된 엔티티
     * @param session DisplaySession
     * @param model BlockbenchModel
     */
    fun register(entity: Entity, session: DisplaySession, model: BlockbenchModel) {
        val uuid = entity.uniqueId
        entityToSession[uuid] = session
        entityToModel[uuid] = model
        // PersistentDataContainer에도 모델 이름 저장
        setModelName(entity, model.modelName)
        // 모델 ID가 없으면 생성하여 저장 (서버 재시작 후 엔티티 식별용)
        if (!hasModelId(entity)) {
            setModelId(entity, UUID.randomUUID().toString())
        }
    }

    /**
     * 엔티티에서 모델 정보 제거
     * @param entity 모델 정보를 제거할 엔티티
     */
    fun unregister(entity: Entity) {
        val uuid = entity.uniqueId
        entityToSession.remove(uuid)
        entityToModel.remove(uuid)
        removeModelName(entity)
        // 모델 ID는 유지 (서버 재시작 후에도 식별 가능하도록)
        // removeModelId(entity) // 필요시 주석 해제
    }

    /**
     * 엔티티에 모델 이름 저장 (PersistentDataContainer)
     * @param entity 모델이 적용된 엔티티
     * @param modelName 저장할 모델 이름
     */
    private fun setModelName(entity: Entity, modelName: String) {
        entity.persistentDataContainer.set(MODEL_NAME_KEY, PersistentDataType.STRING, modelName)
    }

    /**
     * 세션이 제거된 뒤에도 모델 이름을 유지해야 할 때 사용
     * (예: 플레이어 로그아웃 후 재접속 시 재적용)
     */
    fun rememberModelName(entity: Entity, modelName: String) {
        setModelName(entity, modelName)
    }

    /**
     * 엔티티에 적용된 모델 이름 제거 (PersistentDataContainer)
     * @param entity 모델 정보를 제거할 엔티티
     */
    private fun removeModelName(entity: Entity) {
        entity.persistentDataContainer.remove(MODEL_NAME_KEY)
    }
    
    /**
     * 엔티티에 모델 ID 저장 (PersistentDataContainer)
     * @param entity 모델이 적용된 엔티티
     * @param modelId 저장할 모델 ID
     */
    private fun setModelId(entity: Entity, modelId: String) {
        entity.persistentDataContainer.set(MODEL_ID_KEY, PersistentDataType.STRING, modelId)
    }
    
    /**
     * 엔티티에 저장된 모델 ID 가져오기
     * @param entity 모델 ID를 가져올 엔티티
     * @return 모델 ID, 없으면 null
     */
    fun getModelId(entity: Entity): String? {
        return entity.persistentDataContainer.get(MODEL_ID_KEY, PersistentDataType.STRING)
    }
    
    /**
     * 엔티티에 모델 ID가 있는지 확인
     * @param entity 확인할 엔티티
     * @return 모델 ID가 있으면 true, 없으면 false
     */
    fun hasModelId(entity: Entity): Boolean {
        return entity.persistentDataContainer.has(MODEL_ID_KEY, PersistentDataType.STRING)
    }
    
    /**
     * 엔티티에서 모델 ID 제거 (PersistentDataContainer)
     * @param entity 모델 ID를 제거할 엔티티
     */
    fun removeModelId(entity: Entity) {
        entity.persistentDataContainer.remove(MODEL_ID_KEY)
    }
    
    /**
     * 모델 ID로 엔티티 찾기
     * @param modelId 찾을 모델 ID
     * @return 해당 모델 ID를 가진 엔티티, 없으면 null
     */
    fun findEntityByModelId(modelId: String): Entity? {
        // 모든 월드의 모든 엔티티를 검색
        for (world in Bukkit.getWorlds()) {
            for (entity in world.entities) {
                if (getModelId(entity) == modelId) {
                    return entity
                }
            }
        }
        return null
    }

    /**
     * 엔티티에 모델이 적용되어 있는지 확인
     * @param entity 확인할 엔티티
     * @return 모델이 적용되어 있으면 true, 아니면 false
     */
    fun hasModel(entity: Entity): Boolean {
        return entityToSession.containsKey(entity.uniqueId) || 
               entity.persistentDataContainer.has(MODEL_NAME_KEY, PersistentDataType.STRING)
    }

    /**
     * 엔티티에 적용된 모델 이름 가져오기
     * @param entity 모델 정보를 가져올 엔티티
     * @return 모델 이름, 모델이 적용되어 있지 않으면 null
     */
    fun getModelName(entity: Entity): String? {
        // 먼저 인메모리에서 확인
        entityToModel[entity.uniqueId]?.let { return it.modelName }
        // 없으면 PersistentDataContainer에서 확인
        return entity.persistentDataContainer.get(MODEL_NAME_KEY, PersistentDataType.STRING)
    }

    /**
     * 엔티티에 적용된 BlockbenchModel 가져오기
     * @param entity 모델 정보를 가져올 엔티티
     * @return BlockbenchModel, 모델이 적용되어 있지 않거나 세션이 만료된 경우 null
     */
    fun getModel(entity: Entity): BlockbenchModel? {
        return entityToModel[entity.uniqueId]
    }

    /**
     * 엔티티에 적용된 DisplaySession 가져오기
     * @param entity 세션 정보를 가져올 엔티티
     * @return DisplaySession, 세션이 없거나 만료된 경우 null
     */
    fun getSession(entity: Entity): DisplaySession? {
        return entityToSession[entity.uniqueId]
    }

    /**
     * 엔티티의 모델 정보를 모두 가져오기
     * @param entity 모델 정보를 가져올 엔티티
     * @return ModelInfo (session, model), 없으면 null
     */
    fun getModelInfo(entity: Entity): ModelInfo? {
        val session = entityToSession[entity.uniqueId] ?: return null
        val model = entityToModel[entity.uniqueId] ?: return null
        return ModelInfo(session, model)
    }

    /**
     * 모든 등록된 엔티티 UUID 목록
     */
    fun getAllEntityUuids(): Set<UUID> {
        return entityToSession.keys.toSet()
    }

    /**
     * 모든 등록 정보 초기화
     */
    fun clear() {
        entityToSession.clear()
        entityToModel.clear()
    }

    /**
     * 모델 정보를 담는 데이터 클래스
     */
    data class ModelInfo(
        val session: DisplaySession,
        val model: BlockbenchModel
    )
}

