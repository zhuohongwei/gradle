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

package org.gradle.java

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class LazySourceSetIntegrationTest extends AbstractIntegrationSpec {
    def "lazy source set"() {
        buildFile << """
//            apply plugin: "java-base"
            class SourceSet implements Named {
                final String name
                SourceSet(String name) {
                    this.name = name
                }
            }
            def sourceSets = project.container(SourceSet)
   
            tasks.addStuffLater { creator ->
                println("ADDING STUFF LATER")
                sourceSets.each { ss ->
                    creator.register("stuff" + ss.name.capitalize()) {
                        println("Hourray! " + ss.name)
                        doLast {
                            println "executing " + it.name
                        }
                    }
                }
            }
            
            sourceSets.register("foo") {
                println("configuring foo")
            }
            sourceSets.register("bar") {
                println("configuring bar")
            }
            
            tasks.each { println it.name }
"""
        expect:
        succeeds("help")
        outputDoesNotContain("configuring foo")
        outputDoesNotContain("configuring bar")
        outputDoesNotContain("Hourray! foo")
        outputDoesNotContain("Hourray! bar")

        succeeds("stuffFoo")
        outputContains("configuring foo")
        outputContains("configuring bar")
        outputContains("Hourray! foo")
        outputDoesNotContain("Hourray! bar")
        result.assertTasksExecutedAndNotSkipped(":stuffFoo")
    }
}
