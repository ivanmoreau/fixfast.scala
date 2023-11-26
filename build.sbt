// https://typelevel.org/sbt-typelevel/faq.html#what-is-a-base-version-anyway
ThisBuild / tlBaseVersion := "0.0" // your current series x.y

ThisBuild / organization := "com.ivmoreau"
ThisBuild / organizationName := "localservices"
ThisBuild / startYear := Some(2023)
ThisBuild / licenses := Seq(
  "MPL-2.0" -> url(
    "https://www.mozilla.org/media/MPL/2.0/index.f75d2927d3c1.txt"
  )
)
ThisBuild / developers := List(
  // your GitHub handle and name
  tlGitHubDev("ivanmoreau", "Iván Molina Rebolledo"),
  tlGitHubDev("fabianhjr", "Fabián Heredia Montiel")
)

// publish to s01.oss.sonatype.org (set to true to publish to oss.sonatype.org instead)
ThisBuild / tlSonatypeUseLegacyHost := false

// publish website from this branch
ThisBuild / tlSitePublishBranch := Some("main")

val Scala213 = "3.3.1"
ThisBuild / crossScalaVersions := Seq(Scala213, "3.3.1")
ThisBuild / scalaVersion := Scala213 // the default Scala

lazy val root = tlCrossRootProject.aggregate(core)

val tsecV = "0.5.0"

lazy val core = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("core"))
  .settings(
    name := "localservices",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core" % "2.10.0",
      "org.typelevel" %%% "cats-effect" % "3.5.2",
      "org.tpolecat" %%% "skunk-core" % "0.6.1",
      "org.http4s" %%% "http4s-dsl" % "0.23.23",
      "org.http4s" %%% "http4s-netty-server" % "0.5.11",
      "pt.kcry" %%% "blake3" % "3.1.1",
      "com.hubspot.jinjava" % "jinjava" % "2.7.1",
      "org.typelevel" %%% "log4cats-slf4j" % "2.6.0",
      "io.github.jmcardon" %% "tsec-http4s" % tsecV,
      "dev.profunktor" %% "redis4cats-effects" % "1.5.2",
      "com.disneystreaming" %% "weaver-cats" % "0.8.3" % Test,
      "org.slf4j" % "slf4j-reload4j" % "2.0.9" % Runtime
    ),
    testFrameworks += new TestFramework("weaver.framework.CatsEffect")
  )

lazy val docs = project.in(file("site")).enablePlugins(TypelevelSitePlugin)
