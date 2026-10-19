package com.maxrave.simpmusic.brand

/**
 * Beatland fork: switches for upstream behaviours this personal fork does not want.
 *
 * Hand-written (NOT generated, unlike [Brand]). Flip a value to bring the upstream behaviour back.
 * See `docs/beatland/DECISIONS.md` (D-007, D-009).
 */
object ForkPolicy {
    /**
     * The original author's promo dialogs: the review / "star on GitHub" prompt, the blog promo and the
     * kotlin-footguns star prompt. They speak for the upstream author and link to upstream pages.
     */
    const val UPSTREAM_PROMOS_ENABLED = false

    /**
     * The update checker (automatic and manual). It only knows upstream's GitHub releases / F-Droid
     * listing, so it would offer to install the official SimpMusic instead of this fork.
     */
    const val UPSTREAM_UPDATE_CHECK_ENABLED = false
}
