package com.apollographql.apollo.network.ws

import com.apollographql.apollo.ApolloWebSocketException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import okio.internal.commonAsUtf8ToByteArray

@ExperimentalCoroutinesApi
actual class ApolloWebSocketFactory(
    private val serverUrl: HttpUrl,
    private val headers: Map<String, String>,
    private val webSocketFactory: WebSocket.Factory
) : WebSocketFactory {

  actual constructor(
      serverUrl: String,
      headers: Map<String, String>
  ) : this(
      serverUrl = serverUrl.toHttpUrl(),
      headers = headers,
      webSocketFactory = OkHttpClient()
  )

  override suspend fun open(headers: Map<String, String>): WebSocketConnection {
    val messageChannel = Channel<ByteString>(Channel.BUFFERED)
    val webSocketConnectionDeferred = CompletableDeferred<WebSocket>()

    val request = Request.Builder()
        .url(serverUrl)
        .headers(this.headers.plus(headers).toHeaders())
        .build()

    val webSocket = webSocketFactory.newWebSocket(request = request, listener = object : WebSocketListener() {
      override fun onOpen(webSocket: WebSocket, response: Response) {
        if (!webSocketConnectionDeferred.complete(webSocket)) {
          webSocket.cancel()
        }
      }

      override fun onMessage(webSocket: WebSocket, text: String) {
        try {
          messageChannel.offer(text.commonAsUtf8ToByteArray().toByteString())
        } catch (e: Exception) {
          webSocket.cancel()
        }
      }

      override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        try {
          messageChannel.offer(bytes)
        } catch (e: Exception) {
          webSocket.cancel()
        }
      }

      override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        messageChannel.close(
            ApolloWebSocketException(
                message = "Web socket communication error",
                cause = t
            )
        )
      }

      override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        messageChannel.close()
      }
    })

    return WebSocketConnectionImpl(
        webSocket = webSocketConnectionDeferred.await(),
        messageChannel = messageChannel
    )
  }
}

@ExperimentalCoroutinesApi
private class WebSocketConnectionImpl(
    private val webSocket: WebSocket,
    private val messageChannel: Channel<ByteString> = Channel()
) : WebSocketConnection, ReceiveChannel<ByteString> by messageChannel {

  init {
    messageChannel.invokeOnClose {
      webSocket.close(code = 1000, reason = null)
    }
  }

  override fun send(data: ByteString) {
    if (!messageChannel.isClosedForReceive) {
      webSocket.send(data)
    }
  }

  override fun close() {
    messageChannel.close()
  }
}
