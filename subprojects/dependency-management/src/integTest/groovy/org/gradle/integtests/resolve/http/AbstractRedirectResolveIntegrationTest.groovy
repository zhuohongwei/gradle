/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.integtests.resolve.http

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.test.fixtures.ivy.IvyModule
import org.gradle.test.fixtures.maven.MavenModule
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.util.SetSystemProperties
import org.junit.Rule

abstract class AbstractRedirectResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {
    @Rule SetSystemProperties systemProperties = new SetSystemProperties()
    @Rule HttpServer backingServer = new HttpServer()

    def setup() {
        server.useHostname()
        backingServer.useHostname()
        backingServer.start()

        buildFile << """
            configurations { compile }
            dependencies { compile 'group:projectA:1.0' }
            task listJars {
                dependsOn configurations.compile
                doLast {
                    assert configurations.compile.singleFile.name == 'projectA-1.0.jar'
                }
            }
        """
    }

    MavenModule withMavenRepository() {
        buildFile << """
            repositories {
                maven { url "${server.uri}/repo" }
            }
        """
        return mavenRepo().module('group', 'projectA').publish()
    }

    IvyModule withIvyRepository() {
        buildFile << """
            repositories {
                ivy { url "${server.uri}/repo" }
            }
        """
        return ivyRepo().module('group', 'projectA').publish()
    }

    void redirect(String from, String to, File resource) {
        server.expectGetRedirected(from, "${backingServer.uri}/${to}")
        backingServer.expectGet(to, resource)
    }
    void redirectBroken(String from, String to) {
        server.expectGetRedirected(from, "${backingServer.uri}/${to}")
        backingServer.expectGetBroken(to)
    }
    void assertWarnsAboutInsecureRedirects() {
        outputContains("Following insecure redirects has been deprecated. This is scheduled to be removed in Gradle 6.0. Use a secure protocol (like HTTPS) or allow insecure protocols.")
    }
}
