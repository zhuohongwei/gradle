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

import org.gradle.integtests.fixtures.TestResources
import org.gradle.test.fixtures.keystore.TestKeyStore
import org.junit.Rule

class HttpsToHttpRedirectResolveIntegrationTest extends AbstractRedirectResolveIntegrationTest {
    @Rule TestResources resources = new TestResources(temporaryFolder)

    def setup() {
        TestKeyStore keyStore = TestKeyStore.init(resources.dir)
        keyStore.enableSslWithServerCert(server)
        keyStore.configureServerCert(executer)
    }

    def "resolves Ivy module artifacts via HTTP redirect with deprecation"() {
        def module = withIvyRepository()
        when:
        redirect('/repo/group/projectA/1.0/ivy-1.0.xml', '/redirected/group/projectA/1.0/ivy-1.0.xml', module.ivyFile)
        redirect('/repo/group/projectA/1.0/projectA-1.0.jar', '/redirected/group/projectA/1.0/projectA-1.0.jar', module.jarFile)

        then:
        executer.expectDeprecationWarning()
        succeeds('listJars')

        and:
        assertWarnsAboutInsecureRedirects()
    }

    def "resolves Maven module artifacts via HTTP redirect with deprecation"() {
        def module = withMavenRepository()
        when:
        redirect('/repo/group/projectA/1.0/projectA-1.0.pom', '/redirected/group/projectA/1.0/projectA-1.0.pom', module.pomFile)
        redirect('/repo/group/projectA/1.0/projectA-1.0.jar', '/redirected/group/projectA/1.0/projectA-1.0.jar', module.artifactFile)

        then:
        executer.expectDeprecationWarning()
        succeeds('listJars')

        and:
        assertWarnsAboutInsecureRedirects()
    }

    def "prints last redirect location in case of failure with deprecation"() {
        withIvyRepository()
        when:
        redirectBroken('/repo/group/projectA/1.0/ivy-1.0.xml', '/redirected/group/projectA/1.0/ivy-1.0.xml')

        then:
        executer.expectDeprecationWarning()
        fails('listJars')

        and:
        assertWarnsAboutInsecureRedirects()
    }
}
