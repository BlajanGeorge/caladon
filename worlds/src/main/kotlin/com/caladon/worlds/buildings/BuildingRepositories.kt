package com.caladon.worlds.buildings

import org.springframework.data.jpa.repository.JpaRepository

interface CityBuildingRepository : JpaRepository<CityBuilding, CityBuildingId> {
    fun findAllByIdCityId(cityId: Long): List<CityBuilding>
}

interface CityBuildOrderRepository : JpaRepository<CityBuildOrder, Long> {
    fun findAllByCityIdOrderByCompletesAtAscIdAsc(cityId: Long): List<CityBuildOrder>
}
