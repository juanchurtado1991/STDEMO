package com.example.stdemo.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A device manufacturer listed in the SmartThings catalog.
 *
 * @property id Unique SmartThings brand identifier, used to look up the brand's devices.
 * @property name Human-readable brand name shown in the UI.
 * @property iconUrl Remote URL of the brand logo, or `null` when the catalog has none.
 */
@Parcelize
data class Brand(
    val id: String,
    val name: String,
    val iconUrl: String?,
) : Parcelable
