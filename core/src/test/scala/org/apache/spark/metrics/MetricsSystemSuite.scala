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

package org.apache.spark.metrics

import com.codahale.metrics.{Gauge, MetricRegistry}
import org.scalatest.{BeforeAndAfter, PrivateMethodTester}

import org.apache.spark.{SecurityManager, SparkConf, SparkFunSuite}
import org.apache.spark.deploy.master.MasterSource
import org.apache.spark.internal.config._
import org.apache.spark.metrics.sink.Sink
import org.apache.spark.metrics.source.{Source, StaticSources}

class MetricsSystemSuite extends SparkFunSuite with BeforeAndAfter with PrivateMethodTester{
  var filePath: String = _
  var conf: SparkConf = null
  var securityMgr: SecurityManager = null

  before {
    filePath = getClass.getClassLoader.getResource("test_metrics_system.properties").getFile
    conf = new SparkConf(false).set("spark.metrics.conf", filePath)
    securityMgr = new SecurityManager(conf)
  }

  test("MetricsSystem with default config") {
    val metricsSystem = MetricsSystem.createMetricsSystem("default", conf, securityMgr)
    metricsSystem.start()

    assert(metricsSystem.getSources.length === StaticSources.allSources.length)
    assert(metricsSystem.getSinks.length === 0)
    assert(metricsSystem.getServletHandlers.nonEmpty)
  }

  test("MetricsSystem with sources add") {
    val metricsSystem = MetricsSystem.createMetricsSystem("test", conf, securityMgr)
    metricsSystem.start()

    assert(metricsSystem.getSources.length === StaticSources.allSources.length)
    assert(metricsSystem.getSinks.length === 1)
    assert(metricsSystem.getServletHandlers.nonEmpty)

    val source = new MasterSource(null)
    metricsSystem.registerSource(source)
    assert(metricsSystem.getSources.length === StaticSources.allSources.length + 1)
  }

  test("MetricsSystem with Driver instance") {
    val source = new Source {
      override val sourceName = "dummySource"
      override val metricRegistry = new MetricRegistry()
    }

    val appId = "testId"
    val executorId = "driver"
    conf.set("spark.app.id", appId)
    conf.set("spark.executor.id", executorId)

    val instanceName = "driver"
    val driverMetricsSystem = MetricsSystem.createMetricsSystem(instanceName, conf, securityMgr)

    val metricName = driverMetricsSystem.buildRegistryName(source)
    assert(metricName === s"$appId.$executorId.${source.sourceName}")
  }

  test("MetricsSystem with Driver instance and spark.app.id is not set") {
    val source = new Source {
      override val sourceName = "dummySource"
      override val metricRegistry = new MetricRegistry()
    }

    val executorId = "driver"
    conf.set("spark.executor.id", executorId)

    val instanceName = "driver"
    val driverMetricsSystem = MetricsSystem.createMetricsSystem(instanceName, conf, securityMgr)

    val metricName = driverMetricsSystem.buildRegistryName(source)
    assert(metricName === source.sourceName)
  }

  test("MetricsSystem with Driver instance and spark.executor.id is not set") {
    val source = new Source {
      override val sourceName = "dummySource"
      override val metricRegistry = new MetricRegistry()
    }

    val appId = "testId"
    conf.set("spark.app.id", appId)

    val instanceName = "driver"
    val driverMetricsSystem = MetricsSystem.createMetricsSystem(instanceName, conf, securityMgr)

    val metricName = driverMetricsSystem.buildRegistryName(source)
    assert(metricName === source.sourceName)
  }

  test("MetricsSystem with Executor instance") {
    val source = new Source {
      override val sourceName = "dummySource"
      override val metricRegistry = new MetricRegistry()
    }

    val appId = "testId"
    val executorId = "1"
    conf.set("spark.app.id", appId)
    conf.set("spark.executor.id", executorId)

    val instanceName = "executor"
    val executorMetricsSystem = MetricsSystem.createMetricsSystem(instanceName, conf, securityMgr)

    val metricName = executorMetricsSystem.buildRegistryName(source)
    assert(metricName === s"$appId.$executorId.${source.sourceName}")
  }

  test("MetricsSystem with Executor instance and spark.app.id is not set") {
    val source = new Source {
      override val sourceName = "dummySource"
      override val metricRegistry = new MetricRegistry()
    }

    val executorId = "1"
    conf.set("spark.executor.id", executorId)

    val instanceName = "executor"
    val executorMetricsSystem = MetricsSystem.createMetricsSystem(instanceName, conf, securityMgr)

    val metricName = executorMetricsSystem.buildRegistryName(source)
    assert(metricName === source.sourceName)
  }

  test("MetricsSystem with Executor instance and spark.executor.id is not set") {
    val source = new Source {
      override val sourceName = "dummySource"
      override val metricRegistry = new MetricRegistry()
    }

    val appId = "testId"
    conf.set("spark.app.id", appId)

    val instanceName = "executor"
    val executorMetricsSystem = MetricsSystem.createMetricsSystem(instanceName, conf, securityMgr)

    val metricName = executorMetricsSystem.buildRegistryName(source)
    assert(metricName === source.sourceName)
  }

  test("MetricsSystem with instance which is neither Driver nor Executor") {
    val source = new Source {
      override val sourceName = "dummySource"
      override val metricRegistry = new MetricRegistry()
    }

    val appId = "testId"
    val executorId = "dummyExecutorId"
    conf.set("spark.app.id", appId)
    conf.set("spark.executor.id", executorId)

    val instanceName = "testInstance"
    val testMetricsSystem = MetricsSystem.createMetricsSystem(instanceName, conf, securityMgr)

    val metricName = testMetricsSystem.buildRegistryName(source)

    // Even if spark.app.id and spark.executor.id are set, they are not used for the metric name.
    assert(metricName != s"$appId.$executorId.${source.sourceName}")
    assert(metricName === source.sourceName)
  }

  test("MetricsSystem with Executor instance, with custom namespace") {
    val source = new Source {
      override val sourceName = "dummySource"
      override val metricRegistry = new MetricRegistry()
    }

    val appId = "testId"
    val appName = "testName"
    val executorId = "1"
    conf.set("spark.app.id", appId)
    conf.set("spark.app.name", appName)
    conf.set("spark.executor.id", executorId)
    conf.set(METRICS_NAMESPACE, "${spark.app.name}")

    val instanceName = "executor"
    val executorMetricsSystem = MetricsSystem.createMetricsSystem(instanceName, conf, securityMgr)

    val metricName = executorMetricsSystem.buildRegistryName(source)
    assert(metricName === s"$appName.$executorId.${source.sourceName}")
  }

  test("MetricsSystem with Executor instance, custom namespace which is not set") {
    val source = new Source {
      override val sourceName = "dummySource"
      override val metricRegistry = new MetricRegistry()
    }

    val executorId = "1"
    val namespaceToResolve = "${spark.doesnotexist}"
    conf.set("spark.executor.id", executorId)
    conf.set(METRICS_NAMESPACE, namespaceToResolve)

    val instanceName = "executor"
    val executorMetricsSystem = MetricsSystem.createMetricsSystem(instanceName, conf, securityMgr)

    val metricName = executorMetricsSystem.buildRegistryName(source)
    // If the user set the spark.metrics.namespace property to an expansion of another property
    // (say ${spark.doesnotexist}, the unresolved name (i.e. literally ${spark.doesnotexist})
    // is used as the root logger name.
    assert(metricName === s"$namespaceToResolve.$executorId.${source.sourceName}")
  }

  test("MetricsSystem with Executor instance, custom namespace, spark.executor.id not set") {
    val source = new Source {
      override val sourceName = "dummySource"
      override val metricRegistry = new MetricRegistry()
    }

    val appId = "testId"
    conf.set("spark.app.name", appId)
    conf.set(METRICS_NAMESPACE, "${spark.app.name}")

    val instanceName = "executor"
    val executorMetricsSystem = MetricsSystem.createMetricsSystem(instanceName, conf, securityMgr)

    val metricName = executorMetricsSystem.buildRegistryName(source)
    assert(metricName === source.sourceName)
  }

  test("MetricsSystem with non-driver, non-executor instance with custom namespace") {
    val source = new Source {
      override val sourceName = "dummySource"
      override val metricRegistry = new MetricRegistry()
    }

    val appId = "testId"
    val appName = "testName"
    val executorId = "dummyExecutorId"
    conf.set("spark.app.id", appId)
    conf.set("spark.app.name", appName)
    conf.set(METRICS_NAMESPACE, "${spark.app.name}")
    conf.set("spark.executor.id", executorId)

    val instanceName = "testInstance"
    val testMetricsSystem = MetricsSystem.createMetricsSystem(instanceName, conf, securityMgr)

    val metricName = testMetricsSystem.buildRegistryName(source)

    // Even if spark.app.id and spark.executor.id are set, they are not used for the metric name.
    assert(metricName != s"$appId.$executorId.${source.sourceName}")
    assert(metricName === source.sourceName)
  }

  test("MetricsSystem registers dynamically added metrics") {
    val registry = new MetricRegistry()
    val source = new Source {
      override val sourceName = "dummySource"
      override val metricRegistry = new MetricRegistry()
    }

    val instanceName = "testInstance"
    val metricsSystem = MetricsSystem.createMetricsSystem(
      instanceName, conf, securityMgr, registry)
    metricsSystem.registerSource(source)
    assert(!registry.getNames.contains("dummySource.newMetric"), "Metric shouldn't be registered")

    source.metricRegistry.register("newMetric", new Gauge[Integer] {
      override def getValue: Integer = 1
    })
    assert(registry.getNames.contains("dummySource.newMetric"),
      "Metric should have been registered")
  }

}
