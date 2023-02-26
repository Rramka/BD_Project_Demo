ThisBuild / version := "1.8.2"

ThisBuild / scalaVersion := "2.13.10"

lazy val root = (project in file("."))
  .settings(
    name := "BD_Project_Demo"
  )

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % "3.3.2",
  "org.apache.spark" %% "spark-sql" % "3.3.2"
)




