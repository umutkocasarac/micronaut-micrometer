/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.configuration.metrics.management.endpoint

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Unroll

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED

class MetricsEndpointSpec extends Specification {

    void "test the beans are available"() {
        given:
        ApplicationContext context = ApplicationContext.builder("test").build()
        context.start()

        expect:
        context.containsBean(MetricsEndpoint)
        context.containsBean(MeterRegistry)
        context.containsBean(CompositeMeterRegistry)

        cleanup:
        context.close()
    }

    void "test metrics endpoint disabled"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'micronaut.http.client.read-timeout': '5m',
                'endpoints.metrics.sensitive'       : false,
                (MICRONAUT_METRICS_ENABLED)         : false
        ])

        when:
        def context = embeddedServer.getApplicationContext()

        then:
        !context.containsBean(MeterRegistry)
        !context.containsBean(CompositeMeterRegistry)

        URL server = embeddedServer.getURL()
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, server)

        when:
        rxClient.retrieve(HttpRequest.GET("/metrics"), Map).blockingFirst()

        then:
        thrown(HttpClientResponseException)

        cleanup:
        rxClient.close()
        embeddedServer.close()
    }

    @Unroll
    void "test metrics endpoint jvmEnabled(#jvmEnabled) logbackEnabled(#logbackEnabled) uptimeEnabled(#uptimeEnabled) processorEnabled(#processorEnabled) filesEnabled(#filesEnabled)"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'endpoints.metrics.sensitive'                : false,
                (MICRONAUT_METRICS_ENABLED)                  : true,
                "micronaut.metrics.binders.jvm.enabled"      : jvmEnabled,
                "micronaut.metrics.binders.logback.enabled"  : logbackEnabled,
                "micronaut.metrics.binders.uptime.enabled"   : uptimeEnabled,
                "micronaut.metrics.binders.processor.enabled": processorEnabled,
                "micronaut.metrics.binders.files.enabled"    : filesEnabled
        ])
        URL server = embeddedServer.getURL()
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, server)

        when:
        def response = rxClient.exchange("/metrics", Map).blockingFirst()
        Map result = response.body()

        then:
        response.code() == HttpStatus.OK.code
        if (jvmEnabled || logbackEnabled || uptimeEnabled || processorEnabled || filesEnabled) {
            result.names.contains("jvm.buffer.count") == jvmEnabled
            result.names.contains("jvm.buffer.memory.used") == jvmEnabled
            result.names.contains("jvm.buffer.total.capacity") == jvmEnabled
            result.names.contains("jvm.classes.loaded") == jvmEnabled
            result.names.contains("jvm.classes.unloaded") == jvmEnabled
            result.names.contains("jvm.gc.live.data.size") == jvmEnabled
            result.names.contains("jvm.gc.max.data.size") == jvmEnabled
            result.names.contains("jvm.gc.memory.allocated") == jvmEnabled
            result.names.contains("jvm.gc.memory.promoted") == jvmEnabled
            result.names.contains("jvm.memory.committed") == jvmEnabled
            result.names.contains("jvm.memory.max") == jvmEnabled
            result.names.contains("jvm.memory.used") == jvmEnabled
            result.names.contains("jvm.threads.daemon") == jvmEnabled
            result.names.contains("jvm.threads.live") == jvmEnabled
            result.names.contains("jvm.threads.peak") == jvmEnabled

            result.names.contains("logback.events") == logbackEnabled

            result.names.contains("process.files.max") == filesEnabled
            result.names.contains("process.files.open") == filesEnabled

            result.names.contains("process.start.time") == uptimeEnabled
            result.names.contains("process.uptime") == uptimeEnabled

            result.names.contains("process.cpu.usage") == processorEnabled
            result.names.contains("system.cpu.count") == processorEnabled
            result.names.contains("system.cpu.usage") == processorEnabled
            result.names.contains("system.load.average.1m") == processorEnabled
        } else {
            result == [:]
        }

        cleanup:
        rxClient.close()
        embeddedServer.close()

        where:
        jvmEnabled | logbackEnabled | uptimeEnabled | processorEnabled | filesEnabled
        true       | true           | true          | true             | true
        false      | true           | true          | true             | true
        true       | false          | true          | true             | true
        true       | true           | false         | true             | true
        true       | true           | true          | false            | true
        true       | true           | true          | true             | false
        false      | false          | false         | false            | false
    }

    @Unroll
    void "test metrics endpoint get jvm details #name success"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'endpoints.metrics.sensitive'          : false,
                (MICRONAUT_METRICS_ENABLED)            : true,
                "micronaut.metrics.binders.jvm.enabled": true
        ])
        URL server = embeddedServer.getURL()
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, server)

        when:
        def response = rxClient.exchange("/metrics/$name", Map).blockingFirst()
        Map result = response.body() as Map

        then:
        result["name"]
        result["measurements"]
        result["description"]
        result["baseUnit"]

        cleanup:
        rxClient.close()
        embeddedServer.close()

        where:
        name << [
                "jvm.buffer.count",
                "jvm.buffer.memory.used",
                "jvm.buffer.total.capacity",
                "jvm.classes.loaded",
                "jvm.classes.unloaded",
                "jvm.gc.live.data.size",
                "jvm.gc.max.data.size",
                "jvm.gc.memory.allocated",
                "jvm.gc.memory.promoted",
                "jvm.memory.committed",
                "jvm.memory.max",
                "jvm.memory.used",
                "jvm.threads.daemon",
                "jvm.threads.live",
                "jvm.threads.peak"
        ]
    }


    void "test metrics endpoint get jvm details success with tags"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'endpoints.metrics.sensitive'          : false,
                (MICRONAUT_METRICS_ENABLED)            : true,
                "micronaut.metrics.binders.jvm.enabled": true
        ])
        URL server = embeddedServer.getURL()
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, server)

        when:
        def response = rxClient.exchange("/metrics/jvm.buffer.count?tag=id:direct", Map).blockingFirst()
        Map result = response.body() as Map

        then:
        result["name"]
        result["measurements"]
        result["description"]
        result["baseUnit"]

        when:
        rxClient.exchange("/metrics/jvm.buffer.count?tag=id:blah", Map).blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.NOT_FOUND

        cleanup:
        rxClient.close()
        embeddedServer.close()

    }

    @Unroll
    void "test metrics endpoint get jvm details #name disabled"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'endpoints.metrics.sensitive'          : false,
                (MICRONAUT_METRICS_ENABLED)            : false,
                "micronaut.metrics.binders.jvm.enabled": true
        ])
        URL server = embeddedServer.getURL()
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, server)

        when:
        rxClient.exchange("/metrics/$name", Map).blockingFirst()

        then:
        thrown(HttpClientResponseException)

        cleanup:
        rxClient.close()
        embeddedServer.close()

        where:
        name << [
                "jvm.buffer.count",
                "jvm.buffer.memory.used",
                "jvm.buffer.total.capacity",
                "jvm.classes.loaded",
                "jvm.classes.unloaded",
                "jvm.gc.live.data.size",
                "jvm.gc.max.data.size",
                "jvm.gc.memory.allocated",
                "jvm.gc.memory.promoted",
                "jvm.memory.committed",
                "jvm.memory.max",
                "jvm.memory.used",
                "jvm.threads.daemon",
                "jvm.threads.live",
                "jvm.threads.peak"
        ]
    }

    @Unroll
    void "test metrics endpoint get logback details #name success"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'endpoints.metrics.sensitive'              : false,
                (MICRONAUT_METRICS_ENABLED)                : true,
                "micronaut.metrics.binders.logback.enabled": true
        ])
        URL server = embeddedServer.getURL()
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, server)

        when:
        def response = rxClient.exchange("/metrics/$name", Map).blockingFirst()
        Map result = response.body() as Map

        then:
        result["name"]
        result["measurements"]
        result["description"]
        result["baseUnit"]

        cleanup:
        rxClient.close()
        embeddedServer.close()

        where:
        name << ["logback.events"]
    }

    @Unroll
    void "test metrics endpoint get logback details #name disabled"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'endpoints.metrics.sensitive'              : false,
                (MICRONAUT_METRICS_ENABLED)                : true,
                "micronaut.metrics.binders.logback.enabled": false
        ])
        URL server = embeddedServer.getURL()
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, server)

        when:
        rxClient.exchange("/metrics/$name", Map).blockingFirst()

        then:
        thrown(HttpClientResponseException)

        cleanup:
        rxClient.close()
        embeddedServer.close()

        where:
        name << ["logback.events"]
    }

    @Unroll
    void "test metrics endpoint get uptime details #name success"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'endpoints.metrics.sensitive'             : false,
                (MICRONAUT_METRICS_ENABLED)               : true,
                "micronaut.metrics.binders.uptime.enabled": true
        ])
        URL server = embeddedServer.getURL()
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, server)

        when:
        def response = rxClient.exchange("/metrics/$name", Map).blockingFirst()
        Map result = response.body() as Map

        then:
        result["name"]
        result["measurements"]
        result["description"]

        cleanup:
        rxClient.close()
        embeddedServer.close()

        where:
        name << ["process.uptime",
                 "process.start.time"]
    }

    @Unroll
    void "test metrics endpoint get uptime details #name disabled"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'endpoints.metrics.sensitive'             : false,
                (MICRONAUT_METRICS_ENABLED)               : true,
                "micronaut.metrics.binders.uptime.enabled": false
        ])
        URL server = embeddedServer.getURL()
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, server)

        when:
        rxClient.exchange("/metrics/$name", Map).blockingFirst()

        then:
        thrown(HttpClientResponseException)

        cleanup:
        rxClient.close()
        embeddedServer.close()

        where:
        name << ["process.uptime",
                 "process.start.time"]
    }

    @Unroll
    void "test metrics endpoint get processor details #name success"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'endpoints.metrics.sensitive'                : false,
                (MICRONAUT_METRICS_ENABLED)                  : true,
                "micronaut.metrics.binders.processor.enabled": true
        ])
        URL server = embeddedServer.getURL()
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, server)

        when:
        def response = rxClient.exchange("/metrics/$name", Map).blockingFirst()
        Map result = response.body() as Map

        then:
        result["name"]
        result["measurements"]
        result["description"]

        cleanup:
        rxClient.close()
        embeddedServer.close()

        where:
        name << [
                "system.cpu.usage",
                "system.cpu.count",
                "process.cpu.usage"]
    }

    @Unroll
    @IgnoreIf({ os.windows })
    void "test metrics endpoint get processor details unix-specific #name success"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'endpoints.metrics.sensitive'                : false,
                (MICRONAUT_METRICS_ENABLED)                  : true,
                "micronaut.metrics.binders.processor.enabled": true
        ])
        URL server = embeddedServer.getURL()
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, server)

        when:
        def response = rxClient.exchange("/metrics/$name", Map).blockingFirst()
        Map result = response.body() as Map

        then:
        result["name"]
        result["measurements"]
        result["description"]

        cleanup:
        rxClient.close()
        embeddedServer.close()

        where:
        name << ["system.load.average.1m"]
    }

    @Unroll
    void "test metrics endpoint get processor details #name disabled"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'endpoints.metrics.sensitive'                : false,
                (MICRONAUT_METRICS_ENABLED)                  : true,
                "micronaut.metrics.binders.processor.enabled": false
        ])
        URL server = embeddedServer.getURL()
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, server)

        when:
        rxClient.exchange("/metrics/$name", Map).blockingFirst()

        then:
        thrown(HttpClientResponseException)

        cleanup:
        rxClient.close()
        embeddedServer.close()

        where:
        name << ["system.load.average.1m",
                 "system.cpu.usage",
                 "system.cpu.count",
                 "process.cpu.usage"]
    }

    @Unroll
    @IgnoreIf({ os.windows })
    void "test metrics endpoint get file details #name success"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'endpoints.metrics.sensitive'            : false,
                (MICRONAUT_METRICS_ENABLED)              : true,
                "micronaut.metrics.binders.files.enabled": true
        ])
        URL server = embeddedServer.getURL()
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, server)

        when:
        def response = rxClient.exchange("/metrics/$name", Map).blockingFirst()
        Map result = response.body() as Map

        then:
        result["name"]
        result["measurements"]
        result["description"]
        result["baseUnit"]

        cleanup:
        rxClient.close()
        embeddedServer.close()

        where:
        name << ["process.files.open",
                 "process.files.max"]
    }

    @Unroll
    void "test metrics endpoint get file details #name disabled"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'endpoints.metrics.sensitive'            : false,
                (MICRONAUT_METRICS_ENABLED)              : true,
                "micronaut.metrics.binders.files.enabled": false
        ])
        URL server = embeddedServer.getURL()
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, server)

        when:
        rxClient.exchange("/metrics/$name", Map).blockingFirst()

        then:
        thrown(HttpClientResponseException)

        cleanup:
        rxClient.close()
        embeddedServer.close()

        where:
        name << ["process.files.open",
                 "process.files.max"]
    }
}
