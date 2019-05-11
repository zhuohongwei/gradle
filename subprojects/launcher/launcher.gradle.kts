
import org.gradle.build.GradleStartScriptGenerator
import org.gradle.gradlebuild.test.integrationtests.IntegrationTest
import org.gradle.gradlebuild.testing.integrationtests.cleanup.WhenNotEmpty
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    gradlebuild.classycle
    id("com.palantir.graal") version ("0.3.0-27-gf54868f")
}

dependencies {
    compile(project(":baseServices"))
    compile(project(":jvmServices"))
    compile(project(":core"))
    compile(project(":cli"))
    compile(project(":buildOption"))
    compile(project(":toolingApi"))
    compile(project(":native"))
    compile(project(":logging"))
    compile(project(":docs"))

    compile(library("asm"))
    compile(library("commons_io"))
    compile(library("commons_lang"))
    compile(library("slf4j_api"))

    integTestCompile(project(":internalIntegTesting"))
    integTestRuntime(project(":plugins"))
    integTestRuntime(project(":languageNative"))

    testFixturesApi(project(":internalIntegTesting"))
}

val availableJavaInstallations = rootProject.availableJavaInstallations

// Needed for testing debug command line option (JDWPUtil)
val javaInstallationForTest = availableJavaInstallations.javaInstallationForTest
if (!javaInstallationForTest.javaVersion.isJava9Compatible) {
    dependencies {
        integTestRuntime(files(javaInstallationForTest.toolsJar))
    }
}

// If running on Java 8 but compiling with Java 9, Groovy code would still be compiled by Java 8, so here we need the tools.jar
val currentJavaInstallation = availableJavaInstallations.currentJavaInstallation
if (currentJavaInstallation.javaVersion.isJava8) {
    dependencies {
        integTestCompileOnly(files(currentJavaInstallation.toolsJar))
    }
}

gradlebuildJava {
    moduleType = ModuleType.ENTRY_POINT
}

testFixtures {
    from(":core")
    from(":languageJava")
    from(":messaging")
    from(":logging")
    from(":toolingApi")
}

val integTestTasks: DomainObjectCollection<IntegrationTest> by extra
integTestTasks.configureEach {
    maxParallelForks = Math.min(3, project.maxParallelForks)
}

val configureJar by tasks.registering {
    doLast {
        val classpath = listOf(":baseServices", ":coreApi", ":core").joinToString(" ") {
            project(it).tasks.jar.get().archivePath.name
        }
        tasks.jar.get().manifest.attributes("Class-Path" to classpath)
    }
}

tasks.jar {
    dependsOn(configureJar)
    manifest.attributes("Main-Class" to "org.gradle.launcher.GradleMain")
}

val startScripts = tasks.register<GradleStartScriptGenerator>("startScripts") {
    startScriptsDir = file("$buildDir/startScripts")
    launcherJar = tasks.jar.get().outputs.files
}

configurations {
    create("gradleScriptsElements") {
        isVisible = false
        isCanBeResolved = false
        isCanBeConsumed = true
        attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, "start-scripts"))
        // TODO: Update GradleStartScriptGenerator to retain dependency information with Provider API
        outgoing.artifact(startScripts.map { it.startScriptsDir }) {
            builtBy(startScripts)
        }
    }
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}

graal {
    mainClass("org.gradle.launcher.GradleMain")
    outputName("launcher-native")
    graalVersion("1.0.0-rc16")
    option("--report-unsupported-elements-at-runtime")
    option("--allow-incomplete-classpath")
    option("-H:ReflectionConfigurationFiles=${file("graal-reflect.json")}")
    option("-H:DynamicProxyConfigurationFiles=${file("graal-proxies.json")}")
    option("-H:JNIConfigurationFiles=${file("graal-jni.json")}")
    option("-H:ResourceConfigurationFiles=${file("graal-resources.json")}")
    option("-H:Log=registerResource:")
    option("-H:DelayClassInitialization=org.gradle.internal.nativeintegration.services.NativeServices,org.gradle.internal.nativeintegration.jansi.JansiBootPathConfigurer")
}
