package com.joon.ringout.presentation.common.component

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.tooling.preview.Preview
import com.joon.ringout.RingoutTheme

/** 다이얼로그 내부의 Foundation 및 Material 버튼에서 누름 효과만 제거한다. */
@Composable
fun DialogWithoutRipple(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalIndication provides NoDialogIndication,
        LocalRippleConfiguration provides null,
        content = content,
    )
}

private object NoDialogIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        object : Modifier.Node(), DrawModifierNode {
            override fun ContentDrawScope.draw() {
                drawContent()
            }
        }

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = 0
}

@Preview
@Composable
private fun DialogWithoutRipplePreview() {
    RingoutTheme {
        DialogWithoutRipple {
            TextButton(onClick = {}) {
                Text("확인")
            }
        }
    }
}
