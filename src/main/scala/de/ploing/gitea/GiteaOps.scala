package de.ploing.gitea

import okhttp3.{MediaType, OkHttpClient, Request, RequestBody}
import play.api.libs.functional.syntax.{unlift, _}
import play.api.libs.json._

import scala.util.Try


class GiteaOps(client: OkHttpClient, token: String, _giteaBaseUrl: String, idenitityName: String, isUser: Boolean) {
  private val baseUrl = if (_giteaBaseUrl.endsWith("/")) {
    _giteaBaseUrl
  } else {
    s"${_giteaBaseUrl}/"
  }
  private val userApiBaseUrl = if (isUser) {
    s"${baseUrl}api/v1/users/${idenitityName}"
  } else {
    s"${baseUrl}api/v1/orgs/${idenitityName}"
  }

  def getUserId() = {
    val request = new Request.Builder()
      .header("Authorization", s"token ${token}")
      .url(userApiBaseUrl)
      .get()
      .build()
    val response = client.newCall(request).execute()
    val elements = Json.parse(response.body().byteStream()).as[JsObject]
    elements("id").as[Long]
  }


  def getProjects() = {
    val repolistApiUrl = s"${userApiBaseUrl}/repos"
    val request = new Request.Builder()
      .header("Authorization", s"token ${token}")
      .url(repolistApiUrl)
      .get()
      .build()
    val response = client.newCall(request).execute()
    val elements = Json.parse(response.body().byteStream()).as[JsArray].value
    elements.map { entry =>
      (entry \ "name").as[String]
    }
  }


  def addMirror(cloneUrl: String, userId: Long, repoName: String, description: String, privateRepo: Boolean): Unit = {
    case class MigrateJson(authPassword: String, authUsername: String, cloneAddr: String, description: String,
                           mirror: Boolean, privateRepo: Boolean, repoName: String, uid: Long)
    implicit val migrateJsonWrites: Writes[MigrateJson] = (
      (JsPath \ "auth_password").write[String] and
        (JsPath \ "auth_username").write[String] and
        (JsPath \ "clone_addr").write[String] and
        (JsPath \ "description").write[String] and
        (JsPath \ "mirror").write[Boolean] and
        (JsPath \ "private").write[Boolean] and
        (JsPath \ "repo_name").write[String] and
        (JsPath \ "uid").write[Long]
      )(unlift(MigrateJson.unapply))

    val migrateApiUrl = s"${baseUrl}api/v1/repos/migrate"
    val data = MigrateJson(null, null, cloneUrl, description, true, privateRepo, repoName, userId)
    val jsonData = Json.toJson(data).toString()
    val request = new Request.Builder()
      .header("Authorization", s"token ${token}")
      .url(migrateApiUrl)
      .post(RequestBody.create(MediaType.get("application/json"), jsonData))
      .build()
    val response = client.newCall(request).execute()
    if (!response.isSuccessful) {
      val errorMessage = Try(Json.parse(response.body().byteStream()))
        .toOption
        .flatMap { json =>
          (json \ "message").validate[String].asOpt
        }
      println(s"Error ${response.code()} creating gitea mirror repo: ${errorMessage.getOrElse(response.body().string())}")
    }
  }
}
