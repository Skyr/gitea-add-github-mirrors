package de.ploing.gitea

import okhttp3.{OkHttpClient, Request}
import play.api.libs.json.{JsArray, Json}


case class GithubRepo(name: String, url: String, description: String, fork: Boolean)

class GithubOps(client: OkHttpClient) {
  def getProjects(username: String, excludeForks: Boolean) = {
    def getRecursive(url: String, result: Seq[GithubRepo] = Seq()): Seq[GithubRepo] = {
      val (link, repos) = getProjectPage(url)
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
  def getProjectPage(url: String) = {
    val request = new Request.Builder()
      .header("Accept", "application/vnd.github.v3+json")
      .url(url)
      .get()
      .build()
    val response = client.newCall(request).execute()
    val linkHeader = GithubOps.parseLinkHeader(response.header("Link"))
    val elements = Json.parse(response.body().byteStream()).validate[JsArray].get.value
    val repoData = elements.map { entry =>
      GithubRepo((entry \ "name").as[String], (entry \ "git_url").as[String],
        (entry \ "description").as[String], (entry \ "fork").as[Boolean])
    }
    (linkHeader, repoData)
  }
}


object GithubOps {
  /**
    * Parses the Github Link header
    *
    * @param header
    * @return a map from link rel to the link
    */
  def parseLinkHeader(header: String) = {
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