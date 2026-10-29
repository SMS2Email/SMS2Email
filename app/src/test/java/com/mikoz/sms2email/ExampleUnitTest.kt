package io.github.sms2email.sms2email

import android.content.Context
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  private class RecordingSender : MessageSender {
    var lastSender: String? = null
    var lastContent: String? = null
    var callCount: Int = 0

    override fun send(
        context: Context,
        sender: String,
        content: String,
    ) {
      lastSender = sender
      lastContent = content
      callCount++
    }
  }

  @Test
  fun delegatingSender_routesToEmailProviderByDefault() {
    val emailSender = RecordingSender()
    val httpSender = RecordingSender()
    val delegating =
        DelegatingMessageSender(
            emailSender = emailSender,
            httpSender = httpSender,
            providerResolver = { ForwardingProvider.FORWARDING_PROVIDER_EMAIL },
        )

    delegating.send(ContextWrapperStub(), "+1234567890", "Your code is 1234")

    assertEquals(1, emailSender.callCount)
    assertEquals("+1234567890", emailSender.lastSender)
    assertEquals("Your code is 1234", emailSender.lastContent)
    assertEquals(0, httpSender.callCount)
  }

  @Test
  fun delegatingSender_routesToHttpProviderWhenSelected() {
    val emailSender = RecordingSender()
    val httpSender = RecordingSender()
    val delegating =
        DelegatingMessageSender(
            emailSender = emailSender,
            httpSender = httpSender,
            providerResolver = { ForwardingProvider.FORWARDING_PROVIDER_HTTP },
        )

    delegating.send(ContextWrapperStub(), "+1987654321", "OTP: 9999")

    assertEquals(0, emailSender.callCount)
    assertEquals(1, httpSender.callCount)
    assertEquals("+1987654321", httpSender.lastSender)
    assertEquals("OTP: 9999", httpSender.lastContent)
  }

  @Test
  fun buildJsonPayload_escapesSpecialCharacters() {
    val json = HttpSender.buildJsonPayload("+1555000", "Hello \"world\"\nLine 2", 1700000000000L)
    assertEquals(
        "{\"sender\":\"+1555000\",\"message\":\"Hello \\\"world\\\"\\nLine 2\",\"timestamp\":1700000000000}",
        json,
    )
  }

  @Test
  fun parseAuthHeader_handlesBearerAndCustomHeaders() {
    assertNull(HttpSender.parseAuthHeader("   "))
    assertEquals(
        "Authorization" to "Bearer secret123",
        HttpSender.parseAuthHeader("Bearer secret123"),
    )
    assertEquals(
        "X-API-Key" to "my-key-456",
        HttpSender.parseAuthHeader("X-API-Key: my-key-456"),
    )
  }

  @Test
  fun postJson_sendsPostRequestWithHeadersAndBody() {
    ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { serverSocket ->
      var requestLine: String? = null
      val headers = mutableMapOf<String, String>()
      var body: String? = null

      val serverThread = Thread {
        serverSocket.accept().use { client ->
          val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))
          requestLine = reader.readLine()
          var contentLength = 0
          while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            val idx = line.indexOf(':')
            if (idx > 0) {
              val key = line.substring(0, idx).trim().lowercase()
              val value = line.substring(idx + 1).trim()
              headers[key] = value
              if (key == "content-length") {
                contentLength = value.toIntOrNull() ?: 0
              }
            }
          }
          val buf = CharArray(contentLength)
          var readTotal = 0
          while (readTotal < contentLength) {
            val n = reader.read(buf, readTotal, contentLength - readTotal)
            if (n == -1) break
            readTotal += n
          }
          body = String(buf, 0, readTotal)

          val response =
              "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK"
          client.getOutputStream().write(response.toByteArray(StandardCharsets.UTF_8))
          client.getOutputStream().flush()
        }
      }
      serverThread.start()

      val port = serverSocket.localPort
      val sender = HttpSender()
      val payload = HttpSender.buildJsonPayload("+12345", "Test message", 42L)

      sender.postJson("http://127.0.0.1:$port/webhook", payload, "X-API-Key: secret-token")
      serverThread.join(5000)

      assertTrue(requestLine.orEmpty().startsWith("POST /webhook "))
      assertEquals("secret-token", headers["x-api-key"])
      assertEquals("application/json; charset=UTF-8", headers["content-type"])
      assertEquals(payload, body)
    }
  }

  @Test
  fun postJson_throwsOnNon2xxResponse() {
    ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { serverSocket ->
      val serverThread = Thread {
        serverSocket.accept().use { client ->
          val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))
          while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
          }
          val response =
              "HTTP/1.1 401 Unauthorized\r\nContent-Length: 12\r\nConnection: close\r\n\r\nUnauthorized"
          client.getOutputStream().write(response.toByteArray(StandardCharsets.UTF_8))
          client.getOutputStream().flush()
        }
      }
      serverThread.start()

      val port = serverSocket.localPort
      val sender = HttpSender()
      val payload = HttpSender.buildJsonPayload("+12345", "Test message", 42L)

      assertThrows(IOException::class.java) {
        sender.postJson("http://127.0.0.1:$port/fail", payload, "")
      }
      serverThread.join(5000)
    }
  }

  @Test
  fun customApplication_providesConfiguredMessageSender() {
    val customSender = RecordingSender()
    val app = CustomApplication().apply { messageSender = customSender }
    val context =
        object : android.content.ContextWrapper(null) {
          override fun getApplicationContext(): Context = app
        }

    val resolved = CustomApplication.getMessageSender(context)
    resolved.send(context, "+10000", "App-level sender test")

    assertEquals(1, customSender.callCount)
    assertEquals("+10000", customSender.lastSender)
    assertEquals("App-level sender test", customSender.lastContent)
  }

  private class ContextWrapperStub : android.content.ContextWrapper(null)
}
