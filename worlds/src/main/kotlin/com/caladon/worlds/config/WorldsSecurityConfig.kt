package com.caladon.worlds.config

import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity

/** Enables `@PreAuthorize`, used to restrict the admin controllers to administrators. */
@Configuration
@EnableMethodSecurity
class WorldsSecurityConfig
