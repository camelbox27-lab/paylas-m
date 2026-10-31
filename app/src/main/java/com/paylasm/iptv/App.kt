package com.paylasm.iptv

import android.app.Application
import com.paylasm.iptv.data.Repo

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
    }
}
