package com.joon.ringout.alarm

import kotlin.random.Random

actual fun newAlarmId(): String = "alarm-${Random.nextInt(1, Int.MAX_VALUE)}"
