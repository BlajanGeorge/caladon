package com.caladon.users

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration

/** Minimal Boot application so the users module can be integration-tested on its own. */
@SpringBootApplication(exclude = [UserDetailsServiceAutoConfiguration::class])
class UsersTestApplication
