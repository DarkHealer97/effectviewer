package com.effectviewer.model

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,          // 0.0 → 1.0 (1.0 = appena nato)
    var maxLife: Float,
    var size: Float,
    var alpha: Float,
    var color: Int,
    var rotation: Float = 0f,
    var rotationSpeed: Float = 0f
)
