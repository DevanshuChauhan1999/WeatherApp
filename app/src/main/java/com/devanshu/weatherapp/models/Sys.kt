package com.devanshu.weatherapp.models

import java.io.Serializable

data class Sys(
    val type: Int,
    val id: Double,
    val country: String,
    val sunrise: Int,
    val sunset: Int
): Serializable