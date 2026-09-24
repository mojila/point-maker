package com.pointmaker.app.domain

enum class Side {
    LEFT,
    RIGHT;

    fun opponent(): Side = if (this == LEFT) RIGHT else LEFT

    companion object {
        fun of(id: String): Side = if (id == "LEFT") LEFT else RIGHT
    }
}
