package com.lumalink.g100d.transport.transfer

import com.lumalink.g100d.core.data.TransferRepository
import com.lumalink.g100d.transport.session.CameraSession

/**
 * Implemented by the Application so the service can reach the same repository
 * and the current camera session without a global service locator.
 */
interface TransferServiceHost {
    val transferRepository: TransferRepository
    val sessionProvider: () -> CameraSession?
}
