package com.lumalink.g100d

import android.content.Context
import com.lumalink.g100d.core.data.LumaLinkDatabase
import com.lumalink.g100d.core.data.TransferRepository
import com.lumalink.g100d.core.settings.SettingsRepository
import com.lumalink.g100d.transport.session.ConnectionManager
import com.lumalink.g100d.transport.session.WifiScanner

class AppContainer(context: Context) {
    val applicationContext: Context = context.applicationContext

    val settingsRepository: SettingsRepository = SettingsRepository(applicationContext)

    val connectionManager: ConnectionManager by lazy { ConnectionManager(applicationContext) }

    val wifiScanner: WifiScanner by lazy { WifiScanner(applicationContext) }

    val database: LumaLinkDatabase by lazy { LumaLinkDatabase.create(applicationContext) }

    val transferRepository: TransferRepository by lazy {
        TransferRepository(database.transferDao())
    }
}
