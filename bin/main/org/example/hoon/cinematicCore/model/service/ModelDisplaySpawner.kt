package org.example.hoon.cinematicCore.model.service

import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Silverfish
import org.bukkit.plugin.Plugin
import org.example.hoon.cinematicCore.model.domain.BlockbenchModel
import org.example.hoon.cinematicCore.model.domain.getChildElements
import org.example.hoon.cinematicCore.model.loader.ModelFileLoader
import org.example.hoon.cinematicCore.model.store.ScaledBoneCache
import org.example.hoon.cinematicCore.model.service.display.DisplayEntityFactory
import org.example.hoon.cinematicCore.model.service.display.DisplaySession
import org.example.hoon.cinematicCore.model.service.display.RotationSynchronizer
import org.example.hoon.cinematicCore.util.animation.AnimationController
import org.example.hoon.cinematicCore.util.animation.BoneDisplayMapper
import java.io.File

/**
 * 모델의 Bone들을 Display 엔티티로 소환하고 배치하는 클래스
 */
class ModelDisplaySpawner(
    private val plugin: Plugin,
    private val model: BlockbenchModel,
    private val scaledBoneCache: ScaledBoneCache,
    private val entityFactory: DisplayEntityFactory = DisplayEntityFactory(plugin, model, scaledBoneCache),
    private val rotationSynchronizer: RotationSynchronizer = RotationSynchronizer(plugin)
) {
    companion object {
        /**
         * 모델명(bbmodel 파일명)으로 모델을 소환
         * @param plugin 플러그인 인스턴스
         * @param modelName 모델 파일명 (확장자 포함 또는 제외 모두 가능)
         * @param location 소환 위치
         * @param player 플레이어 (hitbox 숨김 처리용, null 가능)
         * @param baseEntity Base 엔티티 (지정하지 않으면 Silverfish 자동 생성)
         * @param modelsDirectory 모델 디렉토리 (기본값: plugin.dataFolder/models/)
         * @return 소환 결과 (session, model), 모델을 찾지 못한 경우 null
         */
        fun spawnByName(
            plugin: Plugin,
            modelName: String,
            location: Location,
            player: Player? = null,
            baseEntity: LivingEntity? = null,
            modelsDirectory: File? = null,
            scaledBoneCache: ScaledBoneCache,
            enableDamageColorChange: Boolean = true
        ): SpawnResult? {
            val modelsDir = modelsDirectory ?: File(plugin.dataFolder, "models/")
            val modelLoader = ModelFileLoader(modelsDir)
            val model = modelLoader.loadModelByName(modelName) ?: return null

            val spawner = ModelDisplaySpawner(plugin, model, scaledBoneCache)
            return spawner.spawn(location, player, baseEntity, enableDamageColorChange)
        }

        /**
         * 엔티티에서 적용된 모델을 제거
         * @param entity 모델을 제거할 엔티티
         * @param removeBaseEntity baseEntity도 함께 제거할지 여부 (기본값: false)
         * @return 모델이 제거되었으면 true, 엔티티에 모델이 없었으면 false
         */
        fun removeFromEntity(
            entity: LivingEntity,
            removeBaseEntity: Boolean = false
        ): Boolean {
            val session = EntityModelUtil.getSession(entity) ?: return false
            session.stop(removeBaseEntity)
            return true
        }
    }

    private var session: DisplaySession? = null

    /**
     * 모델을 지정된 위치에 소환하고 배치
     * @param location 소환 위치
     * @param player 플레이어 (hitbox 숨김 처리용)
     * @param baseEntity Base 엔티티 (지정하지 않으면 Silverfish 자동 생성)
     * @param enableDamageColorChange 데미지 받을 때 빨간색으로 변하는 기능 활성화 여부 (기본값: true)
     * @return 소환 결과
     */
    fun spawn(location: Location, player: Player? = null, baseEntity: LivingEntity? = null, enableDamageColorChange: Boolean = true): SpawnResult {
        val base = baseEntity ?: createBaseEntity(location)
        val mapper = BoneDisplayMapper()
        val spawnedDisplays = mutableListOf<ItemDisplay>()

        model.bones.forEach { bone ->
            val childElements = model.getChildElements(bone)
            if (childElements.isEmpty()) {
                return@forEach
            }
            val display = entityFactory.create(bone, location, base, mapper, player)
            spawnedDisplays += display
        }

        val animationController = AnimationController(plugin, model, mapper)
        animationController.setBaseEntity(base) // h 태그 bone이 baseEntity의 시야를 따라가도록 설정
        
        // 애니메이션 컨트롤러 자동 시작
        animationController.start(1L)
        
        // 기본 애니메이션은 idle 우선 재생
        val defaultAnimation = model.animations.firstOrNull {
            it.name.equals("idle", ignoreCase = true)
        } ?: model.animations.firstOrNull()
        if (defaultAnimation != null) {
            animationController.playAnimation(defaultAnimation, 1f)
        }
        
        val newSession = DisplaySession(
            base,
            rotationSynchronizer,
            mapper,
            animationController,
            model,
            scaledBoneCache,
            enableDamageColorChange
        )
        spawnedDisplays.forEach { newSession.track(it) }
        newSession.initialize()
        session = newSession

        // 엔티티에 모델 정보 등록 (레지스트리 + PersistentDataContainer)
        EntityModelUtil.register(base, newSession, model)

        return SpawnResult(newSession, model)
    }

    /**
     * 소환된 엔티티들을 제거
     */
    fun remove() {
        session?.stop()
        session = null
    }

    private fun createBaseEntity(location: Location): LivingEntity =
        location.world.spawn(location, Silverfish::class.java).apply { setAI(false) }

    class SpawnResult(
        val session: DisplaySession,
        val model: BlockbenchModel
    ) {
        val baseEntity: Entity
            get() = session.baseEntity
        val mapper: BoneDisplayMapper
            get() = session.mapper
        val animationController: AnimationController
            get() = session.animationController
    }
}

