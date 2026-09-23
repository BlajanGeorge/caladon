package com.caladon.worlds.rules

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset.offset
import org.junit.jupiter.api.Test

class UnitRulesTest {
    @Test
    fun `recruit time follows the barracks factor`() {
        assertThat(UnitRules.recruitSeconds(Unit.SPEARMAN, 1)).isCloseTo(642.0, offset(0.5))   // 10m42s
        assertThat(UnitRules.recruitSeconds(Unit.SPEARMAN, 25)).isCloseTo(158.0, offset(0.5))  // 2m38s
        assertThat(UnitRules.recruitSeconds(Unit.NOBLEMAN, 1)).isCloseTo(11_321.0, offset(1.0)) // 3h08m
    }

    @Test
    fun `study time is twice the base recruit time reduced by the academy`() {
        assertThat(UnitRules.studySeconds(Unit.SWORDSMAN, 1)).isCloseTo(2727.0, offset(0.5))   // 45m27s
        assertThat(UnitRules.studySeconds(Unit.CATAPULT, 16)).isCloseTo(3134.0, offset(0.5))   // 52m14s
    }

    @Test
    fun `the ladder matches the design`() {
        assertThat(Unit.SPEARMAN.needsStudy).isFalse()
        assertThat(Unit.entries.filter { it.needsStudy }.map { it.academyLevel }).containsExactly(1, 2, 3, 5, 8, 10, 13, 16, 20)
        assertThat(Unit.entries.map { it.barracksLevel }).containsExactly(1, 3, 5, 5, 8, 10, 12, 15, 18, 20)
    }
}
