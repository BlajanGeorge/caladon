package com.caladon.users.repository

import com.caladon.users.domain.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun findByEmailIgnoreCase(email: String): User?
    fun existsByEmailIgnoreCase(email: String): Boolean
    fun existsByNicknameIgnoreCase(nickname: String): Boolean
}
