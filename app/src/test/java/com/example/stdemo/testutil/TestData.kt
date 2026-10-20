package com.example.stdemo.testutil

import com.example.stdemo.domain.model.Brand
import com.example.stdemo.domain.model.Device

/** Builds a [Brand] with sensible defaults for tests. */
fun aBrand(
    id: String = "brand-1",
    name: String = "Acme",
    iconUrl: String? = "https://example.com/acme.png",
) = Brand(id = id, name = name, iconUrl = iconUrl)

/** Builds a [Device] with sensible defaults for tests. */
fun aDevice(
    id: String = "device-1",
    modelName: String = "Acme Switch",
    modelCode: String? = "AC-100",
    protocol: String? = "ZWAVE",
) = Device(id = id, modelName = modelName, modelCode = modelCode, protocol = protocol)
