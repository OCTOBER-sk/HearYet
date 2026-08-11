package com.hearyet.app.core.model

import kotlinx.serialization.Serializable

/**
 * The four dismissible guide cards shown on Home. Dismissal is persisted in
 * [ApplicationPreferences.dismissedGuideCards] so a dismissed card never
 * reappears.
 */
@Serializable
enum class GuideCardType {
    WATCH,
    HEAR,
    CREATE_SESSION,
    JOIN_SESSION,
}
