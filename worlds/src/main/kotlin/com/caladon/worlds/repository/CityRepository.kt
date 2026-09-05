package com.caladon.worlds.repository

import com.caladon.worlds.domain.City
import org.springframework.data.jpa.repository.JpaRepository

interface CityRepository : JpaRepository<City, Long>
