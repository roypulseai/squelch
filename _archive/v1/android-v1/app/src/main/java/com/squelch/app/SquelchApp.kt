package com.squelch.app

import android.app.Application
import com.squelch.app.crypto.IdentityManager
import com.squelch.app.db.AppDatabase
import com.squelch.app.mesh.MeshEngine
import com.squelch.app.transport.nfc.HceNdefService

/** Application root: identity, database and the mesh engine live here. */
class SquelchApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var identityManager: IdentityManager
        private set

    lateinit var engine: MeshEngine
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.get(this)
        identityManager = IdentityManager(this)
        engine = MeshEngine(this, identityManager, database)
        HceNdefService.identity = identityManager.identity
    }

    fun startMesh() = engine.start()

    fun stopMesh() = engine.stop()
}
