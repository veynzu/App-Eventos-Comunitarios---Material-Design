package com.eventoscomunitarios.app.core.data

import com.eventoscomunitarios.app.domain.model.User

object FakeUsers {
    val users: List<User> = listOf(
        User(
            email = "ana.torres@correo.com",
            password = "12345678"
        )
    )
}
