package org.example.hoon.cinematicCore.util.block

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import kotlin.math.*

/**
 * 지진 효과 계산 유틸리티 클래스
 * 회전값 및 높이 오프셋 계산 담당
 */
internal object EarthquakeCalculator {
    
    /**
     * 높이 오프셋 계산
     */
    fun calculateOffsetY(
        blockLocation: Location,
        centerLocation: Location,
        detectionType: DetectionType,
        config: Any?,
        player: Player?,
        baseOffsetY: Double,
        earthquakeType: EarthquakeType
    ): Double {
        return when (earthquakeType) {
            EarthquakeType.UNIFORM -> {
                // 고르게 (기본값 사용)
                baseOffsetY
            }
            EarthquakeType.DISTANCE_BASED,
            EarthquakeType.DISTANCE_BASED_CENTER -> {
                // 원형: 중심에서 멀어질수록 높게
                if (detectionType != DetectionType.CIRCULAR && detectionType != DetectionType.CIRCULAR_EDGE) {
                    return baseOffsetY
                }
                val circularConfig = config as? CircularConfig ?: CircularConfig(10)
                val distance = blockLocation.distance(centerLocation)
                val maxDistance = circularConfig.radius.toDouble()
                val distanceFactor = (distance / maxDistance).coerceIn(0.0, 1.0)
                
                // 중심: 0.0, 가장자리: 0.4
                0.0 + (distanceFactor * 0.5)
            }
            EarthquakeType.WIDTH_BASED,
            EarthquakeType.WIDTH_BASED_CENTER -> {
                // 전방 사각형: 넓이 방향에서 중심에서 멀어질수록 높게
                if (detectionType != DetectionType.FORWARD_RECTANGLE) {
                    return baseOffsetY
                }
                val rectConfig = config as? ForwardRectangleConfig ?: ForwardRectangleConfig(5, 10)
                val playerLoc = player?.location ?: centerLocation
                val direction = playerLoc.direction.normalize()
                val up = Vector(0, 1, 0)
                val right = direction.clone().crossProduct(up).normalize()
                
                // 넓이 방향의 거리
                val toBlock = Vector(
                    blockLocation.x - centerLocation.x,
                    0.0,
                    blockLocation.z - centerLocation.z
                )
                val widthDistance = abs(toBlock.dot(right))
                val maxWidthDistance = rectConfig.width / 2.0
                val widthFactor = (widthDistance / maxWidthDistance).coerceIn(0.0, 1.0)
                
                // 중심: 0.0, 가장자리: 0.4
                0.0 + (widthFactor * 0.5)
            }
        }
    }
    
    /**
     * 회전값 계산
     */
    fun calculateRotation(
        blockLocation: Location,
        centerLocation: Location,
        detectionType: DetectionType,
        config: Any?,
        player: Player?,
        rotationRange: Float,
        random: java.util.Random,
        earthquakeType: EarthquakeType
    ): RotationValues {
        return when (earthquakeType) {
            EarthquakeType.UNIFORM -> {
                // 고르게 회전
                RotationValues(
                    yaw = (-rotationRange + random.nextFloat() * rotationRange * 2f),
                    pitch = (-rotationRange + random.nextFloat() * rotationRange * 2f),
                    roll = (-rotationRange + random.nextFloat() * rotationRange * 2f)
                )
            }
            EarthquakeType.DISTANCE_BASED -> {
                // 원형: 중심에서 멀어질수록 더 강하게 기울어짐
                if (detectionType != DetectionType.CIRCULAR && detectionType != DetectionType.CIRCULAR_EDGE) {
                    return calculateRotation(blockLocation, centerLocation, detectionType, config, player, rotationRange, random, EarthquakeType.UNIFORM)
                }
                val circularConfig = config as? CircularConfig ?: CircularConfig(10)
                val distance = blockLocation.distance(centerLocation)
                val maxDistance = circularConfig.radius.toDouble()
                val distanceFactor = (distance / maxDistance).coerceIn(0.0, 1.0)
                
                RotationValues(
                    yaw = (-rotationRange * (1f + distanceFactor.toFloat()) + random.nextFloat() * rotationRange * 2f * (1f + distanceFactor.toFloat())),
                    pitch = (-rotationRange * (1f + distanceFactor.toFloat()) + random.nextFloat() * rotationRange * 2f * (1f + distanceFactor.toFloat())),
                    roll = (-rotationRange * (1f + distanceFactor.toFloat()) + random.nextFloat() * rotationRange * 2f * (1f + distanceFactor.toFloat()))
                )
            }
            EarthquakeType.DISTANCE_BASED_CENTER -> {
                // 원형: 중심에서 멀어질수록 더 강하게 기울어지고 중심을 향함
                if (detectionType != DetectionType.CIRCULAR && detectionType != DetectionType.CIRCULAR_EDGE) {
                    return calculateRotation(blockLocation, centerLocation, detectionType, config, player, rotationRange, random, EarthquakeType.UNIFORM)
                }
                val circularConfig = config as? CircularConfig ?: CircularConfig(10)
                val distance = blockLocation.distance(centerLocation)
                val maxDistance = circularConfig.radius.toDouble()
                val distanceFactor = (distance / maxDistance).coerceIn(0.0, 1.0)
                
                // 중심을 향하는 방향 벡터 계산 (수평면)
                val directionToCenter = Vector(
                    centerLocation.x - blockLocation.x,
                    0.0,
                    centerLocation.z - blockLocation.z
                ).normalize()
                
                // 거리에 비례한 회전 강도 (최대 22.5도까지)
                // 중심: 0도, 가장자리: 22.5도
                val maxTiltAngle = 22.5f
                val tiltAngle = distanceFactor.toFloat() * maxTiltAngle
                
                // 중심 방향으로 기울어지도록 pitch와 roll 계산
                val pitchTowardCenter = (directionToCenter.z * tiltAngle).toFloat()
                val rollTowardCenter = (-directionToCenter.x * tiltAngle).toFloat()
                
                // 랜덤 요소 추가 (기본 rotationRange 사용)
                val randomPitch = (-rotationRange * 0.3f + random.nextFloat() * rotationRange * 0.6f)
                val randomRoll = (-rotationRange * 0.3f + random.nextFloat() * rotationRange * 0.6f)
                val randomYaw = (-rotationRange + random.nextFloat() * rotationRange * 2f)
                
                RotationValues(
                    yaw = randomYaw,
                    pitch = pitchTowardCenter + randomPitch,
                    roll = rollTowardCenter + randomRoll
                )
            }
            EarthquakeType.WIDTH_BASED -> {
                // 전방 사각형: 넓이 방향에서 중심에서 멀어질수록 더 강하게 기울어짐
                if (detectionType != DetectionType.FORWARD_RECTANGLE) {
                    return calculateRotation(blockLocation, centerLocation, detectionType, config, player, rotationRange, random, EarthquakeType.UNIFORM)
                }
                val rectConfig = config as? ForwardRectangleConfig ?: ForwardRectangleConfig(5, 10)
                val playerLoc = player?.location ?: centerLocation
                val direction = playerLoc.direction.normalize()
                val up = Vector(0, 1, 0)
                val right = direction.clone().crossProduct(up).normalize()
                
                val toBlock = Vector(
                    blockLocation.x - centerLocation.x,
                    0.0,
                    blockLocation.z - centerLocation.z
                )
                
                val widthDistance = abs(toBlock.dot(right))
                val maxWidthDistance = rectConfig.width / 2.0
                val widthFactor = (widthDistance / maxWidthDistance).coerceIn(0.0, 1.0)
                
                RotationValues(
                    yaw = (-rotationRange * (1f + widthFactor.toFloat()) + random.nextFloat() * rotationRange * 2f * (1f + widthFactor.toFloat())),
                    pitch = (-rotationRange * (1f + widthFactor.toFloat()) + random.nextFloat() * rotationRange * 2f * (1f + widthFactor.toFloat())),
                    roll = (-rotationRange * (1f + widthFactor.toFloat()) + random.nextFloat() * rotationRange * 2f * (1f + widthFactor.toFloat()))
                )
            }
            EarthquakeType.WIDTH_BASED_CENTER -> {
                // 전방 사각형: 넓이 방향에서 중심에서 멀어질수록 더 강하게 기울어지고 중심을 향함
                if (detectionType != DetectionType.FORWARD_RECTANGLE) {
                    return calculateRotation(blockLocation, centerLocation, detectionType, config, player, rotationRange, random, EarthquakeType.UNIFORM)
                }
                val rectConfig = config as? ForwardRectangleConfig ?: ForwardRectangleConfig(5, 10)
                val playerLoc = player?.location ?: centerLocation
                val direction = playerLoc.direction.normalize()
                val up = Vector(0, 1, 0)
                val right = direction.clone().crossProduct(up).normalize()
                
                val toCenter = Vector(
                    centerLocation.x - blockLocation.x,
                    0.0,
                    centerLocation.z - blockLocation.z
                ).normalize()
                
                val toBlock = Vector(
                    blockLocation.x - centerLocation.x,
                    0.0,
                    blockLocation.z - centerLocation.z
                )
                val widthDistance = abs(toBlock.dot(right))
                val maxWidthDistance = rectConfig.width / 2.0
                val widthFactor = (widthDistance / maxWidthDistance).coerceIn(0.0, 1.0)
                
                val rotationStrength = rotationRange * (1f + widthFactor.toFloat())
                
                val pitchTowardCenter = (toCenter.z * rotationStrength * 0.8f).toFloat()
                val rollTowardCenter = (-toCenter.x * rotationStrength * 0.8f).toFloat()
                
                val randomPitch = (-rotationRange * 0.3f + random.nextFloat() * rotationRange * 0.6f)
                val randomRoll = (-rotationRange * 0.3f + random.nextFloat() * rotationRange * 0.6f)
                val randomYaw = (-rotationRange + random.nextFloat() * rotationRange * 2f)
                
                RotationValues(
                    yaw = randomYaw,
                    pitch = pitchTowardCenter + randomPitch,
                    roll = rollTowardCenter + randomRoll
                )
            }
        }
    }
}

