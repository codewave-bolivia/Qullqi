package com.codewave.qullqi.feature.auth.register

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.codewave.qullqi.designsystem.Q
import com.codewave.qullqi.designsystem.QScreen
import com.codewave.qullqi.designsystem.QSpacing
import com.codewave.qullqi.designsystem.components.QFlowTopBar

/** Common layout of a registration step (UI_GUIDELINES §2.3): bar, question title, one help line. */
@Composable
internal fun StepScaffold(
    step: Int,
    title: String,
    help: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    QScreen(modifier) {
        QFlowTopBar(step, TOTAL_STEPS, onBack)
        Spacer(Modifier.height(QSpacing.x28))
        Text(title, style = Q.type.titleFlow, color = Q.colors.tx)
        Spacer(Modifier.height(QSpacing.x8))
        Text(help, style = Q.type.bodySm, color = Q.colors.tx2)
        Spacer(Modifier.height(QSpacing.x28))
        content()
    }
}

internal const val TOTAL_STEPS = 4
