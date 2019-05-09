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

package org.gradle.deployment

import org.gradle.integtests.fixtures.AbstractIntegrationSpec


class DeploymentSpecRegistryIntegrationTest extends AbstractIntegrationSpec {
    def "can register a deployment"() {
        buildFile << """
            ${testDeploymentImpl}

            deployments {
                test(TestDeploymentSpec) {
                    foo = "bar"
                }
            }
            
            task assertDeployment {
                doLast {
                    assert deployments.findByName("test").foo == "bar"
                }
            }
        """

        expect:
        succeeds("assertDeployment")
    }

    def "can register a task to use a deployment"() {
        buildFile << """
            ${testDeploymentImpl}

            import org.gradle.deployment.internal.DeploymentRegistry

            deployments {
                test(TestDeploymentSpec) {
                    foo = "bar"
                }
            }
            
            task requiresTask1 {
                requires "test"
                doLast {
                    def testDeployment = services.get(DeploymentRegistry).get("test", TestDeploymentHandle)
                    assert testDeployment != null
                    assert testDeployment.running
                }
            }
            
            task requiresTask2 {
                requires "test"
                doLast {
                    def testDeployment = services.get(DeploymentRegistry).get("test", TestDeploymentHandle)
                    assert testDeployment != null
                    assert testDeployment.running
                }
            }

            task assertDeployment {
                dependsOn requiresTask1, requiresTask2
            }
        """

        expect:
        succeeds("assertDeployment")
    }

    def getTestDeploymentImpl() {
        return """
            import org.gradle.deployment.*
            
            deployments.registerBinding(TestDeploymentSpec.class, DefaultTestDeploymentSpec.class)
            
            class TestDeploymentState { }
            
            class TestDeploymentHandle implements DeploymentHandle<TestDeploymentState> {
                boolean running
                
                boolean isRunning() { return running }

                void start(Deployment deployment) { 
                    println "Starting service..."
                    running = true
                }
            
                void stop() { 
                    running = false
                }
            
                TestDeploymentState getDeploymentState() { return new TestDeploymentState() }
            } 
            
            interface TestDeploymentSpec extends DeploymentSpec<TestDeploymentHandle> {
            }
            
            class DefaultTestDeploymentSpec implements TestDeploymentSpec, Named {
                String name
                String foo
                
                DefaultTestDeploymentSpec(String name) {
                    this.name = name
                }
                
                String getName() {
                    return name
                }
                
                Class<TestDeploymentHandle> getDeploymentHandleClass() {
                    return TestDeploymentHandle.class
                }
                
                Object[] getConstructorArgs() {
                    return [] as Object[]
                }
            }
        """
    }
}
