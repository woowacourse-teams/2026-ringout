package com.joon.ringout.platform

import com.joon.ringout.data.auth.local.connectAnalyticsUserId

interface IosAnalyticsUserIdSetter {
    fun setUserId(userId: String?)
}

/** Firebase 설정 직후 앱 시작 시 호출한다. 이벤트 파라미터와 별도의 SDK 사용자 ID다. */
fun initializeAnalyticsUserId(setter: IosAnalyticsUserIdSetter) {
    connectAnalyticsUserId(setter)
}
