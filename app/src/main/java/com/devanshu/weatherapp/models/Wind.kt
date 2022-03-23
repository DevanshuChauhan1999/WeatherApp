package com.devanshu.weatherapp.models

import java.io.Serializable

class Wind(
    val speed: Double,
    val deg: Double,
    val gust: Double
): Serializable
