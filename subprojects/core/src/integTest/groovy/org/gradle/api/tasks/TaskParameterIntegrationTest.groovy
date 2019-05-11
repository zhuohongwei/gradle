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

package org.gradle.api.tasks

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.TaskWithParameters
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.provider.PropertyInternal
import org.gradle.api.model.ReplacedBy
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class TaskParameterIntegrationTest extends AbstractIntegrationSpec {
    def makeLegacySample() {
        file('buildSrc/src/main/java/CustomTask.java') << """
    import ${DefaultTask.canonicalName};
    import ${File.canonicalName};
    import ${ReplacedBy.canonicalName};
    import ${Action.canonicalName};
    import ${Nested.canonicalName};
    import ${TaskAction.canonicalName};
    import ${TaskWithParameters.canonicalName};
    import ${Provider.canonicalName};
    import ${PropertyInternal.canonicalName};

    public class CustomTask extends TaskWithParameters<CustomTaskParameters> {
        private CustomTaskParameters parameters = getProject().getObjects().newInstance(CustomTaskParameters.class);
        
        @Override
        public CustomTaskParameters getParameters() {
            return parameters;
        }
        
        @ReplacedBy("parameters.executable")
        public File getExecutable() {
            return parameters.getExecutable().get().getAsFile();
        }

        public void setExecutable(File executable) {
            parameters.getExecutable().set(executable);
        }

        @ReplacedBy("parameters.mainClass")
        public String getMainClass() {
            return parameters.getMainClass().get();
        }

        public void setMainClass(String mainClass) {
            parameters.getMainClass().set(mainClass);
        }

        @ReplacedBy("parameters.ignored")
        public boolean getIgnored() {
            return parameters.getIgnored().get();
        }

        public void setIgnored(boolean ignored) {
            parameters.getIgnored().set(ignored);
        }
        
        @TaskAction
        private void doAction() {
            System.out.println("Executable: " + getExecutable().getAbsolutePath());
            System.out.println("MainClass: " + getMainClass());
            System.out.println("Ignored: " + getIgnored());
        }
        
        
        // Generated
//        public void setExecutable(Provider<?> p) {
//            ((PropertyInternal)getParameters().getExecutable()).setFromAnyValue(p);
//        }
//        
//        public void setMainClass(Provider<? extends String> p) {
//            getParameters().getMainClass().set(p);
//        }
//        
//        public void setIgnored(Provider<? extends Boolean> p) {
//            getParameters().getIgnored().set(p);
//        }
//        
//        public void executable(Provider<?> p) {
//            ((PropertyInternal)getParameters().getExecutable()).setFromAnyValue(p);
//        }
//        
//        public void mainClass(Provider<? extends String> p) {
//            getParameters().getMainClass().set(p);
//        }
//        
//        public void ignored(Provider<? extends Boolean> p) {
//            getParameters().getIgnored().set(p);
//        }
    }
        """

        file('buildSrc/src/main/kotlin/custom-task-extensions.kt') << """
        import ${PropertyInternal.canonicalName}
        import ${Provider.canonicalName}
        import ${TaskProvider.canonicalName}
        import ${Action.canonicalName}
        import ${TaskWithParameters.canonicalName}

        public fun CustomTask.executable(p: Provider<*>) {
            (getParameters().getExecutable() as PropertyInternal<*>).setFromAnyValue(p);
        }
        
        public fun CustomTask.mainClass(p: Provider<String>) {
            getParameters().getMainClass().set(p);
        }
        
        public fun CustomTask.ignored(p: Provider<Boolean>) {
            getParameters().getIgnored().set(p);
        }

        public fun TaskProvider<CustomTask>.parameters(a: CustomTaskParameters.() -> Unit) {
            configure {
                a(it.parameters)
            }
        }
"""

        file('buildSrc/build.gradle.kts') << """
plugins {
  id("org.jetbrains.kotlin.jvm").version("1.3.31")
}

repositories {
    jcenter()
    mavenCentral()
}
"""

        file('buildSrc/src/main/java/CustomTaskParameters.java') << """
    import ${RegularFileProperty.canonicalName};
    import ${Property.canonicalName};
    import ${InputFile.canonicalName};
    import ${Input.canonicalName};

    public interface CustomTaskParameters {
        @InputFile
        RegularFileProperty getExecutable();
        
        @Input
        Property<String> getMainClass();
        
        @Input
        Property<Boolean> getIgnored();
    }
        """
    }

    def "legacy parameter with provider parameter (groovy)"() {
        makeLegacySample()
        buildFile << """
            file("foo").text = "h"
            task test(type: CustomTask) {
                executable = file('bob')
                parameters.mainClass = 'Main'
                ignored = false
            }
            
            test {
                parameters.executable = file('foo')
                mainClass = "MainEx"
            }
            
            test.parameters {
                ignored = true
            }
            
            tasks.register("test2", CustomTask) {
                executable = file('bob')
                parameters.mainClass = 'Main'
                ignored = false
            }
            
            tasks.named("test2") {
                parameters.executable = file('foo')
                mainClass = "MainEx"
            }
            
            tasks.named("test2") {
                parameters {
                    ignored = true
                }
            }
        """

        expect:
        succeeds("test", "test2")
    }

    def "convention nice! (groovy)"() {
        makeLegacySample()
        buildFile << """
            file("foo").text = "h"
            layout.buildDirectory.get().asFile.mkdir()
            layout.buildDirectory.file('foo').get().asFile.text = "j"
            task test(type: CustomTask) {
                executable = provider { file('bob') }
//                executable layout.buildDirectory.file('bob')
                parameters.mainClass = 'Main'
                ignored = false
            }
            
            test {
                executable = layout.buildDirectory.file('foo')
                mainClass = "MainEx"
            }
            
            test.parameters {
                ignored = true
            }
            
            tasks.register("test2", CustomTask) {
                executable = provider { file('bob') }
//                executable provider { file('bob') }
                parameters.mainClass = 'Main'
                ignored = false
            }
            
            tasks.named("test2") {
                executable = layout.buildDirectory.file('foo')
                mainClass = "MainEx"
            }
            
            tasks.named("test2").parameters {
                ignored = true
            }
        """

        expect:
        succeeds("test", "test2")
    }

    def "convention nice! (kotlin)"() {
        makeLegacySample()
        file("build.gradle.kts") << """
            file("foo").writeText("h")
            layout.buildDirectory.get().asFile.mkdir()
            layout.buildDirectory.file("foo").get().asFile.writeText("j")
            val test by tasks.creating(CustomTask::class) {
                executable = file("bob")
                executable(provider { file("bob") })
                parameters.mainClass.set("Main")
                ignored = false
            }
            
            test.executable(layout.buildDirectory.file("foo"))
            test.mainClass = "MainEx"
            
            test.parameters {
                ignored.set(true)
            }
            
            tasks.register<CustomTask>("test2") {
                executable(provider { file("bob") })
                parameters.mainClass.set("Main")
                ignored = false
            }
            
            tasks.named("test2", CustomTask::class) {
                executable(layout.buildDirectory.file("foo"))
                mainClass = "MainEx"
            }
            
            tasks.named("test2", CustomTask::class).parameters {
                ignored.set(true)
            }
        """

        expect:
        succeeds("test", "test2")
    }
}
