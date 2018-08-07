package de.ploing.gitea

import org.scalatest._

class GithubApiSpec extends FlatSpec with Matchers {
  "The Links header" should "contain next and last" in {
    val linksHeader = """<https://api.github.com/user/60021/repos?page=2>; rel="next", <https://api.github.com/user/60021/repos?page=2>; rel="last""""
    val parseResult = GithubOps.parseLinkHeader(linksHeader)
    parseResult.keySet should contain allOf ("next", "last")
  }

  "An empty Links header" should "cause no error" in {
    val linksHeader = ""
    val parseResult = GithubOps.parseLinkHeader(linksHeader)
    parseResult shouldBe empty
  }
}
