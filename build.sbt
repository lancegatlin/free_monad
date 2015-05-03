scalaVersion := "2.11.1"

organization := "net.s_mach"

name := "string"

version := "1.0.0"

scalacOptions ++= Seq("-feature","-unchecked", "-deprecation")

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  "com.chuusai" %% "shapeless" % "2.2.0-RC4",
  "org.scalatest" % "scalatest_2.11" % "2.2.0" % "test"
)