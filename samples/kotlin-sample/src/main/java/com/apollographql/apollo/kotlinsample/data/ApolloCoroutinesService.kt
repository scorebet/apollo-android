package com.apollographql.apollo.kotlinsample.data

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.coroutines.toDeferred
import com.apollographql.apollo.kotlinsample.GithubRepositoriesQuery
import com.apollographql.apollo.kotlinsample.GithubRepositoryCommitsQuery
import com.apollographql.apollo.kotlinsample.GithubRepositoryDetailQuery
import com.apollographql.apollo.kotlinsample.type.OrderDirection
import com.apollographql.apollo.kotlinsample.type.PullRequestState
import com.apollographql.apollo.kotlinsample.type.RepositoryOrderField
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * An implementation of a [GitHubDataSource] that shows how we can use coroutines to make our apollo requests.
 */
class ApolloCoroutinesService(
    apolloClient: ApolloClient,
    private val processContext: CoroutineContext = Dispatchers.IO,
    private val resultContext: CoroutineContext = Dispatchers.Main
) : GitHubDataSource(apolloClient) {
  private var job: Job? = null

  override fun fetchRepositories() {
    val repositoriesQuery = GithubRepositoriesQuery.builder()
        .repositoriesCount(50)
        .orderBy(RepositoryOrderField.UPDATED_AT)
        .orderDirection(OrderDirection.DESC)
        .build()

    job = CoroutineScope(processContext).launch {
      try {
        val response = apolloClient.query(repositoriesQuery).toDeferred().await()
        withContext(resultContext) {
          repositoriesSubject.onNext(mapRepositoriesResponseToRepositories(response))
        }
      } catch (e: Exception) {
        exceptionSubject.onNext(e)
      }
    }
  }

  override fun fetchRepositoryDetail(repositoryName: String) {
    val repositoryDetailQuery = GithubRepositoryDetailQuery.builder()
        .name(repositoryName)
        .pullRequestStates(listOf(PullRequestState.OPEN))
        .build()

    job = CoroutineScope(processContext).launch {
      try {
        val response = apolloClient.query(repositoryDetailQuery).toDeferred().await()

        withContext(resultContext) {
          repositoryDetailSubject.onNext(response)
        }
      } catch (e: Exception) {
        exceptionSubject.onNext(e)
      }
    }
  }

  override fun fetchCommits(repositoryName: String) {
    val commitsQuery = GithubRepositoryCommitsQuery.builder()
        .name(repositoryName)
        .build()

    job = CoroutineScope(processContext).launch {
      try {
        val response = apolloClient.query(commitsQuery).toDeferred().await()
        val headCommit = response.data()?.viewer()?.repository()?.ref()?.target() as? GithubRepositoryCommitsQuery.AsCommit
        val commits = headCommit?.history()?.edges().orEmpty()

        withContext(resultContext) {
          commitsSubject.onNext(commits)
        }
      } catch (e: Exception) {
        exceptionSubject.onNext(e)
      }
    }
  }

  override fun cancelFetching() {
    job?.cancel()
  }
}