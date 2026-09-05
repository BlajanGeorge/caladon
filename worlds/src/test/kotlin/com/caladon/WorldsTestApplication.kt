package com.caladon

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration

/** Boot application for the worlds module tests; scans `com.caladon` so the users module is included. */
@SpringBootApplication(exclude = [UserDetailsServiceAutoConfiguration::class])
class WorldsTestApplication
