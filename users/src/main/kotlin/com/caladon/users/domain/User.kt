package com.caladon.users.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: Role,

    @Column(nullable = false, unique = true)
    var email: String,

    /** Password hash (bcrypt). Never plaintext. */
    @Column(name = "password", nullable = false)
    var passwordHash: String,

    @Column(nullable = false, unique = true)
    var nickname: String,
)
