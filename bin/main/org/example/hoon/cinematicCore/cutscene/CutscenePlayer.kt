package org.example.hoon.cinematicCore.cutscene

import io.papermc.paper.entity.TeleportFlag
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * 컷씬 재생 클래스
 */
class CutscenePlayer(private val plugin: Plugin) {
    
    // 플레이어별 재생 상태
    val playingPlayers: MutableMap<Player, PlayingState> = mutableMapOf()
    
    /**
     * 컷씬 재생
     * 
     * @param cutscene 재생할 컷씬
     * @param player 플레이어
     * @param durationSeconds 재생 시간 (초), null이면 자동 계산
     * @param startFromIndex 시작할 키프레임 인덱스 (기본값: 0)
     * @return 재생 성공 여부
     */
    fun play(cutscene: Cutscene, player: Player, durationSeconds: Double? = null, startFromIndex: Int = 0): Boolean {
        if (cutscene.keyframes.isEmpty()) {
            player.sendActionBar("§c키프레임이 없습니다.")
            return false
        }
        
        if (playingPlayers.containsKey(player)) {
            player.sendActionBar("§c이미 컷씬이 재생 중입니다.")
            return false
        }
        
        // 시작 인덱스 유효성 검사
        if (startFromIndex < 0 || startFromIndex >= cutscene.keyframes.size) {
            player.sendActionBar("§c유효하지 않은 키프레임 인덱스입니다.")
            return false
        }
        
        // 시작 인덱스부터 키프레임 리스트 자르기
        val keyframesFromStart = cutscene.keyframes.drop(startFromIndex)
        if (keyframesFromStart.size < 2) {
            player.sendActionBar("§c재생할 키프레임이 부족합니다.")
            return false
        }
        
        // 시작 위치
        val startLocation = keyframesFromStart.first().location
        
        // 청크 로드 보장
        val chunk = startLocation.chunk
        if (!chunk.isLoaded) {
            chunk.load()
        }
        
        // 플레이어를 카메라에 빙의
        val originalLocation = player.location.clone()
        val originalGameMode = player.gameMode
        
        // 플레이어를 스펙터 모드로 변경
        player.gameMode = GameMode.SPECTATOR
        
        // 플레이어를 카메라 위치로 먼저 텔레포트 (청크 로드 후)
        player.teleport(startLocation)
        
        // 다음 틱에 카메라 생성 및 빙의 (청크가 완전히 로드된 후)
        plugin.server.scheduler.runTask(plugin, Runnable {
            // 카메라 ArmorStand 생성
            val cameraEntity = createCameraEntity(startLocation)
            
            // 카메라에 플레이어를 passenger로 추가
            cameraEntity.addPassenger(player)
            
            // 플레이어가 카메라를 관찰하도록 설정
            player.setSpectatorTarget(cameraEntity)
            
            // 재생 시간 계산 (키프레임 간 거리 기반)
            val duration = durationSeconds ?: calculateDuration(keyframesFromStart)
            
            // 시작 인덱스부터의 컷씬 생성
            val cutsceneFromStart = Cutscene(cutscene.name, keyframesFromStart)
            
            // 재생 시작
            val task = startPlayback(cutsceneFromStart, cameraEntity, player, duration, originalLocation, originalGameMode)
            
            // 재생 상태 저장
            playingPlayers[player] = PlayingState(cameraEntity, task, originalLocation, originalGameMode)
            
            player.sendActionBar("§a컷씬 재생 시작: §f${cutscene.name}")
        })
        
        return true
        
    }
    
    /**
     * 재생 중지
     * 
     * @param player 플레이어
     * @param restoreOriginalLocation true면 재생 전 위치로 복원, false면 현재 위치에서 멈춤
     */
    fun stop(player: Player, restoreOriginalLocation: Boolean = true) {
        val state = playingPlayers.remove(player) ?: return
        
        state.task.cancel()
        state.cameraEntity.remove()
        
        if (restoreOriginalLocation) {
            // 플레이어 원래 위치로 복원
            player.teleport(state.originalLocation)
        }
        // false면 현재 위치에서 멈춤 (카메라 엔티티 위치에 있음)
        
        player.gameMode = state.originalGameMode
        
        player.sendActionBar("§a컷씬 재생 중지")
    }
    
    /**
     * 카메라 ArmorStand 생성
     */
    private fun createCameraEntity(location: Location): ArmorStand {
        // 청크 로드 보장
        val chunk = location.chunk
        if (!chunk.isLoaded) {
            chunk.load()
        }
        
        return location.world.spawn(location, ArmorStand::class.java).apply {
            isInvisible = true
            isInvulnerable = true
            setGravity(false)
            setSilent(true)
            isSmall = true
            setArms(false)
            setBasePlate(false)
            setCollidable(false)
            setPersistent(false)
            setRemoveWhenFarAway(false)
            equipment?.clear()
            
            // 부드러운 이동을 위한 설정
            // ArmorStand는 interpolation이 없으므로 매 틱마다 직접 이동
        }
    }
    
    /**
     * 재생 시간 계산 (키프레임 간 거리 기반)
     */
    private fun calculateDuration(keyframes: List<Keyframe>): Double {
        if (keyframes.size < 2) {
            return 5.0 // 기본 5초
        }
        
        var totalDistance = 0.0
        for (i in 0 until keyframes.size - 1) {
            totalDistance += keyframes[i].location.distance(keyframes[i + 1].location)
        }
        
        // 거리당 약 0.5초 (조정 가능)
        return (totalDistance * 0.5).coerceAtLeast(3.0).coerceAtMost(60.0)
    }
    
    /**
     * 재생 시작
     */
    private fun startPlayback(
        cutscene: Cutscene,
        cameraEntity: ArmorStand,
        player: Player,
        durationSeconds: Double,
        originalLocation: Location,
        originalGameMode: GameMode
    ): BukkitTask {
        val keyframes = cutscene.keyframes
        if (keyframes.size < 2) {
            player.sendActionBar("§c재생할 키프레임이 부족합니다.")
            stop(player)
            return plugin.server.scheduler.runTaskTimer(plugin, Runnable {}, 0L, 1L)
        }

        val segments = keyframes.size - 1
        val segmentDistances = DoubleArray(segments) { index ->
            keyframes[index].location.distance(keyframes[index + 1].location).coerceAtLeast(0.001)
        }
        val baseDurationSeconds = durationSeconds ?: calculateDuration(keyframes)
        var baseTotalTicks = (baseDurationSeconds * 20).coerceAtLeast(segments.toDouble())

        val customTicks = DoubleArray(segments) { index ->
            keyframes[index].durationSeconds?.let { (it * 20).coerceAtLeast(1.0) } ?: Double.NaN
        }
        val customTicksSum = customTicks.filter { !it.isNaN() }.sum()
        val autoSegmentIndices = (0 until segments).filter { customTicks[it].isNaN() }
        val autoCount = autoSegmentIndices.size

        if (customTicksSum + autoCount > baseTotalTicks) {
            baseTotalTicks = customTicksSum + autoCount
        }

        val remainingTicks = (baseTotalTicks - customTicksSum).coerceAtLeast(autoCount.toDouble())
        val autoDistanceTotal = autoSegmentIndices.sumOf { segmentDistances[it] }

        val segmentTickTargets = LongArray(segments)
        customTicks.forEachIndexed { index, value ->
            if (!value.isNaN()) {
                segmentTickTargets[index] = value.roundToLong().coerceAtLeast(1)
            }
        }

        if (autoCount > 0) {
            if (autoDistanceTotal <= 0.0) {
                val ticksPerSegment = (remainingTicks / autoCount).coerceAtLeast(1.0)
                autoSegmentIndices.forEach { segmentIndex ->
                    segmentTickTargets[segmentIndex] = ticksPerSegment.roundToLong().coerceAtLeast(1)
                }
            } else {
                autoSegmentIndices.forEach { segmentIndex ->
                    val share = segmentDistances[segmentIndex] / autoDistanceTotal
                    val ticks = (remainingTicks * share).roundToLong().coerceAtLeast(1)
                    segmentTickTargets[segmentIndex] = ticks
                }
            }
        }

        var currentSegment = 0
        var tickInSegment = 0L
        var pauseTicksRemaining = 0L
        
        return plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (!player.isOnline || !cameraEntity.isValid) {
                stop(player)
                return@Runnable
            }
            
            if (currentSegment >= segments) {
                // 재생 완료
                stop(player)
                player.sendActionBar("§a컷씬 재생 완료")
                return@Runnable
            }
            
            // 멈춤 시간 처리
            if (pauseTicksRemaining > 0) {
                pauseTicksRemaining--
                // 멈춤 중에는 현재 키프레임 위치에 고정
                val currentKeyframe = keyframes[currentSegment]
                val pauseLocation = currentKeyframe.location.clone()
                
                // 청크 로드 보장
                val chunk = pauseLocation.chunk
                if (!chunk.isLoaded) {
                    chunk.load()
                }
                
                // 카메라를 현재 키프레임 위치에 고정
                cameraEntity.teleport(
                    pauseLocation,
                    TeleportFlag.EntityState.RETAIN_PASSENGERS
                )
                
                // 플레이어가 카메라에 빙의되어 있는지 확인하고 재설정
                if (!cameraEntity.passengers.contains(player)) {
                    cameraEntity.addPassenger(player)
                }
                
                // 플레이어도 카메라 위치로 동기화
                val playerDistance = player.location.distance(cameraEntity.location)
                if (playerDistance > 5.0) {
                    player.teleport(pauseLocation)
                    cameraEntity.addPassenger(player)
                }
                
                return@Runnable
            }
            
            val targetTicks = segmentTickTargets[currentSegment].coerceAtLeast(1)
            var t = tickInSegment.toDouble() / targetTicks
            
            // Smooth 적용
            val from = keyframes[currentSegment]
            val to = keyframes[currentSegment + 1]
            
            val originalT = t
            t = applySmoothEasing(t, from)
            
            // 디버깅 메시지
            val interpolatedLocation = when {
                from.interpolationType == InterpolationType.BEZIER -> {
                    val p0 = keyframes[maxOf(currentSegment - 1, 0)]
                    val p3 = keyframes[minOf(currentSegment + 2, keyframes.lastIndex)]
                    interpolateLocationCatmullRom(p0, from, to, p3, t)
                }
                else -> interpolateLocationLinear(from, to, t)
            }
            
            // 청크 로드 보장
            val chunk = interpolatedLocation.chunk
            if (!chunk.isLoaded) {
                chunk.load()
            }
            
            // 카메라 이동 및 방향 설정
            cameraEntity.teleport(
                interpolatedLocation,
                TeleportFlag.EntityState.RETAIN_PASSENGERS
            )
            
            // 플레이어가 카메라에 빙의되어 있는지 확인하고 재설정
            if (!cameraEntity.passengers.contains(player)) {
                cameraEntity.addPassenger(player)
            }
            
            // 플레이어도 카메라 위치로 동기화 (거리가 멀어지면)
            val playerDistance = player.location.distance(cameraEntity.location)
            if (playerDistance > 5.0) {
                player.teleport(interpolatedLocation)
                cameraEntity.addPassenger(player)
            }
            
            tickInSegment++
            if (tickInSegment >= targetTicks) {
                // 세그먼트 완료 - 다음 키프레임의 멈춤 시간 확인
                val nextKeyframe = keyframes[currentSegment + 1]
                if (nextKeyframe.pauseSeconds != null && nextKeyframe.pauseSeconds!! > 0.0) {
                    pauseTicksRemaining = (nextKeyframe.pauseSeconds!! * 20).toLong()
                }
                
                currentSegment++
                tickInSegment = 0L
            }
        }, 0L, 1L)
    }
    
    /**
     * 두 키프레임 간 선형 보간
     */
    private fun interpolateLocationLinear(from: Keyframe, to: Keyframe, t: Double): Location {
        val clampedT = t.coerceIn(0.0, 1.0)
        
        val x = from.location.x + (to.location.x - from.location.x) * clampedT
        val y = from.location.y + (to.location.y - from.location.y) * clampedT
        val z = from.location.z + (to.location.z - from.location.z) * clampedT
        
        // 각도 보간 (짧은 경로 선택)
        val pitch = interpolateAngle(from.pitch, to.pitch, clampedT)
        val yaw = interpolateAngle(from.yaw, to.yaw, clampedT)
        
        val location = Location(from.location.world, x, y, z)
        location.pitch = pitch
        location.yaw = yaw
        
        return location
    }
    
    /**
     * Catmull-Rom 스플라인 보간 (4개의 키프레임 필요)
     * P0, P1, P2, P3 순으로 전달
     */
    private fun interpolateLocationCatmullRom(
        p0: Keyframe,
        p1: Keyframe,
        p2: Keyframe,
        p3: Keyframe,
        t: Double
    ): Location {
        val clampedT = t.coerceIn(0.0, 1.0)

        val t2 = clampedT * clampedT
        val t3 = t2 * clampedT

        fun catmullComponent(c0: Double, c1: Double, c2: Double, c3: Double): Double {
            return 0.5 * (
                (2 * c1) +
                    (-c0 + c2) * clampedT +
                    (2 * c0 - 5 * c1 + 4 * c2 - c3) * t2 +
                    (-c0 + 3 * c1 - 3 * c2 + c3) * t3
                )
        }

        val x = catmullComponent(p0.location.x, p1.location.x, p2.location.x, p3.location.x)
        val y = catmullComponent(p0.location.y, p1.location.y, p2.location.y, p3.location.y)
        val z = catmullComponent(p0.location.z, p1.location.z, p2.location.z, p3.location.z)

        val pitch = interpolateCatmullAngle(p0.pitch, p1.pitch, p2.pitch, p3.pitch, clampedT)
        val yaw = interpolateCatmullAngle(p0.yaw, p1.yaw, p2.yaw, p3.yaw, clampedT)

        return Location(p1.location.world, x, y, z, yaw, pitch)
    }
    
    /**
     * 각도 보간 (0~360도 범위 고려)
     */
    private fun interpolateAngle(from: Float, to: Float, t: Double): Float {
        var diff = to - from
        
        // 짧은 경로 선택
        if (abs(diff) > 180) {
            if (diff > 0) {
                diff -= 360
            } else {
                diff += 360
            }
        }
        
        return (from + diff * t).toFloat()
    }

    /**
     * Catmull-Rom 보간을 이용한 각도 보간
     */
    private fun interpolateCatmullAngle(a0: Float, a1: Float, a2: Float, a3: Float, t: Double): Float {
        val angles = doubleArrayOf(a0.toDouble(), a1.toDouble(), a2.toDouble(), a3.toDouble())
        val unwrapped = DoubleArray(angles.size)
        unwrapped[0] = angles[0]
        for (i in 1 until angles.size) {
            var value = angles[i]
            while (value - unwrapped[i - 1] > 180.0) value -= 360.0
            while (value - unwrapped[i - 1] < -180.0) value += 360.0
            unwrapped[i] = value
        }

        val t2 = t * t
        val t3 = t2 * t

        val result = 0.5 * (
            (2 * unwrapped[1]) +
                (-unwrapped[0] + unwrapped[2]) * t +
                (2 * unwrapped[0] - 5 * unwrapped[1] + 4 * unwrapped[2] - unwrapped[3]) * t2 +
                (-unwrapped[0] + 3 * unwrapped[1] - 3 * unwrapped[2] + unwrapped[3]) * t3
            )

        var normalized = result % 360.0
        if (normalized < -180.0) normalized += 360.0
        if (normalized > 180.0) normalized -= 360.0
        return normalized.toFloat()
    }

    /**
     * Smooth easing 적용
     * 
     * @param t 원본 진행도 (0.0 ~ 1.0)
     * @param keyframe 현재 키프레임
     * @return easing이 적용된 진행도
     */
    /**
     * 더 부드러운 smoothstep (smootherstep)
     */
    private fun smootherstep(t: Double): Double {
        // smootherstep: 6t^5 - 15t^4 + 10t^3
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0)
    }
    
    private fun applySmoothEasing(t: Double, keyframe: Keyframe): Double {
        // 시작 smooth: 점점 빨리 시작 (ease-in) - 전체 구간에 적용
        // 끝 smooth: 점점 느려지기 (ease-out) - 전체 구간에 적용
        
        var easedT = t
        
        if (keyframe.startSmooth && keyframe.endSmooth) {
            // 둘 다 켜져있으면: ease-in-out (시작은 느리게, 중간은 빠르게, 끝은 느리게)
            // smootherstep을 사용하면 이미 ease-in-out 효과가 있음
            easedT = smootherstep(t)
        } else if (keyframe.startSmooth) {
            // 시작 smooth만: 전체 구간에 ease-in 적용 (점점 빨라짐)
            // t^2를 사용하여 부드럽게 가속
            easedT = t * t
        } else if (keyframe.endSmooth) {
            // 끝 smooth만: 전체 구간에 ease-out 적용 (점점 느려짐)
            // 1 - (1-t)^2를 사용하여 부드럽게 감속
            val oneMinusT = 1.0 - t
            easedT = 1.0 - (oneMinusT * oneMinusT)
        }
        // 둘 다 꺼져있으면 원본 t 그대로 사용
        
        return easedT.coerceIn(0.0, 1.0)
    }
    
    /**
     * 재생 상태 데이터 클래스
     */
    data class PlayingState(
        val cameraEntity: ArmorStand,
        val task: BukkitTask,
        val originalLocation: Location,
        val originalGameMode: GameMode
    )
}

