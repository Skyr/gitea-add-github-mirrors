package de.ploing.gitea

import java.io.File
import java.util.concurrent.TimeUnit

import com.typesafe.config.ConfigFactory
import okhttp3.{MediaType, OkHttpClient, Request, RequestBody}
import play.api.libs.json._
import play.api.libs.functional.syntax._

case class GithubRepo(name: String, url: String, fork: Boolean)

object AddGithubMirrors {
  val client = new OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .build()

  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.parseFile(new File("gitea-mirror.conf"))
    /*
    val githubProjects = getGithubProjects(config.getString("github.username"),
      config.getBoolean("github.excludeForks"))
    println(githubProjects.size)
    */

    val giteaToken = config.getString("gitea.token")
    val giteaBaseUrl = if (config.getString("gitea.url").endsWith("/")) {
      config.getString("gitea.url")
    } else {
      s"${config.getString("gitea.url")}/"
    }
    val giteaUserApiBaseUrl = if (config.hasPath("gitea.org")) {
      s"${giteaBaseUrl}api/v1/orgs/${config.getString("gitea.org")}"
    } else {
      s"${giteaBaseUrl}api/v1/users/${config.getString("gitea.user")}"
    }
    val giteaRepoUrl = s"${giteaUserApiBaseUrl}/repos"

    val giteaUserId = getGiteaUserId(giteaUserApiBaseUrl, giteaToken)
    println(giteaUserId)

    val giteaProjects = getGiteaProjects(giteaRepoUrl, giteaToken)

    addGiteaMirror(s"${giteaBaseUrl}api/v1/repos/migrate", giteaToken,
      "git://github.com/Skyr/scmversion-gradle-plugin.git",
      giteaUserId,
      "scmversion-gradle-plugin",
      config.getBoolean("gitea.privateMirrors"))
  }

  def getGithubProjects(username: String, excludeForks: Boolean) = {
    def getRecursive(url: String, result: Seq[GithubRepo] = Seq()): Seq[GithubRepo] = {
      val (link, repos) = getGithubProjectPage(url)
      link.get("next") match {
        case None => result ++ repos
        case Some(nextUrl) => getRecursive(nextUrl, result ++ repos)
      }
    }

    getRecursive(s"https://api.github.com/users/${username}/repos")
      .filter { repo =>
        !excludeForks || !repo.fork
      }
  }

  /**
    * Load and parse paginated list of repositories
    *
    * @param url
    * @return touple: Parsed headers, parsed repo data
    */
  def getGithubProjectPage(url: String) = {
    val request = new Request.Builder()
      .header("Accept", "application/vnd.github.v3+json")
      .url(url)
      .get()
      .build()
    val response = client.newCall(request).execute()
    val linkHeader = parseGithubLinkHeader(response.header("Link"))
    val elements = Json.parse(response.body().byteStream()).validate[JsArray].get.value
    val repoData = elements.map { entry =>
      GithubRepo((entry \ "name").as[String], (entry \ "git_url").as[String], (entry \ "fork").as[Boolean])
    }
    (linkHeader, repoData)
  }

  /**
    * Parses the Github Link header
    *
    * @param header
    * @return a map from link rel to the link
    */
  def parseGithubLinkHeader(header: String) = {
    val pattern = """<([^>]*)>; rel="([^"]*)"""".r
    header
      .split(", *")
      .map { s =>
        val pattern(url, rel) = s
        (rel, url)
      }
      .foldLeft(Map[String, String]()) { (map, link) =>
        map + link
      }
  }


  def getGiteaProjects(url: String, token: String) = {
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


  def getGiteaUserId(url: String, token: String) = {
    val request = new Request.Builder()
      .header("Authorization", s"token ${token}")
      .url(url)
      .get()
      .build()
    val response = client.newCall(request).execute()
    val elements = Json.parse(response.body().byteStream()).as[JsObject]
    elements("id").as[Long]
  }

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

  def addGiteaMirror(apiUrl: String, token: String, cloneUrl: String, userId: Long, repoName: String, privateRepo: Boolean): Unit = {
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
