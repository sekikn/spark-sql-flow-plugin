/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql

import java.io.File
import java.net.URI

import org.apache.spark.{SparkConf, TestUtils}
import org.apache.spark.sql.catalyst.plans.SQLHelper
import org.apache.spark.sql.catalyst.util.{fileToString, stringToFile}
import org.apache.spark.sql.internal.SQLConf
import org.apache.spark.sql.test.SharedSparkSession
import org.apache.spark.util.Utils

class SQLFlowTestSuite extends QueryTest with SharedSparkSession with SQLHelper
  with SQLQueryTestHelper {

  private val regenerateGoldenFiles: Boolean = System.getenv("SPARK_GENERATE_GOLDEN_FILES") == "1"

  private def getWorkspaceFilePath(first: String, more: String*) = {
    if (!(sys.props.contains("spark.test.home") || sys.env.contains("SPARK_HOME"))) {
      fail("spark.test.home or SPARK_HOME is not set.")
    }
    val sparkHome = sys.props.getOrElse("spark.test.home", sys.env("SPARK_HOME"))
    java.nio.file.Paths.get(sparkHome, first +: more: _*)
  }

  protected val baseResourcePath = {
    getWorkspaceFilePath("src", "test", "resources", "sql-flow-tests").toFile
  }

  protected val inputFilePath = new File(baseResourcePath, "inputs").getAbsolutePath
  protected val goldenFilePath = new File(baseResourcePath, "results").getAbsolutePath

  protected val validFileExtensions = ".sql"

  protected override def sparkConf: SparkConf = super.sparkConf
    // Fewer shuffle partitions to speed up testing.
    .set(SQLConf.SHUFFLE_PARTITIONS, 4)

  // Create all the test cases.
  listTestCases.foreach(createScalaTestCase)

  protected case class TestCase(name: String, inputFile: String, resultFilePrefix: String) {
    val dotFile: String = s"$resultFilePrefix.dot"
    val contractedDotFile: String = resultFilePrefix.replaceAll("\\.sql", "-contracted.sql.dot")
    val pngFile: String = s"$resultFilePrefix.png"
    val contractedPngFile: String = resultFilePrefix.replaceAll("\\.sql", "-contracted.sql.png")
  }

  protected def ignoreList: Set[String] = Set(
    // TODO: t Temporarily ignored because the test fails in GitHub Actions
    "group-by-filter.sql"
  )

  protected def createScalaTestCase(testCase: TestCase): Unit = {
    if (ignoreList.contains(testCase.name)) {
      ignore(testCase.name) {
        runTest(testCase)
      }
    } else {
      test(testCase.name) {
        runTest(testCase)
      }
    }
  }

  /** Run a test case. */
  protected def runTest(testCase: TestCase): Unit = {
    def splitWithSemicolon(seq: Seq[String]) = {
      seq.mkString("\n").split("(?<=[^\\\\]);")
    }

    def splitCommentsAndCodes(input: String) = {
      input.split("\n").partition(_.trim.startsWith("--"))
    }

    // List of SQL queries to run
    val input = fileToString(new File(testCase.inputFile))
    val (_, code) = splitCommentsAndCodes(input)
    val queries = splitWithSemicolon(code).toSeq.map(_.trim).filter(_ != "").toSeq
      // Fix misplacement when comment is at the end of the query.
      .map(_.split("\n").filterNot(_.startsWith("--")).mkString("\n")).map(_.trim).filter(_ != "")

    runQueries(queries, testCase)
  }

  // If the `dot` command exists, convert the generated dot files into png ones
  private lazy val tryGeneratePngFile = if (TestUtils.testCommandAvailable("dot")) {
    (src: String, dst: String) => {
      val commands = Seq("bash", "-c", s"dot -Tpng $src > $dst")
      BlockingLineStream(commands)
    }
  } else {
    logWarning(s"`dot` command not available")
    (src: String, dst: String) => {}
  }

  protected def runQueries(queries: Seq[String], testCase: TestCase): Unit = {
    // Create a local SparkSession to have stronger isolation between different test cases.
    // This does not isolate catalog changes.
    val localSparkSession = spark.newSession()

    // Runs the given SQL quries and gets data linearge as a Graphviz-formatted text
    queries.foreach(localSparkSession.sql(_).collect)

    val fileHeader = s"// Automatically generated by ${getClass.getSimpleName}\n\n"
    val flowString = SQLFlow().catalogToSQLFlow(localSparkSession)
    val contractedFlowString = SQLContractedFlow().catalogToSQLFlow(localSparkSession)

    if (regenerateGoldenFiles) {
      stringToFile(new File(testCase.dotFile), s"$fileHeader$flowString")
      stringToFile(new File(testCase.contractedDotFile), s"$fileHeader$contractedFlowString")
      tryGeneratePngFile(testCase.dotFile, testCase.pngFile)
      tryGeneratePngFile(testCase.contractedDotFile, testCase.contractedPngFile)
    }

    def checkSQLFlowString(goldenFile: String, flowString: String): Unit = {
      // Read back the golden file.
      def normalize(s: String) = s.replaceAll("_\\d+", "_x").replaceAll("#\\d+", "#x")
      val expectedOutput = fileToString(new File(goldenFile)).replaceAll(fileHeader, "")
      assert(normalize(expectedOutput) === normalize(flowString))
    }

    withClue(s"${testCase.name}${System.lineSeparator()}") {
      checkSQLFlowString(testCase.dotFile, flowString)
      checkSQLFlowString(testCase.contractedDotFile, contractedFlowString)
    }
  }

  protected lazy val listTestCases: Seq[TestCase] = {
    listFilesRecursively(new File(inputFilePath)).flatMap { file =>
      val resultFilePrefix = file.getAbsolutePath.replace(inputFilePath, goldenFilePath)
      val absPath = file.getAbsolutePath
      val testCaseName = absPath.stripPrefix(inputFilePath).stripPrefix(File.separator)
      TestCase(testCaseName, absPath, resultFilePrefix) :: Nil
    }
  }

  /** Returns all the files (not directories) in a directory, recursively. */
  protected def listFilesRecursively(path: File): Seq[File] = {
    val (dirs, files) = path.listFiles().partition(_.isDirectory)
    // Filter out test files with invalid extensions such as temp files created
    // by vi (.swp), Mac (.DS_Store) etc.
    val filteredFiles = files.filter(_.getName.endsWith(validFileExtensions))
    filteredFiles ++ dirs.flatMap(listFilesRecursively)
  }

  /** Load built-in test tables into the SparkSession. */
  private def createTestTables(session: SparkSession): Unit = {
    import session.implicits._

    // Before creating test tables, deletes orphan directories in warehouse dir
    Seq("testdata").foreach { dirName =>
      val f = new File(new URI(s"${conf.warehousePath}/$dirName"))
      if (f.exists()) {
        Utils.deleteRecursively(f)
      }
    }

    (1 to 10).map(i => (i, i.toString)).toDF("key", "value")
      .repartition(1)
      .write
      .format("parquet")
      .saveAsTable("testdata")
  }

  private def removeTestTables(session: SparkSession): Unit = {
    session.sql("DROP TABLE IF EXISTS testdata")
  }
  override def beforeAll(): Unit = {
    super.beforeAll()
    createTestTables(spark)
  }

  override def afterAll(): Unit = {
    try {
      removeTestTables(spark)
    } finally {
      super.afterAll()
    }
  }
}