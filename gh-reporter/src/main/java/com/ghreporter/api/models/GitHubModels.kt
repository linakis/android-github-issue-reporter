package com.ghreporter.api.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Request body for creating a GitHub issue.
 */
@JsonClass(generateAdapter = true)
data class CreateIssueRequest(
    @Json(name = "title") val title: String,
    @Json(name = "body") val body: String,
    @Json(name = "labels") val labels: List<String>? = null,
    @Json(name = "assignees") val assignees: List<String>? = null,
    @Json(name = "milestone") val milestone: Int? = null
)

/**
 * Response from creating a GitHub issue.
 */
@JsonClass(generateAdapter = true)
data class IssueResponse(
    @Json(name = "id") val id: Long,
    @Json(name = "number") val number: Int,
    @Json(name = "title") val title: String,
    @Json(name = "body") val body: String?,
    @Json(name = "state") val state: String,
    @Json(name = "html_url") val htmlUrl: String,
    @Json(name = "user") val user: GitHubUser,
    @Json(name = "labels") val labels: List<Label>?,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String
)

/**
 * GitHub user information.
 */
@JsonClass(generateAdapter = true)
data class GitHubUser(
    @Json(name = "id") val id: Long,
    @Json(name = "login") val login: String,
    @Json(name = "avatar_url") val avatarUrl: String?,
    @Json(name = "html_url") val htmlUrl: String,
    @Json(name = "name") val name: String? = null,
    @Json(name = "email") val email: String? = null
)

/**
 * GitHub issue label.
 */
@JsonClass(generateAdapter = true)
data class Label(
    @Json(name = "id") val id: Long,
    @Json(name = "name") val name: String,
    @Json(name = "color") val color: String,
    @Json(name = "description") val description: String?
)

/**
 * Request body for creating a GitHub Gist.
 */
@JsonClass(generateAdapter = true)
data class CreateGistRequest(
    @Json(name = "description") val description: String,
    @Json(name = "public") val public: Boolean = false,
    @Json(name = "files") val files: Map<String, GistFile>
)

/**
 * A file within a Gist.
 */
@JsonClass(generateAdapter = true)
data class GistFile(
    @Json(name = "content") val content: String
)

/**
 * Response from creating a GitHub Gist.
 */
@JsonClass(generateAdapter = true)
data class GistResponse(
    @Json(name = "id") val id: String,
    @Json(name = "html_url") val htmlUrl: String,
    @Json(name = "description") val description: String?,
    @Json(name = "public") val public: Boolean,
    @Json(name = "files") val files: Map<String, GistFileResponse>,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String,
    @Json(name = "owner") val owner: GitHubUser?
)

/**
 * File information in a Gist response.
 */
@JsonClass(generateAdapter = true)
data class GistFileResponse(
    @Json(name = "filename") val filename: String,
    @Json(name = "type") val type: String?,
    @Json(name = "language") val language: String?,
    @Json(name = "raw_url") val rawUrl: String?,
    @Json(name = "size") val size: Int
)

/**
 * GitHub repository information.
 */
@JsonClass(generateAdapter = true)
data class Repository(
    @Json(name = "id") val id: Long,
    @Json(name = "name") val name: String,
    @Json(name = "full_name") val fullName: String,
    @Json(name = "private") val private: Boolean,
    @Json(name = "html_url") val htmlUrl: String,
    @Json(name = "description") val description: String?,
    @Json(name = "owner") val owner: GitHubUser
)

/**
 * GitHub API error response.
 */
@JsonClass(generateAdapter = true)
data class GitHubError(
    @Json(name = "message") val message: String,
    @Json(name = "documentation_url") val documentationUrl: String?
)
