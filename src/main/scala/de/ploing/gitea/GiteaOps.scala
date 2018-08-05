package de.ploing.gitea

import de.ploing.gitea.AddGithubMirrors.client
import okhttp3.{MediaType, Request, RequestBody}
import play.api.libs.functional.syntax.unlift
import play.api.libs.functional.syntax._
import play.api.libs.json._


object GiteaOps {
  def getUserId(url: String, token: String) = {
    val request = new Request.Builder()
      .header("Authorization", s"token ${token}")
      .url(url)
      .get()
      .build()
    val response = client.newCall(request).execute()
    val elements = Json.parse(response.body().byteStream()).as[JsObject]
    elements("id").as[Long]
  }


  def getProjects(url: String, token: String) = {
    val request = new Request.Builder()
      .header("Authorization", s"token ${token}")
      .url(url)
      .get()
      .build()
    val response = client.newCall(request).execute()
    val elements = Json.parse(response.body().byteStream()).as[JsArray].value
    elements.map { entry =>
      (entry \ "name").as[String]
    }
  }


  def addMirror(apiUrl: String, token: String, cloneUrl: String, userId: Long, repoName: String, privateRepo: Boolean): Unit = {
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

    val data = MigrateJson(null, null, cloneUrl, "", true, privateRepo, repoName, userId)
    val jsonData = Json.toJson(data).toString()
    println(Json.prettyPrint(Json.toJson(data)))
    val request = new Request.Builder()
      .header("Authorization", s"token ${token}")
      .url(apiUrl)
      .post(RequestBody.create(MediaType.get("application/json"), jsonData))
      .build()
    val response = client.newCall(request).execute()
    println(response.code())
    println(response.body().string())
  }
}
