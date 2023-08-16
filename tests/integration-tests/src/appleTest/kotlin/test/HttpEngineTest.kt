package test

import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.exception.ApolloNetworkException
import com.apollographql.apollo3.integration.normalizer.HeroNameQuery
import com.apollographql.apollo3.mockserver.MockServer
import com.apollographql.apollo3.mockserver.enqueue
import com.apollographql.apollo3.mpp.currentTimeMillis
import com.apollographql.apollo3.network.http.DefaultHttpEngine
import com.apollographql.apollo3.network.http.get
import com.apollographql.apollo3.testing.internal.runTest
import platform.Foundation.NSError
import platform.Foundation.NSURLErrorCannotConnectToHost
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLErrorTimedOut
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class HttpEngineTest {
  @Test
  fun canReadNSError() = runTest {
    /**
     * Try to trigger an error wihtout having to wait 60s on a timeout.
     * Hopefully Port 1 is closed on most machines. If that's not enough, we'd need to find an instrumented server
     */
    val apolloClient = ApolloClient.Builder().serverUrl("https://127.0.0.1:1/graphql").build()

    val response = apolloClient.query(HeroNameQuery()).execute()
    val apolloNetworkException = response.exception
    assertNotNull(apolloNetworkException)
    assertIs<ApolloNetworkException>(apolloNetworkException)

    val cause = apolloNetworkException.platformCause
    // assertIs doesn't work with Obj-C classes so we rely on `check` instead
    // assertIs<NSError>(cause)
    check(cause is NSError)

    assertEquals(cause.domain, NSURLErrorDomain)
    assertEquals(cause.code, NSURLErrorCannotConnectToHost)
  }

  @Test
  fun connectTimeoutIsWorking() = runTest {
    val mockServer = MockServer(2_000)
    // Enqueue a trivial response to not crash in the mockServer
    mockServer.enqueue("")

    assertTimeout(mockServer)
  }

  @Test
  fun readTimeoutIsWorking() = runTest {
    val mockServer = MockServer(0)
    // Enqueue a response with a 2 seconds delay
    mockServer.enqueue("", 2_000)

    assertTimeout(mockServer)
  }

  private suspend fun assertTimeout(mockServer: MockServer) {
    val engine = DefaultHttpEngine(timeoutMillis = 1_000)

    val before = currentTimeMillis()
    try {
      println("Before " + currentTimeMillis())
      engine.get(mockServer.url()).execute()
      println("After " + currentTimeMillis())
      fail("We expected an exception")
    } catch (e: ApolloNetworkException) {
      val platformCause = e.platformCause
      println(platformCause)
      assertTrue(platformCause is NSError)
      assertEquals(platformCause.domain, NSURLErrorDomain)
      assertEquals(platformCause.code, NSURLErrorTimedOut)
    }
    val after = currentTimeMillis()

    /**
     * Check that we timed out at ~1 second
     */
    assertTrue(after - before >= 1000)
    assertTrue(after - before <= 1500)
    mockServer.stop()
  }
}
