package com.hearyet.app.core.media.network

import com.hearyet.app.core.media.network.clients.FtpClient
import com.hearyet.app.core.media.network.clients.SmbClient
import com.hearyet.app.core.media.network.clients.WebDavClient
import com.hearyet.app.core.model.NetworkConnection
import com.hearyet.app.core.model.NetworkProtocol

object NetworkClientFactory {
    fun create(connection: NetworkConnection): NetworkClient = when (connection.protocol) {
        NetworkProtocol.SMB -> SmbClient(connection)
        NetworkProtocol.FTP -> FtpClient(connection)
        NetworkProtocol.WEBDAV -> WebDavClient(connection)
    }
}
