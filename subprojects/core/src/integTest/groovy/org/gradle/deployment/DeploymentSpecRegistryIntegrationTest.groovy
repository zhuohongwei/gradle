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
                    assert testDeployment.starts == 1
                }
            }
            
            task requiresTask2 {
                requires "test"
                doLast {
                    def testDeployment = services.get(DeploymentRegistry).get("test", TestDeploymentHandle)
                    assert testDeployment != null
                    assert testDeployment.running
                    assert testDeployment.starts == 1
                }
            }

            task assertDeployment {
                dependsOn requiresTask1, requiresTask2
            }
        """

        expect:
        succeeds("assertDeployment")

        and:
        result.assertHasPostBuildOutput("Stopping 'bar' service")
    }

    def "can configure the deployment state on startup"() {
        buildFile << """
            ${testDeploymentImpl}

            import org.gradle.deployment.internal.DeploymentRegistry

            deployments {
                test(TestDeploymentSpec) {
                    foo = "bar"
                }
            }

            task predecessor {
                doLast {
                    def testDeployment = services.get(DeploymentRegistry).get("test", TestDeploymentHandle)
                    assert testDeployment == null
                }
            }

            task assertDeployment {
                dependsOn predecessor
                requires "test"
                doLast {
                    def testDeployment = services.get(DeploymentRegistry).get("test", TestDeploymentHandle)
                    assert testDeployment.deploymentState.port == 1234
                }
            }
        """

        expect:
        succeeds("assertDeployment")
    }

    def "can register a task to use multiple deployments"() {
        buildFile << """
            ${testDeploymentImpl}

            import org.gradle.deployment.internal.DeploymentRegistry

            deployments {
                test1(TestDeploymentSpec) {
                    foo = "bar"
                }
                test2(TestDeploymentSpec) {
                    foo = "baz"
                }
            }
            
            task assertDeployment {
                requires "test1"
                requires "test2"
                doLast {
                    def testDeployment1 = services.get(DeploymentRegistry).get("test1", TestDeploymentHandle)
                    assert testDeployment1 != null
                    assert testDeployment1.running
                    assert testDeployment1.starts == 1

                    def testDeployment2 = services.get(DeploymentRegistry).get("test2", TestDeploymentHandle)
                    assert testDeployment2 != null
                    assert testDeployment2.running
                    assert testDeployment2.starts == 1
                }
            }          
        """

        expect:
        succeeds("assertDeployment")

        and:
        result.assertHasPostBuildOutput("Stopping 'bar' service")
        result.assertHasPostBuildOutput("Stopping 'baz' service")
    }

    def getTestDeploymentImpl() {
        return """
            import org.gradle.deployment.*
            import javax.inject.Inject
            
            deployments.registerBinding(TestDeploymentSpec.class, DefaultTestDeploymentSpec.class)
            
            class TestDeploymentState { 
                int port
            }
            
            class TestDeploymentHandle implements DeploymentHandle<TestDeploymentState> {
                boolean running
                int starts = 0
                TestDeploymentState state = new TestDeploymentState()
                String foo
                
                @Inject
                TestDeploymentHandle(String foo) {
                    this.foo = foo
                }
                
                boolean isRunning() { return running }

                void start(Deployment deployment) { 
                    println "Starting '\$foo' service..."
                    starts++
                    state.port = 1234
                    running = true
                }
            
                void stop() { 
                    println "Stopping '\$foo' service..."
                    running = false
                }
            
                TestDeploymentState getDeploymentState() { return state }
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
                    return [foo] as Object[]
                }
            }
        """
    }
}
