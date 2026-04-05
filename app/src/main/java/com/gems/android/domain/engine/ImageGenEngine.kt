package com.gems.android.domain.engine

import android.graphics.Bitmap

/** Domain-layer interface for the on-device image generator (MobileDiffusion). */
interface ImageGenEngine {
    /** Generate a 512x512 Bitmap from [prompt]. */
    suspend fun generate(prompt: String): Bitmap
}
