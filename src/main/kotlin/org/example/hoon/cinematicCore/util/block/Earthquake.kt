package org.example.hoon.cinematicCore.util.block

import DisplayPivotUtil.EulerDeg
import DisplayPivotUtil.setTransformLocal
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.block.Block
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import org.joml.Vector3f
import kotlin.math.*

/**
 * 블럭 디스플레이 지진 효과를 생성하는 클래스
 * 블럭을 베리어로 변경하고 블럭 디스플레이를 소환한 후, 지정된 시간 후 랜덤 순서로 복원
 */
class Earthquake(
    private val plugin: Plugin
) {
    // 활성화된 블럭 디스플레이 추적 (블럭 위치 -> 복원 정보)
    private val activeBlocks = mutableMapOf<String, BlockRestoreInfo>()
    
    // 복원 스케줄 추적 (블럭 위치 -> 복원 스케줄 태스크)
    private val restoreSchedules = mutableMapOf<String, BukkitTask>()
    
    /**
     * 블럭 위치를 키로 변환 (x,y,z만 사용)
     */
    private fun getBlockKey(block: Block): String {
        return "${block.x},${block.y},${block.z}"
    }
    
    
    /**
     * 블럭 디스플레이 지진 효과 실행
     * 
     * @param centerLocation 중심 위치 (원형, 전방 사각형, 원형 가장자리) 또는 null (커스텀 로케이션)
     * @param player 플레이어 (전방 사각형 감지 시 필요)
     * @param type 감지 유형
     * @param config 감지 설정 (유형에 따라 다른 설정 객체)
     * @param customLocations 커스텀 로케이션 리스트 (CUSTOM_LOCATIONS 유형 시 필요)
     * @param restoreTimeSeconds 복원 시간 (초)
     * @param scanDepth 스캔 깊이 (몇 칸 아래까지 감지할지, 기본값: 3)
     * @param scanAbove 위쪽으로 스캔할 높이 (몇 칸 위까지 감지할지, 기본값: 3)
     * @param rotationRange 회전 범위 (기본값: -10도 ~ 10도)
     * @param spawnOffsetY 스폰 오프셋 Y (기본값: 0.1)
     * @param animationTicks 회전 애니메이션 틱 수 (기본값: 2)
     * @param delayBetweenRestores 블럭 복원 사이의 틱 간격 (기본값: 2)
     * @param earthquakeType 지진 효과 타입 (기본값: UNIFORM)
     * @param sequential 순차적 처리 활성화 여부 (기본값: false)
     * @param startFromCenter 중심부터 시작 여부 (기본값: true)
     * @param totalSequentialTime 순차적 처리 전체 시간 (초, 기본값: 1.0)
     * @param randomSize 랜덤 사이즈 활성화 여부 (기본값: false, true일 때 각 BlockDisplay의 사이즈를 0.9~1.2 범위로 랜덤 변경)
     */
    fun execute(
        centerLocation: Location? = null,
        player: Player? = null,
        type: DetectionType,
        config: Any? = null,
        customLocations: List<Location>? = null,
        restoreTimeSeconds: Long,
        scanDepth: Int = 3,
        scanAbove: Int = 3,
        rotationRange: Float = 10f,
        spawnOffsetY: Double = 0.1,
        animationTicks: Long = 2L,
        delayBetweenRestores: Long = 2L,
        earthquakeType: EarthquakeType = EarthquakeType.UNIFORM,
        sequential: Boolean = false,
        startFromCenter: Boolean = true,
        totalSequentialTime: Float = 1.0f,
        randomSize: Boolean = false
    ) {
        val world = centerLocation?.world ?: player?.world ?: return
        val blocksToRestore = if (sequential) {
            // 순차적 처리인 경우 동기화된 리스트 사용
            java.util.Collections.synchronizedList(mutableListOf<BlockRestoreInfo>())
        } else {
            mutableListOf<BlockRestoreInfo>()
        }
        val random = java.util.Random()
        
        // 중심 위치 계산 (회전값 계산에 필요)
        val actualCenterLocation = when (type) {
            DetectionType.FORWARD_RECTANGLE -> {
                player?.location ?: return
            }
            else -> {
                centerLocation ?: return
            }
        }
        
        // 블럭 위치 리스트 생성
        val blockLocations = when (type) {
            DetectionType.CIRCULAR -> {
                val circularConfig = config as? CircularConfig ?: return
                BlockLocationGenerator.getCircularBlocks(centerLocation ?: return, circularConfig.radius)
            }
            DetectionType.FORWARD_RECTANGLE -> {
                val rectConfig = config as? ForwardRectangleConfig ?: return
                val p = player ?: return
                BlockLocationGenerator.getForwardRectangleBlocks(p, rectConfig.width, rectConfig.length)
            }
            DetectionType.CIRCULAR_EDGE -> {
                val edgeConfig = config as? CircularEdgeConfig ?: return
                BlockLocationGenerator.getCircularEdgeBlocks(centerLocation ?: return, edgeConfig.radius, edgeConfig.edgeRange)
            }
            DetectionType.CUSTOM_LOCATIONS -> {
                customLocations ?: return
            }
        }
        
        // 순차적 처리인 경우 블럭을 거리별로 그룹화
        val locationGroups = if (sequential) {
            blockLocations.groupBy { location ->
                val distance = location.distance(actualCenterLocation)
                // 거리를 정수로 반올림하여 그룹화
                distance.toInt()
            }.toList().sortedBy { (distance, _) ->
                if (startFromCenter) distance else -distance
            }
        } else {
            null
        }
        
        // 순차적 처리인 경우 그룹당 시간 계산 및 그룹 병합
        val finalGroups = if (sequential && locationGroups != null) {
            val groupCount = locationGroups.size
            val minTimePerGroup = 0.05f // 최소 그룹당 시간 (초) - 1틱 = 0.05초
            val timePerGroup = totalSequentialTime / groupCount
            
            if (timePerGroup < minTimePerGroup && groupCount > 1) {
                // 시간이 너무 짧으면 여러 그룹을 묶어서 처리
                val groupsPerBatch = kotlin.math.ceil(groupCount * minTimePerGroup / totalSequentialTime).toInt().coerceAtLeast(1)
                val mergedGroups = mutableListOf<List<Location>>()
                
                locationGroups.chunked(groupsPerBatch).forEach { batch ->
                    val merged = batch.flatMap { it.second }
                    mergedGroups.add(merged)
                }
                
                mergedGroups
            } else {
                // 그룹을 그대로 사용
                locationGroups.map { it.second }
            }
        } else {
            null
        }
        
        // 각 위치에서 가장 높은 블럭 찾기 및 처리
        val locationsToProcess = if (sequential && finalGroups != null) {
            finalGroups.flatMap { it }
        } else {
            blockLocations
        }
        
        locationsToProcess.forEachIndexed { index, location ->
            val startY = (location.blockY + scanAbove).coerceAtMost(world.maxHeight - 1)
            val endY = (location.blockY - scanDepth).coerceAtLeast(world.minHeight)
            
            // 예외 블럭(빛 블럭, 베리어)이 범위 내에 있는지 먼저 확인
            var hasExceptionBlock = false
            for (y in startY downTo endY) {
                val block = world.getBlockAt(location.blockX, y, location.blockZ)
                val blockType = block.type
                // 예외 블럭이 있으면 이 위치는 완전히 스킵
                if (blockType == Material.BARRIER || blockType == Material.LIGHT) {
                    hasExceptionBlock = true
                    break
                }
            }
            
            // 예외 블럭이 있으면 이 위치는 스킵
            if (hasExceptionBlock) {
                return@forEachIndexed
            }
            
            // 가장 높은 블럭 찾기
            var highestBlock: Block? = null
            for (y in startY downTo endY) {
                val block = world.getBlockAt(location.blockX, y, location.blockZ)
                val blockType = block.type
                
                // 공기가 아니고, 투과 불가능하며, 부서지기 쉬운 블럭이 아니면 가장 높은 블럭으로 설정
                if (blockType != Material.AIR 
                    && !blockType.isTransparent 
                    && !FragileBlockChecker.isFragileBlock(blockType)) {
                    highestBlock = block
                    break
                }
            }
            
            // 가장 높은 블럭 위에 부서지기 쉬운 블럭이 있는지 확인
            // (풀이나 꽃이 있으면 아래 블럭을 변경하지 않음)
            if (highestBlock != null) {
                val highestY = highestBlock.y
                var hasFragileBlockAbove = false
                
                // 가장 높은 블럭 위부터 시작 위치까지 확인
                if (highestY + 1 <= startY) {
                    for (y in (highestY + 1)..startY) {
                        val blockAbove = world.getBlockAt(location.blockX, y, location.blockZ)
                        if (FragileBlockChecker.isFragileBlock(blockAbove.type)) {
                            hasFragileBlockAbove = true
                            break
                        }
                    }
                }
                
                // 부서지기 쉬운 블럭이 위에 있으면 이 위치는 스킵
                if (hasFragileBlockAbove) {
                    return@forEachIndexed
                }
            }
            
            // 블럭 처리 (순차적이면 거리별 그룹에 따라 지연 시간 계산)
            if (highestBlock != null) {
                val blockKey = getBlockKey(highestBlock)
                val delayTicks = if (sequential && finalGroups != null) {
                    // 현재 블럭이 속한 그룹 찾기
                    val groupIndex = finalGroups.indexOfFirst { group ->
                        group.any { it.blockX == location.blockX && it.blockZ == location.blockZ }
                    }
                    if (groupIndex >= 0 && finalGroups.isNotEmpty()) {
                        // 그룹당 시간 계산
                        val timePerGroup = totalSequentialTime / finalGroups.size
                        (groupIndex * timePerGroup * 20f).toLong() // 초를 틱으로 변환
                    } else {
                        0L
                    }
                } else {
                    0L
                }
                
                // 순차적 처리인 경우 지연 시간 후에 처리
                if (sequential && delayTicks > 0) {
                    Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                        processBlock(
                            highestBlock,
                            blockKey,
                            actualCenterLocation,
                            type,
                            config,
                            player,
                            rotationRange,
                            random,
                            earthquakeType,
                            spawnOffsetY,
                            blocksToRestore,
                            randomSize
                        )
                    }, delayTicks)
                } else {
                    // 즉시 처리
                    processBlock(
                        highestBlock,
                        blockKey,
                        actualCenterLocation,
                        type,
                        config,
                        player,
                        rotationRange,
                        random,
                        earthquakeType,
                        spawnOffsetY,
                        blocksToRestore,
                        randomSize
                    )
                }
            }
        }
        
        // 순차적 처리가 아닌 경우에만 즉시 복원 스케줄링
        // 순차적 처리인 경우는 모든 블럭이 처리된 후에 복원 스케줄링해야 함
        if (!sequential && blocksToRestore.isNotEmpty()) {
            scheduleRestore(blocksToRestore, restoreTimeSeconds, animationTicks, delayBetweenRestores)
        } else if (sequential && finalGroups != null && finalGroups.isNotEmpty()) {
            // 순차적 처리인 경우 마지막 그룹 처리 후 복원 스케줄링
            val totalDelay = (totalSequentialTime * 20f).toLong() + 5 // 여유 시간 추가
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                // blocksToRestore의 복사본을 만들어서 사용 (동기화 문제 방지)
                val blocksCopy = synchronized(blocksToRestore) {
                    blocksToRestore.toList()
                }
                if (blocksCopy.isNotEmpty()) {
                    scheduleRestore(blocksCopy.toMutableList(), restoreTimeSeconds, animationTicks, delayBetweenRestores)
                }
            }, totalDelay)
        }
    }
    
    /**
     * 블럭 처리 (내부 메서드)
     */
    private fun processBlock(
        highestBlock: Block,
        blockKey: String,
        actualCenterLocation: Location,
        type: DetectionType,
        config: Any?,
        player: Player?,
        rotationRange: Float,
        random: java.util.Random,
        earthquakeType: EarthquakeType,
        spawnOffsetY: Double,
        blocksToRestore: MutableList<BlockRestoreInfo>,
        randomSize: Boolean
    ) {
        // 예외 블럭(빛 블럭, 베리어)은 처리하지 않음
        val blockType = highestBlock.type
        if (blockType == Material.BARRIER || blockType == Material.LIGHT) {
            return
        }
        
        val world = highestBlock.world
        val existingInfo = activeBlocks[blockKey]
        
        if (existingInfo != null && existingInfo.blockDisplay.isValid) {
                    // 이미 활성화된 블럭 디스플레이가 있으면 재사용
                    // 기존 복원 스케줄 취소
                    restoreSchedules[blockKey]?.cancel()
                    restoreSchedules.remove(blockKey)
                    
                    // 블럭 파티클 생성 (블럭 중심 위치)
                    val blockLocation = existingInfo.block.location
                    val particleLocation = blockLocation.clone().add(0.5, 0.5, 0.5)
                    world.spawnParticle(
                        Particle.BLOCK,
                        particleLocation,
                        20,  // 파티클 개수
                        0.3, 0.3, 0.3,  // 오프셋
                        0.1,  // 속도
                        existingInfo.originalBlockData
                    )
                    
                    // 기존 회전값과 다른 새로운 회전값 생성
                    var newRotation: RotationValues
                    var attempts = 0
                    do {
                        newRotation = EarthquakeCalculator.calculateRotation(
                            existingInfo.block.location,
                            actualCenterLocation,
                            type,
                            config,
                            player,
                            rotationRange,
                            random,
                            earthquakeType
                        )
                        attempts++
                    } while (
                        attempts < 10 && 
                        kotlin.math.abs(newRotation.yaw - existingInfo.initialYaw) < 0.1f &&
                        kotlin.math.abs(newRotation.pitch - existingInfo.initialPitch) < 0.1f &&
                        kotlin.math.abs(newRotation.roll - existingInfo.initialRoll) < 0.1f
                    )
                    
                    // 랜덤 사이즈 계산 (0.9 ~ 1.2 범위)
                    val randomScale = if (randomSize) {
                        val scaleValue = 0.9f + random.nextFloat() * (1.2f - 0.9f)
                        Vector3f(scaleValue, scaleValue, scaleValue)
                    } else {
                        null
                    }
                    
                    // 새로운 회전값 즉시 적용
                    existingInfo.blockDisplay.setTransformLocal(
                        px = 0.5f, py = 0.5f, pz = 0.5f,
                        euler = EulerDeg(yaw = newRotation.yaw, pitch = newRotation.pitch, roll = newRotation.roll),
                        scale = randomScale
                    )
                    
                    // 복원 정보 업데이트 (새로운 회전값으로)
                    val updatedInfo = BlockRestoreInfo(
                        existingInfo.block,
                        existingInfo.originalBlockData,
                        existingInfo.blockDisplay,
                        newRotation.yaw,
                        newRotation.pitch,
                        newRotation.roll
                    )
            activeBlocks[blockKey] = updatedInfo
            blocksToRestore.add(updatedInfo)
        } else {
            // 새로운 블럭 디스플레이 생성
            val originalBlockData = highestBlock.blockData.clone()
            val blockLocation = highestBlock.location
            
            // 베리어로 변경
            highestBlock.type = Material.BARRIER
            
            // 블럭 파티클 생성 (블럭 중심 위치)
            val particleLocation = blockLocation.clone().add(0.5, 0.5, 0.5)
            world.spawnParticle(
                Particle.BLOCK,
                particleLocation,
                20,  // 파티클 개수
                0.3, 0.3, 0.3,  // 오프셋
                0.1,  // 속도
                originalBlockData
            )
            
            // 청크 로드 확인
            val chunk = world.getChunkAt(blockLocation)
            if (!chunk.isLoaded) {
                chunk.load()
            }
            
            // 높이 오프셋 계산 (지진 효과 타입에 따라)
            val calculatedOffsetY = EarthquakeCalculator.calculateOffsetY(
                blockLocation,
                actualCenterLocation,
                type,
                config,
                player,
                spawnOffsetY,
                earthquakeType
            )
            
            // 블럭 디스플레이 소환
            val displayLocation = blockLocation.clone().add(0.0, calculatedOffsetY, 0.0)
            val blockDisplay = world.spawn(displayLocation, BlockDisplay::class.java) { display ->
                display.block = originalBlockData
            }

            blockDisplay.interpolationDuration = 1
            blockDisplay.interpolationDelay = 0
            
            // 회전값 계산 (지진 효과 타입에 따라)
            val rotation = EarthquakeCalculator.calculateRotation(
                blockLocation,
                actualCenterLocation,
                type,
                config,
                player,
                rotationRange,
                random,
                earthquakeType
            )
            
            // 랜덤 사이즈 계산 (0.9 ~ 1.2 범위)
            val randomScale = if (randomSize) {
                val scaleValue = 0.9f + random.nextFloat() * (1.3f - 0.8f)
                Vector3f(scaleValue, scaleValue, scaleValue)
            } else {
                null
            }
            
            blockDisplay.setTransformLocal(
                px = 0.5f, py = 0.5f, pz = 0.5f,
                euler = EulerDeg(yaw = rotation.yaw, pitch = rotation.pitch, roll = rotation.roll),
                scale = randomScale
            )
            
            // 블럭 디스플레이가 살짝 튀는 애니메이션 (translation)
            val bounceHeight = 0.15
            val bounceTicks = 5L
            val baseLocation = displayLocation.clone()
            object : BukkitRunnable() {
                var currentTick = 0L
                override fun run() {
                    if (!blockDisplay.isValid) {
                        cancel()
                        return
                    }

                    if (currentTick >= bounceTicks) {
                        blockDisplay.teleport(baseLocation)
                        cancel()
                        return
                    }

                    val progress = currentTick.toFloat() / bounceTicks.toFloat()
                    val eased = if (progress <= 0.5f) {
                        progress * 2f
                    } else {
                        2f - progress * 2f
                    }
                    val offsetY = eased * bounceHeight
                    val currentLocation = baseLocation.clone().add(0.0, offsetY, 0.0)
                    blockDisplay.teleport(currentLocation)

                    currentTick++
                }
            }.runTaskTimer(plugin, 0L, 1L)

            // 복원 정보 저장
            val restoreInfo = BlockRestoreInfo(
                highestBlock,
                originalBlockData,
                blockDisplay,
                rotation.yaw,
                rotation.pitch,
                rotation.roll
            )
            activeBlocks[blockKey] = restoreInfo
            blocksToRestore.add(restoreInfo)
        }
    }
    
    
    /**
     * 블럭 복원 스케줄링
     */
    private fun scheduleRestore(
        blocksToRestore: MutableList<BlockRestoreInfo>,
        restoreTimeSeconds: Long,
        animationTicks: Long,
        delayBetweenRestores: Long
    ) {
        val random = java.util.Random()
        blocksToRestore.shuffle(random)
        
        // 블럭 수에 따라 동적으로 그룹 크기 결정
        // 블럭이 많을수록 더 많은 블럭을 동시에 복구
        val blockCount = blocksToRestore.size
        val groupSize = when {
            blockCount <= 30 -> 5
            blockCount <= 60 -> 5
            blockCount <= 100 -> 15
            blockCount <= 150 -> 30
            blockCount <= 200 -> 35
            else -> 40
        }
        val groups = blocksToRestore.chunked(groupSize)
        
        // 각 블럭의 복원 스케줄 저장
        blocksToRestore.forEach { restoreInfo ->
            val blockKey = getBlockKey(restoreInfo.block)
            
            // 그룹 인덱스 찾기
            val groupIndex = groups.indexOfFirst { group -> group.contains(restoreInfo) }
            val groupStartTick = if (groupIndex >= 0) groupIndex * delayBetweenRestores else 0L
            
            var lastRestoreTask: BukkitTask? = null
            
            // 회전 애니메이션 스케줄링
            for (tick in 0..animationTicks) {
                val progress = tick.toFloat() / animationTicks.toFloat()
                
                // 선형 보간 (Lerp)
                val currentYaw = restoreInfo.initialYaw * (1f - progress)
                val currentPitch = restoreInfo.initialPitch * (1f - progress)
                val currentRoll = restoreInfo.initialRoll * (1f - progress)
                
                val finalTick = tick
                val task = Bukkit.getScheduler().runTaskLater(
                    plugin,
                    Runnable {
                        // 블럭 디스플레이가 여전히 유효한지 확인
                        if (!restoreInfo.blockDisplay.isValid) {
                            // 이미 제거된 경우 복원만 수행
                            if (finalTick == animationTicks) {
                                restoreInfo.block.blockData = restoreInfo.originalBlockData
                                activeBlocks.remove(blockKey)
                                restoreSchedules.remove(blockKey)
                            }
                            return@Runnable
                        }
                        
                        // 회전값 업데이트
                        restoreInfo.blockDisplay.setTransformLocal(
                            px = 0.5f, py = 0.5f, pz = 0.5f,
                            euler = EulerDeg(
                                yaw = currentYaw,
                                pitch = currentPitch,
                                roll = currentRoll
                            )
                        )
                        
                        // 마지막 틱에서 블럭 복원
                        if (finalTick == animationTicks) {
                            restoreInfo.blockDisplay.remove()
                            restoreInfo.block.blockData = restoreInfo.originalBlockData
                            
                            // 활성화된 블럭 목록에서 제거
                            activeBlocks.remove(blockKey)
                            restoreSchedules.remove(blockKey)
                        }
                    },
                    restoreTimeSeconds * 20L + groupStartTick + finalTick
                )
                
                // 마지막 틱의 태스크를 저장 (취소용)
                if (finalTick == animationTicks) {
                    lastRestoreTask = task
                }
            }
            
            // 복원 스케줄 추적용 (마지막 틱의 태스크를 저장)
            if (lastRestoreTask != null) {
                restoreSchedules[blockKey] = lastRestoreTask!!
            }
        }
    }
}

