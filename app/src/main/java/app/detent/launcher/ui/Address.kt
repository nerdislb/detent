package app.detent.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import app.detent.launcher.Ease
import app.detent.launcher.LauncherState
import app.detent.launcher.Palette
import app.detent.launcher.parseLiveUrl
import kotlinx.coroutines.delay
import kotlin.math.max

/** The sheet where the address of the laptop theme is typed or pasted. */
@Composable
fun AddressSheet(s: LauncherState, pal: Palette) {
    val u = LocalUnits.current
    val move = (40 * u.k).toInt()
    AnimatedVisibility(
        s.address,
        enter = fadeIn(tween(300)) + slideInVertically(tween(400, easing = Ease)) { move },
        exit = fadeOut(tween(300)) + slideOutVertically(tween(400, easing = Ease)) { move },
    ) {
        val focus = remember { FocusRequester() }
        val kb = LocalSoftwareKeyboardController.current
        var text by remember { mutableStateOf(s.addressStart()) }
        var tried by remember { mutableStateOf(false) }
        val valid = parseLiveUrl(text) != null
        LaunchedEffect(Unit) {
            delay(80)
            runCatching { focus.requestFocus() }
            kb?.show()
        }
        val save = {
            if (valid) s.saveAddress(text) else tried = true
        }
        Column(Modifier.fillMaxSize().background(pal.sheet).tap { }.imePadding()) {
            Spacer(Modifier.height(u.dp(max(60f, u.top + 20f))))
            Txt("laptop address", 26f, pal.fg, Modifier.padding(horizontal = u.dp(28f)), weight = FontWeight.Light, spacing = -.03f)
            Txt(
                "the https address of the theme file your computer publishes. detent reads it while it is in front, and the theme follows.",
                13f, pal.muted2, Modifier.padding(start = u.dp(28f), end = u.dp(28f), top = u.dp(10f), bottom = u.dp(18f)),
            )
            BasicTextField(
                value = text,
                onValueChange = { text = it; tried = false },
                modifier = Modifier.padding(horizontal = u.dp(28f)).fillMaxWidth().height(u.dp(48f)).focusRequester(focus),
                singleLine = true,
                textStyle = textStyle(u, 15f, pal.fg),
                cursorBrush = SolidColor(pal.accent),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { save() }),
                decorationBox = { inner ->
                    Box(
                        Modifier.fillMaxSize().drawBehind {
                            val y = size.height - u.k / 2
                            drawLine(pal.rim2, Offset(0f, y), Offset(size.width, y), strokeWidth = u.k)
                        },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (text.isEmpty()) Txt("https://host/theme.json", 15f, pal.muted, maxLines = 1)
                        inner()
                    }
                },
            )
            Txt(
                if (tried && !valid) "that is not an https address" else "",
                12f, pal.accent, Modifier.padding(start = u.dp(28f), top = u.dp(10f)),
            )
            Row(
                Modifier.padding(horizontal = u.dp(28f), vertical = u.dp(18f)),
                horizontalArrangement = Arrangement.spacedBy(u.dp(10f)),
            ) {
                Chip("save", valid, pal, { save() }, weight = FontWeight.Medium)
                if (s.hasAddress) Chip("switch off", false, pal, { s.clearAddress() })
                Chip("cancel", false, pal, { s.closeAddress() })
            }
        }
    }
}
