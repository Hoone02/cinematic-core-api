package org.example.hoon.cinematicCore.util.entity

import org.bukkit.Location
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import org.example.hoon.cinematicCore.model.service.EntityModelUtil

/**
 * Passenger 위치를 커스텀 오프셋으로 제어하는 매니저
 * 
 * 가짜 엔티티(ArmorStand)를 중간에 배치하여 탑승 위치를 조정합니다.
 * 탑승 구조: realVehicle -> fakeMount -> player
 */
class PassengerPositionManager(
    private val plugin: Plugin
) {
    
    /**
     * 엔티티에 passenger를 커스텀 오프셋으로 탑승시킵니다
     * 
     * @param vehicle 탈것 엔티티
     * @param passenger 탑승할 엔티티
     * @param forward 전방 오프셋 (양수: 앞, 음수: 뒤) - vehicle 방향 기준
     * @param up 상하 오프셋 (양수: 위, 음수: 아래) - 절대 Y축
     * @param right 좌우 오프셋 (양수: 우측, 음수: 좌측) - vehicle 방향 기준
     * @param rotateWithVehicle vehicle 방향에 따라 오프셋도 회전할지 여부 (기본값: false, 절대 오프셋)
     * @param boneUuid bone의 UUID (지정하면 해당 bone의 pivot 위치를 탑승 위치로 사용)
     * @return 위치 업데이트 태스크 (필요시 취소 가능)
     */
    fun mountWithOffset(
        vehicle: Entity,
        passenger: Entity,
        forward: Double = 0.0,
        up: Double = 0.0,
        right: Double = 0.0,
        rotateWithVehicle: Boolean = false,
        boneUuid: String? = null
    ): BukkitTask {
        // passenger가 이미 탑승 중이면 먼저 내림
        if (passenger.isInsideVehicle) {
            passenger.leaveVehicle()
        }
        
        // 보이지 않는 ArmorStand를 중간 엔티티로 생성
        val fakeMount = vehicle.world.spawn(vehicle.location, ArmorStand::class.java) { armorStand ->
            armorStand.isVisible = false
            armorStand.setGravity(false)
            armorStand.isMarker = true // 충돌 방지
            armorStand.isInvulnerable = true
            armorStand.isSmall = true
        }
        
        // fakeMount에 메타데이터 추가 (탑승 정보 추적용)
        if (vehicle is LivingEntity && EntityModelUtil.hasModel(vehicle)) {
            val session = EntityModelUtil.getSession(vehicle)
            if (session != null && boneUuid != null) {
                // fakeMount에 vehicle과 passenger 정보 저장 (내림 방지용)
                fakeMount.setMetadata("cinematiccore_vehicle", org.bukkit.metadata.FixedMetadataValue(plugin, vehicle.uniqueId.toString()))
                fakeMount.setMetadata("cinematiccore_passenger", org.bukkit.metadata.FixedMetadataValue(plugin, passenger.uniqueId.toString()))
            }
        }
        
        // boneUuid가 지정되어 있으면 bone의 pivot 위치를 사용, 아니면 오프셋 위치 사용
        val initialLoc = if (boneUuid != null && vehicle is LivingEntity && EntityModelUtil.hasModel(vehicle)) {
            // vehicle에 모델이 적용되어 있고 boneUuid가 지정된 경우
            val session = EntityModelUtil.getSession(vehicle)
            val pivotLoc = session?.mapper?.getBonePivotLocation(boneUuid)
            if (pivotLoc != null) {
                // bone의 pivot 위치에 오프셋 적용
                if (forward != 0.0 || up != 0.0 || right != 0.0) {
                    // 오프셋이 있는 경우 bone 위치에 오프셋 추가
                    val offsetLoc = pivotLoc.clone()
                    if (rotateWithVehicle) {
                        // vehicle 방향 기준 오프셋
                        val vehicleYaw = getVehicleYaw(vehicle)
                        val yawRad = Math.toRadians(vehicleYaw.toDouble())
                        val dirX = -Math.sin(yawRad)
                        val dirZ = Math.cos(yawRad)
                        val dir = Vector(dirX, 0.0, dirZ).normalize()
                        val rightVec = dir.clone().crossProduct(Vector(0, 1, 0)).normalize()
                        
                        offsetLoc.add(dir.multiply(forward))
                        offsetLoc.add(0.0, up, 0.0)
                        offsetLoc.add(rightVec.multiply(right))
                    } else {
                        // 절대 오프셋
                        offsetLoc.add(right, up, -forward)
                    }
                    offsetLoc
                } else {
                    // 오프셋이 없으면 bone의 pivot 위치만 사용
                    pivotLoc
                }
            } else {
                // bone을 찾을 수 없으면 기본 오프셋 위치 사용
                getOffsetLocation(vehicle, forward, up, right, rotateWithVehicle)
            }
        } else {
            // boneUuid가 없거나 모델이 없는 경우 기본 오프셋 위치 사용
            getOffsetLocation(vehicle, forward, up, right, rotateWithVehicle)
        }
        
        fakeMount.teleport(initialLoc)
        
        // passenger를 fakeMount에 탑승
        fakeMount.addPassenger(passenger)
        
        // 위치 지속 업데이트
        // fakeMount를 vehicle에 탑승시키지 않고, 위치를 vehicle 기준으로 계속 동기화
        val task = object : org.bukkit.scheduler.BukkitRunnable() {
            override fun run() {
                // 엔티티 유효성 확인
                if (!passenger.isValid || !fakeMount.isValid || !vehicle.isValid) {
                    if (fakeMount.isValid) {
                        fakeMount.remove()
                    }
                    cancel()
                    return
                }
                
                // passenger가 여전히 fakeMount에 탑승 중인지 확인
                if (passenger.vehicle != fakeMount) {
                    // 내릴 수 없는 경우 다시 탑승
                    if (vehicle is LivingEntity && EntityModelUtil.hasModel(vehicle)) {
                        val session = EntityModelUtil.getSession(vehicle)
                        if (session != null && boneUuid != null && session.isPassengerMounted(passenger)) {
                            if (!session.canPassengerDismount(passenger)) {
                                // 내릴 수 없으므로 다시 탑승
                                if (fakeMount.isValid && passenger.isValid) {
                                    fakeMount.addPassenger(passenger)
                                }
                                return
                            }
                        }
                    }
                    
                    // 내릴 수 있는 경우 또는 탑승 정보가 없는 경우 정상 처리
                    if (fakeMount.isValid) {
                        fakeMount.remove()
                    }
                    cancel()
                    return
                }
                
                // 위치 계산
                val newLoc = if (boneUuid != null && vehicle is LivingEntity && EntityModelUtil.hasModel(vehicle)) {
                    // bone의 pivot 위치 사용
                    val session = EntityModelUtil.getSession(vehicle)
                    val pivotLoc = session?.mapper?.getBonePivotLocation(boneUuid)
                    if (pivotLoc != null) {
                        // bone의 pivot 위치에 오프셋 적용
                        if (forward != 0.0 || up != 0.0 || right != 0.0) {
                            // 오프셋이 있는 경우 bone 위치에 오프셋 추가
                            val offsetLoc = pivotLoc.clone()
                            if (rotateWithVehicle) {
                                // vehicle 방향 기준 오프셋
                                val vehicleYaw = getVehicleYaw(vehicle)
                                val yawRad = Math.toRadians(vehicleYaw.toDouble())
                                val dirX = -Math.sin(yawRad)
                                val dirZ = Math.cos(yawRad)
                                val dir = Vector(dirX, 0.0, dirZ).normalize()
                                val rightVec = dir.clone().crossProduct(Vector(0, 1, 0)).normalize()
                                
                                offsetLoc.add(dir.multiply(forward))
                                offsetLoc.add(0.0, up, 0.0)
                                offsetLoc.add(rightVec.multiply(right))
                            } else {
                                // 절대 오프셋
                                offsetLoc.add(right, up, -forward)
                            }
                            offsetLoc
                        } else {
                            // 오프셋이 없으면 bone의 pivot 위치만 사용
                            pivotLoc
                        }
                    } else {
                        // bone을 찾을 수 없으면 기본 오프셋 위치 사용
                        getOffsetLocation(vehicle, forward, up, right, rotateWithVehicle)
                    }
                } else {
                    // boneUuid가 없거나 모델이 없는 경우 기본 오프셋 위치 사용
                    getOffsetLocation(vehicle, forward, up, right, rotateWithVehicle)
                }
                
                // fakeMount의 위치를 업데이트
                fakeMount.teleport(newLoc)
                
                // NMS를 사용하여 위치를 강제 업데이트
                try {
                    val craftFakeMount = fakeMount as? org.bukkit.craftbukkit.entity.CraftArmorStand
                    if (craftFakeMount != null) {
                        val nmsFakeMount = craftFakeMount.handle
                        nmsFakeMount.setPos(newLoc.x, newLoc.y, newLoc.z)
                        // 이전 틱 위치도 업데이트 (이동 감지 방지)
                        nmsFakeMount.xo = newLoc.x
                        nmsFakeMount.yo = newLoc.y
                        nmsFakeMount.zo = newLoc.z
                    }
                } catch (e: Exception) {
                    // NMS 접근 실패 시 무시 (정상)
                }
            }
        }
        
        // 즉시 한 번 실행하고, 매 틱마다 실행
        task.run()
        return task.runTaskTimer(plugin, 1L, 1L)
    }
    
    /**
     * vehicle에 적용된 모델의 yaw를 가져옵니다
     * 모델이 없으면 vehicle의 기본 yaw를 반환합니다
     */
    private fun getVehicleYaw(vehicle: Entity): Float {
        // vehicle이 LivingEntity이고 모델이 적용되어 있는지 확인
        if (vehicle is LivingEntity && EntityModelUtil.hasModel(vehicle)) {
            // 모델이 적용된 경우 baseEntity의 bodyYaw 사용 (RotationSynchronizer가 계산한 yaw)
            return vehicle.bodyYaw
        }
        // 모델이 없으면 기본 yaw 사용
        return vehicle.location.yaw
    }
    
    /**
     * 탈것 기준 상대 좌표로 오프셋 위치를 계산합니다
     * 
     * @param vehicle 탈것 엔티티
     * @param forward 전방 오프셋 (양수: 앞, 음수: 뒤)
     * @param up 상하 오프셋 (양수: 위, 음수: 아래) - 항상 절대 Y축
     * @param right 좌우 오프셋 (양수: 우측, 음수: 좌측)
     * @param rotateWithVehicle vehicle 방향에 따라 오프셋도 회전할지 여부
     * @return 오프셋이 적용된 위치
     */
    private fun getOffsetLocation(
        vehicle: Entity,
        forward: Double,
        up: Double,
        right: Double,
        rotateWithVehicle: Boolean
    ): Location {
        val loc = vehicle.location.clone()
        
        if (rotateWithVehicle) {
            // vehicle에 적용된 모델의 yaw 사용
            val vehicleYaw = getVehicleYaw(vehicle)
            
            // yaw를 라디안으로 변환하여 방향 벡터 계산
            val yawRad = Math.toRadians(vehicleYaw.toDouble())
            val dirX = -Math.sin(yawRad)
            val dirZ = Math.cos(yawRad)
            val dir = Vector(dirX, 0.0, dirZ).normalize()
            
            // 우측 벡터 계산 (Y축 기준으로 외적)
            val rightVec = dir.clone().crossProduct(Vector(0, 1, 0)).normalize()
            
            return loc
                .add(dir.multiply(forward))           // 전후 (모델 yaw 기준)
                .add(0.0, up, 0.0)                    // 상하 (절대 Y축)
                .add(rightVec.multiply(right))         // 좌우 (모델 yaw 기준)
                .apply {
                    yaw = vehicleYaw
                    pitch = loc.pitch
                }
        } else {
            // 절대 오프셋 (vehicle 방향과 무관)
            // forward: 북쪽 방향 기준 (Z축 음수)
            // right: 동쪽 방향 기준 (X축 양수)
            // up: Y축 (항상 절대)
            return loc
                .add(0.0, up, -forward)               // forward는 북쪽(Z축 음수) 기준
                .add(right, 0.0, 0.0)                 // right는 동쪽(X축 양수) 기준
                .apply {
                    yaw = loc.yaw
                    pitch = loc.pitch
                }
        }
    }
}

