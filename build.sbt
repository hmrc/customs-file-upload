import AppDependencies._
import sbt.Keys._
import sbt.Tests.{Group, SubProcess}
import sbt._
import uk.gov.hmrc.DefaultBuildSettings.{addTestReportOption, targetJvm}
import uk.gov.hmrc.PublishingSettings._
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.gitstamp.GitStampPlugin._
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
import uk.gov.hmrc.versioning.SbtGitVersioning

import scala.language.postfixOps

name := "customs-file-upload"
scalaVersion := "2.12.10"
targetJvm := "jvm-1.8"

lazy val allResolvers = resolvers ++= Seq(
  Resolver.bintrayRepo("hmrc", "releases"),
  Resolver.jcenterRepo
)

lazy val ComponentTest = config("component") extend Test
lazy val CdsIntegrationComponentTest = config("it") extend Test

val testConfig = Seq(ComponentTest, CdsIntegrationComponentTest, Test)

def forkedJvmPerTestConfig(tests: Seq[TestDefinition], packages: String*): Seq[Group] =
  tests.groupBy(_.name.takeWhile(_ != '.')).filter(packageAndTests => packages contains packageAndTests._1) map {
    case (packg, theTests) =>
      Group(packg, theTests, SubProcess(ForkOptions()))
  } toSeq

lazy val testAll = TaskKey[Unit]("test-all")
lazy val allTest = Seq(testAll := (test in ComponentTest)
  .dependsOn((test in CdsIntegrationComponentTest).dependsOn(test in Test)).value)

lazy val microservice = (project in file("."))
  .enablePlugins(PlayScala)
  .enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning)
  .enablePlugins(SbtDistributablesPlugin)
  .disablePlugins(sbt.plugins.JUnitXmlReportPlugin)
  .enablePlugins(SbtArtifactory)
  .configs(testConfig: _*)
  .settings(
    commonSettings,
    unitTestSettings,
    integrationComponentTestSettings,
    playPublishingSettings,
    allTest,
    scoverageSettings,
    allResolvers
  )
  .settings(majorVersion := 0)

def onPackageName(rootPackage: String): String => Boolean = {
  testName => testName startsWith rootPackage
}

lazy val unitTestSettings =
  inConfig(Test)(Defaults.testTasks) ++
    Seq(
      testOptions in Test := Seq(Tests.Filter(onPackageName("unit"))),
      testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oD"),
      unmanagedSourceDirectories in Test := Seq((baseDirectory in Test).value / "test"),
      addTestReportOption(Test, "test-reports")
    )

lazy val integrationComponentTestSettings =
  inConfig(CdsIntegrationComponentTest)(Defaults.testTasks) ++
    Seq(
      testOptions in CdsIntegrationComponentTest := Seq(Tests.Filter(integrationComponentTestFilter)),
      fork in CdsIntegrationComponentTest := false,
      parallelExecution in CdsIntegrationComponentTest := false,
      addTestReportOption(CdsIntegrationComponentTest, "int-comp-test-reports"),
      testGrouping in CdsIntegrationComponentTest := forkedJvmPerTestConfig((definedTests in Test).value, "integration", "component")
    )

lazy val commonSettings: Seq[Setting[_]] = publishingSettings ++ gitStampSettings

lazy val playPublishingSettings: Seq[sbt.Setting[_]] = Seq(credentials += SbtCredentials) ++
  publishAllArtefacts

lazy val scoverageSettings: Seq[Setting[_]] = Seq(
  coverageExcludedPackages := List(
      "<empty>"
      ,"uk\\.gov\\.hmrc\\.customs\\.file\\.upload\\.model\\..*"
      ,"uk\\.gov\\.hmrc\\.customs\\.file\\.upload\\.views\\..*"
      ,".*(Reverse|AuthService|BuildInfo|Routes).*"
    ).mkString(";"),
  coverageMinimum := 96,
  coverageFailOnMinimum := true,
  coverageHighlighting := true,
  parallelExecution in Test := false
)

def integrationComponentTestFilter(name: String): Boolean = (name startsWith "integration") || (name startsWith "component")
def unitTestFilter(name: String): Boolean = name startsWith "unit"

scalastyleConfig := baseDirectory.value / "project" / "scalastyle-config.xml"

val compileDependencies = Seq(customsApiCommon, simpleReactiveMongo)

val testDependencies = Seq(scalaTestPlusPlay, wireMock, mockito, customsApiCommonTests, reactiveMongoTest)

unmanagedResourceDirectories in Compile += baseDirectory.value / "public"

libraryDependencies ++= compileDependencies ++ testDependencies

evictionWarningOptions in update := EvictionWarningOptions.default.withWarnTransitiveEvictions(false)
