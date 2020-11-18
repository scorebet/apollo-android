package com.apollographql.apollo

import com.apollographql.apollo.api.ApolloExperimental
import com.apollographql.apollo.api.ExecutionContext
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.interceptor.ApolloRequest
import com.apollographql.apollo.interceptor.ApolloResponse
import com.apollographql.apollo.interceptor.BearerTokenInterceptor
import com.apollographql.apollo.mock.MockQuery
import com.apollographql.apollo.mock.TestTokenProvider
import com.apollographql.apollo.network.HttpExecutionContext
import com.apollographql.apollo.network.NetworkTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.single
import okio.ByteString.Companion.encodeUtf8
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@ApolloExperimental
class OauthInterceptorTest {
  class AuthenticatedNetworkTransport : NetworkTransport {
    companion object {
      const val VALID_ACCESS_TOKEN1 = "VALID_ACCESS_TOKEN1"
      const val VALID_ACCESS_TOKEN2 = "VALID_ACCESS_TOKEN2"
      const val INVALID_ACCESS_TOKEN = "INVALID_ACCESS_TOKEN"
    }

    override fun <D : Operation.Data> execute(request: ApolloRequest<D>, executionContext: ExecutionContext): Flow<ApolloResponse<D>> {
      val authorization = executionContext[HttpExecutionContext.Request]?.headers?.get("Authorization")

      return flow {
        when (authorization) {
          "Bearer $VALID_ACCESS_TOKEN1",
          "Bearer $VALID_ACCESS_TOKEN2" -> {
            emit(
                ApolloResponse(
                    requestUuid = request.requestUuid,
                    response = request.operation.parse("{\"data\":{\"name\":\"MockQuery\"}}".encodeUtf8()),
                    executionContext = ExecutionContext.Empty
                )
            )
          }
          else -> {
            throw ApolloHttpException(
                message = "Http request failed with status code `401`",
                statusCode = 401,
                headers = emptyMap<String, String>()
            )
          }
        }
      }
    }
  }


  private fun apolloClient(currentAccessToken: String, newAccessToken: String): ApolloClient {
    val networkTransport = AuthenticatedNetworkTransport()
    return ApolloClient(
        networkTransport = networkTransport,
        interceptors = listOf(BearerTokenInterceptor(TestTokenProvider(
            currentAccessToken,
            newAccessToken
        )))
    )
  }

  @Test
  fun `valid access token succeeds`() {
    val response = runBlocking {
      apolloClient(AuthenticatedNetworkTransport.VALID_ACCESS_TOKEN1,
          AuthenticatedNetworkTransport.VALID_ACCESS_TOKEN2)
          .query(MockQuery()).execute().single()
    }

    assertNotNull(response.data)
    assertEquals(expected = MockQuery.Data("{\"data\":{\"name\":\"MockQuery\"}}"), actual = response.data)
  }

  @Test
  fun `invalid access token fails`() {
    val result = runBlocking {
      kotlin.runCatching {
        apolloClient(AuthenticatedNetworkTransport.INVALID_ACCESS_TOKEN,
            AuthenticatedNetworkTransport.INVALID_ACCESS_TOKEN)
            .query(MockQuery()).execute().single()
      }
    }

    assertTrue(result.isFailure)
    result.onFailure { e ->
      assertTrue(e is ApolloException)
    }
  }

  @Test
  fun `refresh access token succeeds`() {
    val response = runBlocking {
      apolloClient(AuthenticatedNetworkTransport.INVALID_ACCESS_TOKEN,
          AuthenticatedNetworkTransport.VALID_ACCESS_TOKEN2)
          .query(MockQuery()).execute().single()
    }

    assertNotNull(response.data)
    assertEquals(expected = MockQuery.Data("{\"data\":{\"name\":\"MockQuery\"}}"), actual = response.data)
  }
}
