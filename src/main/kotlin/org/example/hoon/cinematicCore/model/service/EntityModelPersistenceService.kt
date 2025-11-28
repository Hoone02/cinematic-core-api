package org.example.hoon.cinematicCore.model.service

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.example.hoon.cinematicCore.CinematicCore
import org.example.hoon.cinematicCore.model.service.ModelDisplaySpawner
import org.example.hoon.cinematicCore.util.ConsoleLogger
import org.example.hoon.cinematicCore.util.prefix
import java.io.File
import java.util.*

/**
 * 엔티티 모델 정보를 파일에 저장하고 로드하는 서비스
 */
class EntityModelPersistenceService(private val plugin: Plugin) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val dataFile: File = File(plugin.dataFolder, "entity_models.json")
    
    /**
     * 엔티티 모델 정보를 담는 데이터 클래스
     */
    data class EntityModelData(
        val modelId: String,  // UUID 대신 모델 ID 사용 (서버 재시작 후에도 유지)
        val modelName: String,
        val worldName: String,
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float,
        val pitch: Float,
        val entityType: String? = null  // 엔티티 타입 (찾기 용이하게)
    )
    
    /**
     * 서버 종료 시 모든 모델 적용 엔티티 정보를 저장하고 모델 제거
     */
    fun saveAndRemoveAllModels(): Int {
        val entityUuids = EntityModelUtil.getAllEntityUuids()
        if (entityUuids.isEmpty()) {
            // 저장할 엔티티가 없으면 파일 삭제
            if (dataFile.exists()) {
                dataFile.delete()
            }
            return 0
        }
        
        val entityModelDataList = mutableListOf<EntityModelData>()
        
        entityUuids.forEach { uuid ->
            val entity = Bukkit.getEntity(uuid) as? LivingEntity ?: return@forEach
            
            // 모델 이름 가져오기
            val modelName = EntityModelUtil.getModelName(entity) ?: return@forEach
            
            // 모델 ID 가져오기 (없으면 생성)
            val modelId = EntityModelUtil.getModelId(entity) ?: UUID.randomUUID().toString().also {
                // 모델 ID가 없으면 생성하여 저장
                entity.persistentDataContainer.set(
                    org.bukkit.NamespacedKey(CinematicCore.instance, "model_id"),
                    org.bukkit.persistence.PersistentDataType.STRING,
                    it
                )
            }
            
            // 위치 정보 저장
            val location = entity.location
            entityModelDataList.add(
                EntityModelData(
                    modelId = modelId,
                    modelName = modelName,
                    worldName = location.world?.name ?: "world",
                    x = location.x,
                    y = location.y,
                    z = location.z,
                    yaw = location.yaw,
                    pitch = location.pitch,
                    entityType = entity.type.name
                )
            )
            
            // 모델 제거 (AABB 포함)
            ModelDisplaySpawner.removeFromEntity(entity, removeBaseEntity = false)
        }
        
        // JSON 파일로 저장
        try {
            plugin.dataFolder.mkdirs()
            dataFile.writeText(gson.toJson(entityModelDataList))
            ConsoleLogger.success("${prefix} ${entityModelDataList.size}개 엔티티 모델 정보 저장 완료")
        } catch (e: Exception) {
            ConsoleLogger.error("${prefix} 엔티티 모델 정보 저장 실패: ${e.message}")
            e.printStackTrace()
        }
        
        return entityModelDataList.size
    }
    
    /**
     * 서버 시작 시 저장된 엔티티 모델 정보를 로드하고 모델 재적용
     */
    fun loadAndRestoreModels(): Int {
        if (!dataFile.exists()) {
            return 0
        }
        
        val entityModelDataList: List<EntityModelData> = try {
            val json = dataFile.readText()
            val type = object : TypeToken<List<EntityModelData>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            ConsoleLogger.error("${prefix} 엔티티 모델 정보 로드 실패: ${e.message}")
            e.printStackTrace()
            return 0
        }
        
        if (entityModelDataList.isEmpty()) {
            return 0
        }
        
        var restoredCount = 0
        val maxRetries = 5  // 최대 5번 재시도
        
        // 재시도 로직을 포함한 엔티티 찾기 및 모델 적용 함수
        fun tryRestoreModels(dataList: List<EntityModelData>, attempt: Int) {
            var currentRestoredCount = 0
            val remainingData = mutableListOf<EntityModelData>()
            
            dataList.forEach { data ->
                try {
                    // 모델 ID로 엔티티 찾기
                    var entity = EntityModelUtil.findEntityByModelId(data.modelId) as? LivingEntity
                    
                    // 모델 ID로 찾지 못했으면 위치 기반으로 찾기
                    if (entity == null || !entity.isValid) {
                        val world = Bukkit.getWorld(data.worldName)
                        if (world == null) {
                            remainingData.add(data)
                            return@forEach
                        }
                        
                        // 청크가 로드되어 있는지 확인하고 필요시 로드
                        val chunkX = data.x.toInt() shr 4
                        val chunkZ = data.z.toInt() shr 4
                        if (!world.isChunkLoaded(chunkX, chunkZ)) {
                            world.loadChunk(chunkX, chunkZ, true)
                        }
                        
                        val savedLocation = Location(world, data.x, data.y, data.z, data.yaw, data.pitch)
                        
                        // 저장된 위치 근처의 엔티티 중에서 모델 ID가 없는 엔티티 찾기 (검색 범위 확대)
                        val nearbyEntities = world.getNearbyEntities(savedLocation, 10.0, 10.0, 10.0)
                            .filterIsInstance<LivingEntity>()
                            .filter { 
                                // 같은 타입이고 모델 ID가 없는 엔티티
                                (data.entityType == null || it.type.name == data.entityType) &&
                                !EntityModelUtil.hasModelId(it) &&
                                !EntityModelUtil.hasModel(it)  // 이미 모델이 적용된 엔티티 제외
                            }
                        
                        // 가장 가까운 엔티티 선택
                        entity = nearbyEntities.minByOrNull { it.location.distance(savedLocation) } as? LivingEntity
                        
                        if (entity != null) {
                            // 찾은 엔티티에 모델 ID 부여
                            entity.persistentDataContainer.set(
                                org.bukkit.NamespacedKey(CinematicCore.instance, "model_id"),
                                org.bukkit.persistence.PersistentDataType.STRING,
                                data.modelId
                            )
                            ConsoleLogger.info("${prefix} 위치 기반으로 엔티티 찾음 및 모델 ID 부여: ${data.modelId}")
                        }
                    }
                    
                    if (entity == null || !entity.isValid) {
                        remainingData.add(data)
                        return@forEach
                    }
                    
                    // 위치 확인 (엔티티가 저장된 위치와 비슷한 곳에 있는지)
                    val entityLocation = entity.location
                    val savedLocation = Location(
                        Bukkit.getWorld(data.worldName),
                        data.x,
                        data.y,
                        data.z,
                        data.yaw,
                        data.pitch
                    )
                    
                    // 거리가 너무 멀면 경고 (엔티티가 이동했을 수 있음)
                    if (entityLocation.distance(savedLocation) > 10.0) {
                        ConsoleLogger.warn("${prefix} 엔티티 위치가 저장된 위치와 다름: ${data.modelId} (거리: ${entityLocation.distance(savedLocation)})")
                        // 위치가 다르더라도 모델은 적용
                    }
                    
                    // 모델 재적용
                    val spawnResult = ModelDisplaySpawner.spawnByName(
                        plugin = plugin,
                        modelName = data.modelName,
                        location = entityLocation,
                        player = null,
                        scaledBoneCache = CinematicCore.instance.modelService.scaledBoneCache,
                        baseEntity = entity
                    )
                    
                    if (spawnResult != null) {
                        currentRestoredCount++
                        restoredCount++
                    } else {
                        ConsoleLogger.warn("${prefix} 모델 재적용 실패: ${data.modelName} (모델 ID: ${data.modelId})")
                        remainingData.add(data)
                    }
                } catch (e: Exception) {
                    ConsoleLogger.error("${prefix} 엔티티 모델 재적용 중 오류 (${data.modelId}): ${e.message}")
                    e.printStackTrace()
                    remainingData.add(data)
                }
            }
            
            if (currentRestoredCount > 0) {
                ConsoleLogger.info("${prefix} ${currentRestoredCount}개 엔티티 모델 재적용 완료 (시도 ${attempt}/${maxRetries})")
            }
            
            // 재시도가 필요하고 남은 데이터가 있으면 재시도
            if (attempt < maxRetries && remainingData.isNotEmpty()) {
                val delay = (attempt * 20L).coerceAtLeast(20L)  // 최소 1초, 최대 5초
                Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                    // 남은 데이터만 다시 시도
                    tryRestoreModels(remainingData, attempt + 1)
                }, delay)
            } else if (remainingData.isNotEmpty()) {
                ConsoleLogger.warn("${prefix} ${remainingData.size}개 엔티티 모델 재적용 실패 (최대 재시도 횟수 도달)")
                remainingData.forEach { data ->
                    ConsoleLogger.warn("${prefix} - 모델 ID: ${data.modelId}, 위치: ${data.worldName}, ${data.x}, ${data.y}, ${data.z}")
                }
            }
        }
        
        // 처음 시도는 3초 후 실행 (서버가 완전히 시작되고 엔티티가 로드될 때까지 대기)
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            tryRestoreModels(entityModelDataList, 1)
            
            if (restoredCount > 0) {
                ConsoleLogger.success("${prefix} 총 ${restoredCount}개 엔티티 모델 재적용 완료")
            }
        }, 60L) // 3초 후 실행
        
        return entityModelDataList.size
    }
}

