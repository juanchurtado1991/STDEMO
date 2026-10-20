package com.example.stdemo.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A device type (product) that a [Brand] publishes in the SmartThings catalog.
 *
 * @property id Unique SmartThings product identifier.
 * @property modelName Marketing model name shown as the item title.
 * @property modelCode Manufacturer model code or SKU, or `null` when not provided.
 * @property protocol Connectivity protocol reported by the catalog (for example `ZWAVE`,
 * `ZIGBEE`), or `null` when unknown.
 */
@Parcelize
data class Device(
    val id: String,
    val modelName: String,
    val modelCode: String?,
    val protocol: String?,
) : Parcelable
