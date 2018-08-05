package de.ploing.gitea

import java.io.File
import java.util.concurrent.TimeUnit

import com.typesafe.config.ConfigFactory
import okhttp3.OkHttpClient

case class GithubRepo(name: String, url: String, fork: Boolean)

object AddGithubMirrors {
  val client = new OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .build()

  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.parseFile(new File("gitea-mirror.conf"))

    val githubProjects = GithubOps.getProjects(config.getString("github.username"),
      config.getBoolean("github.excludeForks"))
    println(githubProjects.size)

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

    val giteaUserId = GiteaOps.getUserId(giteaUserApiBaseUrl, giteaToken)
    println(giteaUserId)

    val giteaProjects = GiteaOps.getProjects(giteaRepoUrl, giteaToken)

    GiteaOps.addMirror(s"${giteaBaseUrl}api/v1/repos/migrate", giteaToken,
      "git://github.com/Skyr/scmversion-gradle-plugin.git",
      giteaUserId,
      "scmversion-gradle-plugin",
      config.getBoolean("gitea.privateMirrors"))
  }
}
