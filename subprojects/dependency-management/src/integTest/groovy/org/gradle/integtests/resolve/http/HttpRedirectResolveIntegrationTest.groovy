/*
 * Copyright 2019 the original author or authors.
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

class HttpRedirectResolveIntegrationTest extends AbstractRedirectResolveIntegrationTest {

    def "resolves Ivy module artifacts via HTTP redirect with deprecation"() {
        def module = withIvyRepository()
        when:
        redirect('/repo/group/projectA/1.0/ivy-1.0.xml', '/redirected/group/projectA/1.0/ivy-1.0.xml', module.ivyFile)
        redirect('/repo/group/projectA/1.0/projectA-1.0.jar', '/redirected/group/projectA/1.0/projectA-1.0.jar', module.jarFile)

        then:
        executer.expectDeprecationWarnings(2) // HTTP repository + HTTP redirect
        succeeds('listJars')

        and:
        assertWarnsAboutInsecureRepositories()
        assertWarnsAboutInsecureRedirects()
    }

    def "resolves Maven module artifacts via HTTP redirect with deprecation"() {
        def module = withMavenRepository()
        when:
        redirect('/repo/group/projectA/1.0/projectA-1.0.pom', '/redirected/group/projectA/1.0/projectA-1.0.pom', module.pomFile)
        redirect('/repo/group/projectA/1.0/projectA-1.0.jar', '/redirected/group/projectA/1.0/projectA-1.0.jar', module.artifactFile)

        then:
        executer.expectDeprecationWarnings(2) // HTTP repository + HTTP redirect
        succeeds('listJars')

        and:
        assertWarnsAboutInsecureRepositories("maven")
        assertWarnsAboutInsecureRedirects()
    }

    def "prints last redirect location in case of failure with deprecation"() {
        withIvyRepository()
        when:
        redirectBroken('/repo/group/projectA/1.0/ivy-1.0.xml', '/redirected/group/projectA/1.0/ivy-1.0.xml')

        then:
        executer.expectDeprecationWarnings(2) // HTTP repository + HTTP redirect
        fails('listJars')

        and:
        assertWarnsAboutInsecureRepositories()
        assertWarnsAboutInsecureRedirects()
    }

    void assertWarnsAboutInsecureRepositories(String repositoryType="ivy") {
        outputContains("Using insecure protocols with repositories has been deprecated. This is scheduled to be removed in Gradle 6.0. Switch repository '${repositoryType}(${server.uri}/repo)' to a secure protocol (like HTTPS) or allow insecure protocols, see ")
    }
}
