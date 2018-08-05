package de.ploing.gitea

import java.io.File
import java.util.concurrent.TimeUnit

import com.typesafe.config.ConfigFactory
import okhttp3.OkHttpClient

object AddGithubMirrors {
  val client = new OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .build()

  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.parseFile(new File("gitea-mirror.conf"))
    val githubOps = new GithubOps(client)
    val giteaOps = new GiteaOps(client,
      config.getString("gitea.token"),
      config.getString("gitea.url"),
      if (config.hasPath("gitea.org")) {
        config.getString("gitea.org")
      } else {
        config.getString("gitea.user")
      },
      config.hasPath("gitea.user"))

    val giteaUserId = giteaOps.getUserId()

    val githubProjects = githubOps.getProjects(config.getString("github.username"),
      config.getBoolean("github.excludeForks"))
    val giteaProjects = giteaOps.getProjects()

    githubProjects.foreach { githubRepo =>
      if (!giteaProjects.contains(githubRepo.name)) {
        println(s"New clone: ${githubRepo.name} from ${githubRepo.url}")
        giteaOps.addMirror(githubRepo.url,
          giteaUserId,
          githubRepo.name,
          config.getBoolean("gitea.privateMirrors"))
      }
    }
  }
}
