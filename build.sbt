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

ThisBuild / tlFatalWarnings := false
ThisBuild / tlCiHeaderCheck := false

ThisBuild / tlCiScalafixCheck := false
ThisBuild / tlCiDocCheck := false
ThisBuild / tlCiMimaBinaryIssueCheck := false
ThisBuild / tlCiDependencyGraphJob := false

val Scala331 = "3.3.1"
ThisBuild / crossScalaVersions := Seq(Scala331)
ThisBuild / scalaVersion := Scala331 // the default Scala

ThisBuild / assemblyMergeStrategy := {
  case PathList("javax", "servlet", xs @ _*)        => MergeStrategy.last
  case PathList("javax", "activation", xs @ _*)     => MergeStrategy.last
  case PathList("org", "apache", xs @ _*)           => MergeStrategy.last
  case PathList("com", "google", xs @ _*)           => MergeStrategy.last
  case PathList("com", "esotericsoftware", xs @ _*) => MergeStrategy.last
  case PathList("com", "codahale", xs @ _*)         => MergeStrategy.last
  case PathList("com", "yammer", xs @ _*)           => MergeStrategy.last
  case "about.html"                                 => MergeStrategy.rename
  case "META-INF/ECLIPSEF.RSA"                      => MergeStrategy.last
  case "META-INF/mailcap"                           => MergeStrategy.last
  case "META-INF/mimetypes.default"                 => MergeStrategy.last
  case "plugin.properties"                          => MergeStrategy.last
  case "log4j.properties"                           => MergeStrategy.last
  case "META-INF/io.netty.versions.properties"      => MergeStrategy.last
  case "META-INF/versions/9/module-info.class"      => MergeStrategy.last
  case x =>
    val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
    oldStrategy(x)
}

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
    testFrameworks += new TestFramework("weaver.framework.CatsEffect"),
    assembly / assemblyJarName := s"${name.value}.jar"
  )
