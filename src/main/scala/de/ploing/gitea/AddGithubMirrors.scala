package de.ploing.gitea

import java.io.File

import com.typesafe.config.ConfigFactory
import okhttp3.{OkHttpClient, Request}
import play.api.libs.json.{JsArray, Json}


case class GithubRepo(name: String, url: String, fork: Boolean)

object AddGithubMirrors {
  val client = new OkHttpClient()

  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.parseFile(new File("gitea-mirror.conf"))
    val githubProjects = getGithubProjects(config.getString("github.username"))
    println(githubProjects.size)
  }

  def getGithubProjects(username: String) = {
    def getRecursive(url: String): Seq[GithubRepo] = {
      val (link, repos) = getGithubProjectPage(url)
      link.get("next").map { nextUrl =>
        repos ++ getRecursive(link("next"))
      }.getOrElse(repos)
    }

    getRecursive(s"https://api.github.com/users/${username}/repos")
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
}
