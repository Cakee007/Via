package dev.ujhhgtg.via.ui

import android.content.Context
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.settings.settingsColor
import dev.ujhhgtg.via.skins.SkinResources

/** mark.via.common.widget.n0.j(w) / t: title-row button before the reload control. */
class ResourceSnifferButton(context: Context) : ImageView(context) {
    // s6.H1 is the last reported availability, independent of n0.j visibility.
    private var lastAvailability = false
    init {
        layoutParams = LinearLayout.LayoutParams(context.dp(48f), -1)
        setPadding(context.dp(13f), 0, context.dp(13f), 0)
        setImageDrawable(SkinResources.drawable(context, R.drawable.resource_sniffer_button, "ic_resource"))
        setColorFilter(settingsColor(context, R.attr.viaSubtleColor, 0xff232323.toInt()))
        setBackgroundResource(R.drawable.circle_ripple)
        contentDescription = context.getString(R.string.resource_sniffer)
        visibility = View.GONE
    }

    /** a9 initializes without motion; s6.B/R5 animates only availability changes when flag 4 is on. */
    fun update(showAutomatically: Boolean, hasMedia: Boolean, animated: Boolean) {
        if (animated) {
            if (!showAutomatically || lastAvailability == hasMedia) return
            lastAvailability = hasMedia
        }
        val next = !showAutomatically || !hasMedia
        animate().cancel()
        if (!animated) {
            visibility = if (next) View.GONE else View.VISIBLE
            return
        }
        val motion = animate().translationX(if (next) width.toFloat() else 0f).alpha(if (next) 0f else 1f)
            .setDuration(150).setInterpolator(PathInterpolator(.2f, .2f, .8f, .8f))
        if (next) motion.withEndAction { visibility = View.GONE }
        else motion.withStartAction { visibility = View.VISIBLE }
        motion.start()
    }
}
