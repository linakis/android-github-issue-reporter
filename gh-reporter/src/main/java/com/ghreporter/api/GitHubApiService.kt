package com.ghreporter.api

import com.ghreporter.api.models.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit interface for GitHub REST API.
 */
interface GitHubApiService {

    /**
     * Get the authenticated user's information.
     */
    @GET("user")
    suspend fun getAuthenticatedUser(): Response<GitHubUser>

    /**
     * Create a new issue in a repository.
     *
     * @param owner Repository owner (user or organization)
     * @param repo Repository name
     * @param request Issue creation request body
     */
    @POST("repos/{owner}/{repo}/issues")
    suspend fun createIssue(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: CreateIssueRequest
    ): Response<IssueResponse>

    /**
     * Get a specific issue.
     *
     * @param owner Repository owner
     * @param repo Repository name
     * @param issueNumber Issue number
     */
    @GET("repos/{owner}/{repo}/issues/{issue_number}")
    suspend fun getIssue(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("issue_number") issueNumber: Int
    ): Response<IssueResponse>

    /**
     * List issues in a repository.
     *
     * @param owner Repository owner
     * @param repo Repository name
     * @param state Issue state filter (open, closed, all)
     * @param perPage Number of results per page
     * @param page Page number
     */
    @GET("repos/{owner}/{repo}/issues")
    suspend fun listIssues(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "open",
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1
    ): Response<List<IssueResponse>>

    /**
     * Create a new Gist.
     *
     * @param request Gist creation request body
     */
    @POST("gists")
    suspend fun createGist(
        @Body request: CreateGistRequest
    ): Response<GistResponse>

    /**
     * Get repository information.
     *
     * @param owner Repository owner
     * @param repo Repository name
     */
    @GET("repos/{owner}/{repo}")
    suspend fun getRepository(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<Repository>

    /**
     * Check if the authenticated user has access to a repository.
     * Returns 404 if no access.
     *
     * @param owner Repository owner
     * @param repo Repository name
     */
    @GET("repos/{owner}/{repo}")
    suspend fun checkRepositoryAccess(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<Repository>

    /**
     * List available labels in a repository.
     *
     * @param owner Repository owner
     * @param repo Repository name
     */
    @GET("repos/{owner}/{repo}/labels")
    suspend fun listLabels(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<List<Label>>
}
