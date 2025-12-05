package com.programminghut.realtime_object

import android.app.Application
import com.cloudinary.android.MediaManager

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val config = mutableMapOf<String, String>()
        config["cloud_name"] = "drh4bob8r"
        config["api_key"] = "784677881121569"
        config["api_secret"] = "3E770td6Une4DRwuXe5Hqzn8R8Y"
        MediaManager.init(this, config)
    }
}
