package com.joon.ringout.alarm

import platform.Foundation.NSUUID

actual fun newAlarmId(): String = NSUUID().UUIDString
