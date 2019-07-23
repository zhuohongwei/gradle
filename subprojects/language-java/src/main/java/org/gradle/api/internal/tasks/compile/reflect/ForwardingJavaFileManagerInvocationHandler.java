/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.reflect;

import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.Set;

import static org.gradle.api.internal.tasks.compile.filter.AnnotationProcessorFilter.getFilteredClassLoader;

/**
 * Intercepts JavaFileManager calls to ignore files on the sourcepath.
 */
public class ForwardingJavaFileManagerInvocationHandler implements InvocationHandler {
    public static final String GET_CLASS_LOADER = "getClassLoader";
    private static final String HAS_LOCATION_METHOD = "hasLocation";
    private static final String LIST_METHOD = "list";
    private final Object proxied;
    private final boolean hasEmptySourcePaths;

    public ForwardingJavaFileManagerInvocationHandler(Object proxied, boolean hasEmptySourcePaths) {
        this.proxied = proxied;
        this.hasEmptySourcePaths = hasEmptySourcePaths;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getName().equals(HAS_LOCATION_METHOD) && hasEmptySourcePaths) {
            // There is currently a requirement in the JDK9 javac implementation
            // that when javac is invoked with an explicitly empty sourcepath
            // (i.e. {@code --sourcepath ""}), it won't allow you to compile a java 9
            // module. However, we really want to explicitly set an empty sourcepath
            // so that we don't implicitly pull in unrequested sourcefiles which
            // haven't been snapshotted because we will consider the task up-to-date
            // if the implicit files change.
            //
            // This implementation of hasLocation() pretends that the JavaFileManager
            // has no concept of a source path.
            if (args[0].equals(StandardLocation.SOURCE_PATH)) {
                return false;
            }
        }
        if (method.getName().equals(LIST_METHOD) && hasEmptySourcePaths) {
            // If we are pretending that we don't have a sourcepath, the compiler will
            // look on the classpath for sources. Since we don't want to bring in any
            // sources implicitly from the classpath, we have to ignore source files
            // found on the classpath.
            if (args[0].equals(StandardLocation.CLASS_PATH)) {
                ((Set<JavaFileObject.Kind>) args[2]).remove(JavaFileObject.Kind.SOURCE);
            }
        }

        if (method.getName().equals(GET_CLASS_LOADER) && args[0] == StandardLocation.ANNOTATION_PROCESSOR_PATH) {
            // Injects a filtering classloader when the compiler uses the standard java annotation processor path.
            // This prevents Gradle classes or external libraries from being visible on the annotation processor path.
            ClassLoader classLoader = (ClassLoader) method.invoke(proxied, args);
            if (classLoader instanceof URLClassLoader) {
                return new URLClassLoader(((URLClassLoader) classLoader).getURLs(), getFilteredClassLoader(classLoader.getParent()));
            } else {
                return classLoader;
            }
        }

        return method.invoke(proxied, args);
    }
}
