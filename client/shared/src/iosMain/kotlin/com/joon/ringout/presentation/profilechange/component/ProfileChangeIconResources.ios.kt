package com.joon.ringout.presentation.profilechange.component

import org.jetbrains.compose.resources.DrawableResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.mypage_arrow_left
import ringout.shared.generated.resources.nickname_change_invalid
import ringout.shared.generated.resources.nickname_change_user
import ringout.shared.generated.resources.nickname_change_valid
import ringout.shared.generated.resources.profile_image_edit_icon_dark
import ringout.shared.generated.resources.profile_image_edit_icon_light
import ringout.shared.generated.resources.user_profile_image_icon

internal actual val ProfileChangeBackIconResource: DrawableResource =
    Res.drawable.mypage_arrow_left
internal actual val ProfileChangeUserIconResource: DrawableResource =
    Res.drawable.nickname_change_user
internal actual val ProfileChangeValidIconResource: DrawableResource =
    Res.drawable.nickname_change_valid
internal actual val ProfileChangeInvalidIconResource: DrawableResource =
    Res.drawable.nickname_change_invalid
internal actual val ProfileImagePlaceholderResource: DrawableResource =
    Res.drawable.user_profile_image_icon
internal actual val ProfileImageEditDarkIconResource: DrawableResource =
    Res.drawable.profile_image_edit_icon_dark
internal actual val ProfileImageEditLightIconResource: DrawableResource =
    Res.drawable.profile_image_edit_icon_light
