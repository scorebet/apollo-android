package com.apollographql.apollo.network

import com.apollographql.apollo.api.ApolloExperimental
import com.apollographql.apollo.api.ExecutionContext
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.interceptor.ApolloRequest
import com.apollographql.apollo.interceptor.ApolloResponse
import kotlinx.coroutines.flow.Flow

@ApolloExperimental
interface NetworkTransport {

  fun <D : Operation.Data> execute(request: ApolloRequest<D>, executionContext: ExecutionContext): Flow<ApolloResponse<D>>
}
