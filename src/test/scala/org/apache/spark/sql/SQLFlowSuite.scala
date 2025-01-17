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

import org.apache.spark.TestUtils
import org.apache.spark.sql.catalyst.util._
import org.apache.spark.sql.test.{SharedSparkSession, SQLTestUtils}

class SQLFlowSuite extends QueryTest with SharedSparkSession with SQLTestUtils {

  private def getOutputAsString(f: => Unit): String = {
    val output = new java.io.ByteArrayOutputStream()
    Console.withOut(output) { f }
    output.toString
  }

  private def checkOutputString(actual: String, expected: String): Unit = {
    def normalize(s: String) = s.replaceAll("_\\d+", "_x").replaceAll(" ", "").replaceAll("\n", "")
    assert(normalize(actual) == normalize(expected),
      s"`$actual` didn't match an expected string `$expected`")
  }

  test("df.printAsSQLFlow") {
    import SQLFlow._
    val flowString = getOutputAsString {
      val df = sql("SELECT k, sum(v) FROM VALUES (1, 2), (3, 4) t(k, v) GROUP BY k")
      df.debugPrintAsSQLFlow()
    }
    checkOutputString(flowString,
      s"""
         |digraph {
         |  graph [pad="0.5", nodesep="0.5", ranksep="2", fontname="Helvetica"];
         |  node [shape=plain]
         |  rankdir=LR;
         |
         |  "Aggregate_x" [label=<
         |  <table border="1" cellborder="0" cellspacing="0">
         |    <tr><td bgcolor="lightgray" port="nodeName"><i>Aggregate_x</i></td></tr>
         |    <tr><td port="0">k</td></tr>
         |  <tr><td port="1">sum(v)</td></tr>
         |  </table>>];
         |
         |
         |  "LocalRelation_x" [label=<
         |  <table border="1" cellborder="0" cellspacing="0">
         |    <tr><td bgcolor="lightpink" port="nodeName"><i>LocalRelation_x</i></td></tr>
         |    <tr><td port="0">k</td></tr>
         |  <tr><td port="1">v</td></tr>
         |  </table>>];
         |
         |  "LocalRelation_x":0 -> "Aggregate_x":0;
         |  "LocalRelation_x":1 -> "Aggregate_x":1;
         |}
       """.stripMargin)
  }

  test("SQLFlow.printAsSQLFlow") {
    withTempView("t") {
      sql(s"""
           |CREATE OR REPLACE TEMPORARY VIEW t AS
           |  SELECT k, sum(v) FROM VALUES (1, 2), (3, 4) t(k, v) GROUP BY k
         """.stripMargin)

      val flowString = getOutputAsString {
        SQLFlow.debugPrintAsSQLFlow()
      }
      checkOutputString(flowString,
        s"""
           |digraph {
           |  graph [pad="0.5", nodesep="0.5", ranksep="2", fontname="Helvetica"];
           |  node [shape=plain]
           |  rankdir=LR;
           |
           |
           |  "Aggregate_1133" [label=<
           |  <table border="1" cellborder="0" cellspacing="0">
           |    <tr><td bgcolor="lightgray" port="nodeName"><i>Aggregate_1133</i></td></tr>
           |    <tr><td port="0">k</td></tr>
           |  <tr><td port="1">sum(v)</td></tr>
           |  </table>>];
           |
           |
           |  "t" [label=<
           |  <table border="1" cellborder="0" cellspacing="0">
           |    <tr><td bgcolor="lightyellow" port="nodeName"><i>t</i></td></tr>
           |    <tr><td port="0">k</td></tr>
           |  <tr><td port="1">sum(v)</td></tr>
           |  </table>>];
           |
           |  "Aggregate_1133":0 -> "t":0;
           |  "Aggregate_1133":1 -> "t":1;
           |  "t":0 -> "Aggregate_1133":0;
           |  "t":1 -> "Aggregate_1133":1;
           |}
         """.stripMargin)
    }
  }

  test("df.saveAsSQLFlow") {
    withTempDir { dirPath =>
      import SQLFlow._
      val df = sql("SELECT k, sum(v) FROM VALUES (1, 2), (3, 4) t(k, v) GROUP BY k")

      df.saveAsSQLFlow(s"${dirPath.getAbsolutePath}/d")
      val flowString = fileToString(new File(s"${dirPath.getAbsolutePath}/d/sqlflow.dot"))
      checkOutputString(flowString,
        s"""
           |digraph {
           |  graph [pad="0.5", nodesep="0.5", ranksep="2", fontname="Helvetica"];
           |  node [shape=plain]
           |  rankdir=LR;
           |
           |
           |  "Aggregate_x" [label=<
           |  <table border="1" cellborder="0" cellspacing="0">
           |    <tr><td bgcolor="lightgray" port="nodeName"><i>Aggregate_x</i></td></tr>
           |    <tr><td port="0">k</td></tr>
           |  <tr><td port="1">sum(v)</td></tr>
           |  </table>>];
           |
           |
           |  "LocalRelation_x" [label=<
           |  <table border="1" cellborder="0" cellspacing="0">
           |    <tr><td bgcolor="lightpink" port="nodeName"><i>LocalRelation_x</i></td></tr>
           |    <tr><td port="0">k</td></tr>
           |  <tr><td port="1">v</td></tr>
           |  </table>>];
           |
           |  "LocalRelation_x":0 -> "Aggregate_x":0;
           |  "LocalRelation_x":1 -> "Aggregate_x":1;
           |}
         """.stripMargin)
    }
  }

  test("SQLFlow.saveAsSQLFlow") {
    withTempView("t") {
      withTempDir { dirPath =>
        sql(s"""
             |CREATE OR REPLACE TEMPORARY VIEW t AS
             |  SELECT k, sum(v) FROM VALUES (1, 2), (3, 4) t(k, v) GROUP BY k
           """.stripMargin)

        SQLFlow.saveAsSQLFlow(s"${dirPath.getAbsolutePath}/d")
        val flowString = fileToString(new File(s"${dirPath.getAbsolutePath}/d/sqlflow.dot"))
        checkOutputString(flowString,
          s"""
             |digraph {
             |  graph [pad="0.5", nodesep="0.5", ranksep="2", fontname="Helvetica"];
             |  node [shape=plain]
             |  rankdir=LR;
             |
             |
             |  "Aggregate_x" [label=<
             |  <table border="1" cellborder="0" cellspacing="0">
             |    <tr><td bgcolor="lightgray" port="nodeName"><i>Aggregate_x</i></td></tr>
             |    <tr><td port="0">k</td></tr>
             |  <tr><td port="1">sum(v)</td></tr>
             |  </table>>];
             |
             |
             |  "t" [label=<
             |  <table border="1" cellborder="0" cellspacing="0">
             |    <tr><td bgcolor="lightyellow" port="nodeName"><i>t</i></td></tr>
             |    <tr><td port="0">k</td></tr>
             |  <tr><td port="1">sum(v)</td></tr>
             |  </table>>];
             |
             |  "Aggregate_x":0 -> "t":0;
             |  "Aggregate_x":1 -> "t":1;
             |  "t":0 -> "Aggregate_x":0;
             |  "t":1 -> "Aggregate_x":1;
             |}
           """.stripMargin)
      }
    }
  }

  test("path already exists") {
    withTempDir { dir =>
      import SQLFlow._
      val errMsg1 = intercept[AnalysisException] {
        spark.range(1).saveAsSQLFlow(dir.getAbsolutePath)
      }.getMessage
      assert(errMsg1.contains(" already exists"))
      val errMsg2 = intercept[AnalysisException] {
        SQLFlow.saveAsSQLFlow(dir.getAbsolutePath)
      }.getMessage
      assert(errMsg2.contains(" already exists"))
    }
  }

  test("invalid image format") {
    withTempDir { dir =>
      import SQLFlow._
      val errMsg1 = intercept[AnalysisException] {
        spark.range(1).saveAsSQLFlow(s"${dir.getAbsolutePath}/d", format = "invalid")
      }.getMessage
      assert(errMsg1.contains("Invalid image format: invalid"))
      val errMsg2 = intercept[AnalysisException] {
        SQLFlow.saveAsSQLFlow(s"${dir.getAbsolutePath}/d", format = "invalid")
      }.getMessage
      assert(errMsg2.contains("Invalid image format: invalid"))
    }
  }

  test("image data generation") {
    assume(TestUtils.testCommandAvailable("dot"))
    withTempDir { dirPath =>
      import SQLFlow._
      val df = sql("SELECT k, sum(v) FROM VALUES (1, 2), (3, 4) t(k, v) GROUP BY k")

      SQLFlow.validImageFormatSet.foreach { format =>
        val outputPath = s"${dirPath.getAbsolutePath}/$format"
        df.saveAsSQLFlow(outputPath, format)
        val imgFile = new File(s"${outputPath}/sqlflow.$format")
        assert(imgFile.exists())
      }
    }
    withTempView("t") {
      withTempDir { dirPath =>
        sql(
          s"""
             |CREATE OR REPLACE TEMPORARY VIEW t AS
             |  SELECT k, sum(v) FROM VALUES (1, 2), (3, 4) t(k, v) GROUP BY k
           """.stripMargin)

        SQLFlow.validImageFormatSet.foreach { format =>
          val outputPath = s"${dirPath.getAbsolutePath}/$format"
          SQLFlow.saveAsSQLFlow(outputPath, format)
          val imgFile = new File(s"${outputPath}/sqlflow.$format")
          assert(imgFile.exists())
        }
      }
    }
  }

  test("cached plan node") {
    withTempView("v") {
      val df = spark.range(1)
        .selectExpr("id as k", "id as v")
        .groupBy("k")
        .count()
        .cache()

      df.where("count > 2")
        .selectExpr("k", "rand() as v")
        .createOrReplaceTempView("v")

      val flowString = getOutputAsString {
        SQLFlow.debugPrintAsSQLFlow()
      }
      checkOutputString(flowString,
        s"""
           |digraph {
           |  graph [pad="0.5", nodesep="0.5", ranksep="2", fontname="Helvetica"];
           |  node [shape=plain]
           |  rankdir=LR;
           |
           |
           |  "Aggregate_2" [label=<
           |  <table border="1" cellborder="0" cellspacing="0">
           |    <tr><td bgcolor="lightblue" port="nodeName"><i>Aggregate_2</i></td></tr>
           |    <tr><td port="0">k</td></tr>
           |  <tr><td port="1">count</td></tr>
           |  </table>>];
           |
           |
           |  "Filter_3" [label=<
           |  <table border="1" cellborder="0" cellspacing="0">
           |    <tr><td bgcolor="lightgray" port="nodeName"><i>Filter_3</i></td></tr>
           |    <tr><td port="0">k</td></tr>
           |  <tr><td port="1">count</td></tr>
           |  </table>>];
           |
           |
           |  "Project_1" [label=<
           |  <table border="1" cellborder="0" cellspacing="0">
           |    <tr><td bgcolor="lightgray" port="nodeName"><i>Project_1</i></td></tr>
           |    <tr><td port="0">k</td></tr>
           |  </table>>];
           |
           |
           |  "Project_4" [label=<
           |  <table border="1" cellborder="0" cellspacing="0">
           |    <tr><td bgcolor="lightgray" port="nodeName"><i>Project_4</i></td></tr>
           |    <tr><td port="0">k</td></tr>
           |  <tr><td port="1">v</td></tr>
           |  </table>>];
           |
           |
           |  "Range_0" [label=<
           |  <table border="1" cellborder="0" cellspacing="0">
           |    <tr><td bgcolor="lightpink" port="nodeName"><i>Range_0</i></td></tr>
           |    <tr><td port="0">id</td></tr>
           |  </table>>];
           |
           |
           |  "v" [label=<
           |  <table border="1" cellborder="0" cellspacing="0">
           |    <tr><td bgcolor="lightyellow" port="nodeName"><i>v</i></td></tr>
           |    <tr><td port="0">k</td></tr>
           |  <tr><td port="1">v</td></tr>
           |  </table>>];
           |
           |  "Aggregate_2":0 -> "Filter_3":0;
           |  "Aggregate_2":1 -> "Filter_3":1;
           |  "Filter_3":0 -> "Project_4":0;
           |  "Project_1":0 -> "Aggregate_2":0;
           |  "Project_4":0 -> "v":0;
           |  "Project_4":1 -> "v":1;
           |  "Range_0":0 -> "Project_1":0;
           |}
         """.stripMargin)
    }
  }

  test("remove redundant cached plan node") {
    withTempView("t1", "t2") {
      spark.range(1)
        .selectExpr("id as k", "id as v")
        .groupBy("k")
        .count()
        .cache()
        .createOrReplaceTempView("t1")

      spark.table("t1")
        .where("count > 2")
        .selectExpr("k", "rand() as v")
        .createOrReplaceTempView("t2")

      val flowString = getOutputAsString {
        SQLFlow.debugPrintAsSQLFlow()
      }
      checkOutputString(flowString,
        s"""
           |digraph {
           |  graph [pad="0.5", nodesep="0.5", ranksep="2", fontname="Helvetica"];
           |  node [shape=plain]
           |  rankdir=LR;
           |
           |
           |  "Aggregate_2" [label=<
           |  <table border="1" cellborder="0" cellspacing="0">
           |    <tr><td bgcolor="lightgray" port="nodeName"><i>Aggregate_2</i></td></tr>
           |    <tr><td port="0">k</td></tr>
           |  <tr><td port="1">count</td></tr>
           |  </table>>];
           |
           |
           |  "Filter_3" [label=<
           |  <table border="1" cellborder="0" cellspacing="0">
           |    <tr><td bgcolor="lightgray" port="nodeName"><i>Filter_3</i></td></tr>
           |    <tr><td port="0">k</td></tr>
           |  <tr><td port="1">count</td></tr>
           |  </table>>];
           |
           |
           |  "Project_1" [label=<
           |  <table border="1" cellborder="0" cellspacing="0">
           |    <tr><td bgcolor="lightgray" port="nodeName"><i>Project_1</i></td></tr>
           |    <tr><td port="0">k</td></tr>
           |  </table>>];
           |
           |
           |  "Project_4" [label=<
           |  <table border="1" cellborder="0" cellspacing="0">
           |    <tr><td bgcolor="lightgray" port="nodeName"><i>Project_4</i></td></tr>
           |    <tr><td port="0">k</td></tr>
           |  <tr><td port="1">v</td></tr>
           |  </table>>];
           |
           |
           |  "Range_0" [label=<
           |  <table border="1" cellborder="0" cellspacing="0">
           |    <tr><td bgcolor="lightpink" port="nodeName"><i>Range_0</i></td></tr>
           |    <tr><td port="0">id</td></tr>
           |  </table>>];
           |
           |
           |  "t1" [label=<
           |  <table border="1" cellborder="0" cellspacing="0">
           |    <tr><td bgcolor="lightblue" port="nodeName"><i>t1</i></td></tr>
           |    <tr><td port="0">k</td></tr>
           |  <tr><td port="1">count</td></tr>
           |  </table>>];
           |
           |
           |  "t2" [label=<
           |  <table border="1" cellborder="0" cellspacing="0">
           |    <tr><td bgcolor="lightyellow" port="nodeName"><i>t2</i></td></tr>
           |    <tr><td port="0">k</td></tr>
           |  <tr><td port="1">v</td></tr>
           |  </table>>];
           |
           |  "Aggregate_2":0 -> "t1":0;
           |  "Aggregate_2":1 -> "t1":1;
           |  "Filter_3":0 -> "Project_4":0;
           |  "Project_1":0 -> "Aggregate_2":0;
           |  "Project_4":0 -> "t2":0;
           |  "Project_4":1 -> "t2":1;
           |  "Range_0":0 -> "Project_1":0;
           |  "t1":0 -> "Filter_3":0;
           |  "t1":1 -> "Filter_3":1;
           |}
         """.stripMargin)
    }
  }

  test("handle cache plan nodes correctly") {
    withView("t1") {
      withTempView("t2", "t3") {
        sql("CREATE VIEW t1 AS SELECT k, SUM(v) sum FROM VALUES (1, 2), (2, 3) t(k, v) GROUP BY k")
        sql("CREATE TEMPORARY VIEW t2 AS SELECT k, sum, rand() v2 FROM t1")

        val df = spark.table("t2")
          .where("k > 1")
          .cache()

        df.groupBy("sum")
          .count()
          .createOrReplaceTempView("t3")

        val flowString = getOutputAsString {
          SQLFlow.debugPrintAsSQLFlow()
        }
        checkOutputString(flowString,
          s"""
             |digraph {
             |  graph [pad="0.5", nodesep="0.5", ranksep="2", fontname="Helvetica"];
             |  node [shape=plain]
             |  rankdir=LR;
             |
             |
             |  "Aggregate_1" [label=<
             |  <table border="1" cellborder="0" cellspacing="0">
             |    <tr><td bgcolor="lightgray" port="nodeName"><i>Aggregate_1</i></td></tr>
             |    <tr><td port="0">k</td></tr>
             |  <tr><td port="1">sum</td></tr>
             |  </table>>];
             |
             |
             |  "Aggregate_4" [label=<
             |  <table border="1" cellborder="0" cellspacing="0">
             |    <tr><td bgcolor="lightgray" port="nodeName"><i>Aggregate_4</i></td></tr>
             |    <tr><td port="0">sum</td></tr>
             |  <tr><td port="1">count</td></tr>
             |  </table>>];
             |
             |
             |  "Filter_2" [label=<
             |  <table border="1" cellborder="0" cellspacing="0">
             |    <tr><td bgcolor="lightgray" port="nodeName"><i>Filter_2</i></td></tr>
             |    <tr><td port="0">k</td></tr>
             |  <tr><td port="1">sum</td></tr>
             |  <tr><td port="2">v2</td></tr>
             |  </table>>];
             |
             |
             |  "LocalRelation_0" [label=<
             |  <table border="1" cellborder="0" cellspacing="0">
             |    <tr><td bgcolor="lightpink" port="nodeName"><i>LocalRelation_0</i></td></tr>
             |    <tr><td port="0">k</td></tr>
             |  <tr><td port="1">v</td></tr>
             |  </table>>];
             |
             |
             |  "Project_3" [label=<
             |  <table border="1" cellborder="0" cellspacing="0">
             |    <tr><td bgcolor="lightblue" port="nodeName"><i>Project_3</i></td></tr>
             |    <tr><td port="0">sum</td></tr>
             |  </table>>];
             |
             |
             |  "Project_5" [label=<
             |  <table border="1" cellborder="0" cellspacing="0">
             |    <tr><td bgcolor="lightgray" port="nodeName"><i>Project_5</i></td></tr>
             |    <tr><td port="0">k</td></tr>
             |  <tr><td port="1">sum</td></tr>
             |  <tr><td port="2">v2</td></tr>
             |  </table>>];
             |
             |
             |  "default.t1" [label=<
             |  <table border="1" cellborder="0" cellspacing="0">
             |    <tr><td bgcolor="lightyellow" port="nodeName"><i>default.t1</i></td></tr>
             |    <tr><td port="0">k</td></tr>
             |  <tr><td port="1">sum</td></tr>
             |  </table>>];
             |
             |
             |  "t2" [label=<
             |  <table border="1" cellborder="0" cellspacing="0">
             |    <tr><td bgcolor="lightyellow" port="nodeName"><i>t2</i></td></tr>
             |    <tr><td port="0">k</td></tr>
             |  <tr><td port="1">sum</td></tr>
             |  <tr><td port="2">v2</td></tr>
             |  </table>>];
             |
             |
             |  "t3" [label=<
             |  <table border="1" cellborder="0" cellspacing="0">
             |    <tr><td bgcolor="lightyellow" port="nodeName"><i>t3</i></td></tr>
             |    <tr><td port="0">sum</td></tr>
             |  <tr><td port="1">count</td></tr>
             |  </table>>];
             |
             |  "Aggregate_1":0 -> "default.t1":0;
             |  "Aggregate_1":1 -> "default.t1":1;
             |  "Aggregate_4":0 -> "t3":0;
             |  "Aggregate_4":1 -> "t3":1;
             |  "Filter_2":1 -> "Project_3":0;
             |  "LocalRelation_0":0 -> "Aggregate_1":0;
             |  "LocalRelation_0":1 -> "Aggregate_1":1;
             |  "Project_3":0 -> "Aggregate_4":0;
             |  "Project_5":0 -> "t2":0;
             |  "Project_5":1 -> "t2":1;
             |  "Project_5":2 -> "t2":2;
             |  "default.t1":0 -> "Project_5":0;
             |  "default.t1":1 -> "Project_5":1;
             |  "t2":0 -> "Filter_2":0;
             |  "t2":1 -> "Filter_2":1;
             |  "t2":2 -> "Filter_2":2;
             |}
           """.stripMargin)
      }
    }
  }

  test("handle permanent views correctly") {
    withView("t1", "t2", "t3") {
      sql("CREATE VIEW t1 AS SELECT k, SUM(v) sum FROM VALUES (1, 2), (2, 3) t(k, v) GROUP BY k")
      sql("CREATE VIEW t2 AS SELECT k, sum, rand() v2 FROM t1")
      sql("CREATE VIEW t3 AS SELECT k FROM t2 WHERE v2 > 0.50")

      val flowString = getOutputAsString {
        SQLFlow.debugPrintAsSQLFlow()
      }
      checkOutputString(flowString,
        s"""
           |digraph {
           |  graph [pad="0.5", nodesep="0.5", ranksep="2", fontname="Helvetica"];
           |  node [shape=plain]
           |  rankdir=LR;
           |
           |
           |  "Aggregate_1" [label=<
           |  <table border="1" cellborder="0" cellspacing="0">
           |    <tr><td bgcolor="lightgray" port="nodeName"><i>Aggregate_1</i></td></tr>
           |    <tr><td port="0">k</td></tr>
           |  <tr><td port="1">sum</td></tr>
           |  </table>>];
           |
           |
           |  "Filter_3" [label=<
           |  <table border="1" cellborder="0" cellspacing="0">
           |    <tr><td bgcolor="lightgray" port="nodeName"><i>Filter_3</i></td></tr>
           |    <tr><td port="0">k</td></tr>
           |  <tr><td port="1">sum</td></tr>
           |  <tr><td port="2">v2</td></tr>
           |  </table>>];
           |
           |
           |  "LocalRelation_0" [label=<
           |  <table border="1" cellborder="0" cellspacing="0">
           |    <tr><td bgcolor="lightpink" port="nodeName"><i>LocalRelation_0</i></td></tr>
           |    <tr><td port="0">k</td></tr>
           |  <tr><td port="1">v</td></tr>
           |  </table>>];
           |
           |
           |  "Project_2" [label=<
           |  <table border="1" cellborder="0" cellspacing="0">
           |    <tr><td bgcolor="lightgray" port="nodeName"><i>Project_2</i></td></tr>
           |    <tr><td port="0">k</td></tr>
           |  <tr><td port="1">sum</td></tr>
           |  <tr><td port="2">v2</td></tr>
           |  </table>>];
           |
           |
           |  "Project_4" [label=<
           |  <table border="1" cellborder="0" cellspacing="0">
           |    <tr><td bgcolor="lightgray" port="nodeName"><i>Project_4</i></td></tr>
           |    <tr><td port="0">k</td></tr>
           |  </table>>];
           |
           |
           |  "default.t1" [label=<
           |  <table border="1" cellborder="0" cellspacing="0">
           |    <tr><td bgcolor="lightyellow" port="nodeName"><i>default.t1</i></td></tr>
           |    <tr><td port="0">k</td></tr>
           |  <tr><td port="1">sum</td></tr>
           |  </table>>];
           |
           |
           |  "default.t2" [label=<
           |  <table border="1" cellborder="0" cellspacing="0">
           |    <tr><td bgcolor="lightyellow" port="nodeName"><i>default.t2</i></td></tr>
           |    <tr><td port="0">k</td></tr>
           |  <tr><td port="1">sum</td></tr>
           |  <tr><td port="2">v2</td></tr>
           |  </table>>];
           |
           |
           |  "default.t3" [label=<
           |  <table border="1" cellborder="0" cellspacing="0">
           |    <tr><td bgcolor="lightyellow" port="nodeName"><i>default.t3</i></td></tr>
           |    <tr><td port="0">k</td></tr>
           |  </table>>];
           |
           |  "Aggregate_1":0 -> "default.t1":0;
           |  "Aggregate_1":1 -> "default.t1":1;
           |  "Filter_3":0 -> "Project_4":0;
           |  "LocalRelation_0":0 -> "Aggregate_1":0;
           |  "LocalRelation_0":1 -> "Aggregate_1":1;
           |  "Project_2":0 -> "default.t2":0;
           |  "Project_2":1 -> "default.t2":1;
           |  "Project_2":2 -> "default.t2":2;
           |  "Project_4":0 -> "default.t3":0;
           |  "default.t1":0 -> "Project_2":0;
           |  "default.t1":1 -> "Project_2":1;
           |  "default.t2":0 -> "Filter_3":0;
           |  "default.t2":1 -> "Filter_3":1;
           |  "default.t2":2 -> "Filter_3":2;
           |}
         """.stripMargin)
    }
  }

  test("TODO: Cannot cache view") {
    withView("t") {
      sql("CREATE VIEW t AS SELECT k, SUM(v) sum FROM VALUES (1, 2) t(k, v) GROUP BY k")

      val flowString = getOutputAsString {
        SQLFlow.debugPrintAsSQLFlow()
      }
      checkOutputString(flowString,
        s"""
           |digraph {
           |  graph [pad="0.5", nodesep="0.5", ranksep="2", fontname="Helvetica"];
           |  node [shape=plain]
           |  rankdir=LR;
           |
           |
           |  "Aggregate_1" [label=<
           |  <table border="1" cellborder="0" cellspacing="0">
           |    <tr><td bgcolor="lightgray" port="nodeName"><i>Aggregate_1</i></td></tr>
           |    <tr><td port="0">k</td></tr>
           |  <tr><td port="1">sum</td></tr>
           |  </table>>];
           |
           |
           |  "LocalRelation_0" [label=<
           |  <table border="1" cellborder="0" cellspacing="0">
           |    <tr><td bgcolor="lightpink" port="nodeName"><i>LocalRelation_0</i></td></tr>
           |    <tr><td port="0">k</td></tr>
           |  <tr><td port="1">v</td></tr>
           |  </table>>];
           |
           |
           |  "default.t" [label=<
           |  <table border="1" cellborder="0" cellspacing="0">
           |    <tr><td bgcolor="lightyellow" port="nodeName"><i>default.t</i></td></tr>
           |    <tr><td port="0">k</td></tr>
           |  <tr><td port="1">sum</td></tr>
           |  </table>>];
           |
           |  "Aggregate_1":0 -> "default.t":0;
           |  "Aggregate_1":1 -> "default.t":1;
           |  "LocalRelation_0":0 -> "Aggregate_1":0;
           |  "LocalRelation_0":1 -> "Aggregate_1":1;
           |}
         """.stripMargin)
    }
  }
}
