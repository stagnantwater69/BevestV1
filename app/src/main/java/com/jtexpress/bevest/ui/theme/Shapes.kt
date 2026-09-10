package com.jtexpress.bevest.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * BeVest shape system.
 *
 * Two families, and which one a component gets carries meaning:
 *
 *  - **Rounded** — neutral content. Lists, settings, forms, anything the user browses.
 *  - **Chamfered** — safety-critical surfaces. The cut corner comes from industrial
 *    signage and machine guarding, where a bevel marks a plate you are meant to read
 *    rather than a panel you look past. Status readouts, alerts, and the emergency
 *    banner get it; nothing else does.
 *
 * Restricting the chamfer is the point. If every card had one it would be decoration;
 * because only the safety surfaces do, the shape itself tells you where to look.
 */

/**
 * A rounded rectangle with the top-left corner cut flat.
 *
 * Implemented as a [Shape] rather than a `GenericShape` because the cut and the radius
 * are declared in [Dp] and only [Density] can convert them — a `GenericShape` builder
 * has no density in scope, so it would have to hardcode pixels and would render at the
 * wrong size on anything but one screen.
 */
data class ChamferedShape(
    val cut: Dp = 14.dp,
    val radius: Dp = Radius.lg,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val c = with(density) { cut.toPx() }.coerceAtMost(min(size.width, size.height) / 2f)
        val r = with(density) { radius.toPx() }.coerceAtMost(min(size.width, size.height) / 2f)

        val path = Path().apply {
            // Start past the cut on the top edge and travel clockwise.
            moveTo(c, 0f)
            lineTo(size.width - r, 0f)
            quadraticBezierTo(size.width, 0f, size.width, r)
            lineTo(size.width, size.height - r)
            quadraticBezierTo(size.width, size.height, size.width - r, size.height)
            lineTo(r, size.height)
            quadraticBezierTo(0f, size.height, 0f, size.height - r)
            // Up the left edge, then the bevel closes back to the start.
            lineTo(0f, c)
            close()
        }
        return Outline.Generic(path)
    }
}

/** Convenience factory so call sites read as a shape, not a constructor. */
fun chamferedShape(cut: Dp = 14.dp, radius: Dp = Radius.lg): Shape =
    ChamferedShape(cut, radius)

/**
 * One shape per component role, so a screen never invents its own.
 * Reach for these instead of `RoundedCornerShape(...)` at a call site.
 */
object BevestShapes {

    // -- neutral content --
    /** Standard content card, list row, settings group. */
    val card: Shape = RoundedCornerShape(Radius.lg)

    /** Nested surface inside a card — a metric block, a form group. */
    val inner: Shape = RoundedCornerShape(Radius.md)

    /** Small square-ish holder: icon tiles, swatches, badges. */
    val tile: Shape = RoundedCornerShape(Radius.sm)

    /** Buttons and text inputs. Slightly tighter than a card so controls read as controls. */
    val control: Shape = RoundedCornerShape(Radius.md)

    /** Chips and anything that should read as a token. */
    val pill: Shape = RoundedCornerShape(Radius.pill)

    /** Avatars, dots, status lamps. */
    val round: Shape = CircleShape

    /** Bottom sheets and dialogs — rounded on top only, flat against the edge. */
    val sheet: Shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl)

    // -- safety-critical --
    /** Live status readouts and the site status panel. */
    val status: Shape = chamferedShape(cut = 16.dp, radius = Radius.lg)

    /** Alert and incident surfaces — a smaller cut so rows stay calm in a list. */
    val alert: Shape = chamferedShape(cut = 12.dp, radius = Radius.md)

    /** The emergency banner. The deepest cut in the app; nothing else uses it. */
    val emergency: Shape = chamferedShape(cut = 22.dp, radius = Radius.lg)
}

/**
 * Fed to [androidx.compose.material3.MaterialTheme] so the Material components the app
 * still uses off the shelf — dialogs, menus, FABs, chips — pick up the same radii
 * instead of their defaults. Without this, an `AlertDialog` quietly reintroduces stock
 * Material shapes into an otherwise consistent screen.
 */
val MaterialShapes = androidx.compose.material3.Shapes(
    extraSmall = RoundedCornerShape(Radius.sm),
    small = RoundedCornerShape(Radius.md),
    medium = RoundedCornerShape(Radius.md),
    large = RoundedCornerShape(Radius.lg),
    extraLarge = RoundedCornerShape(Radius.xl),
)
