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

package org.gradle.internal.vfs.impl


import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Ignore
import spock.lang.Specification

import java.nio.file.Paths

class RootNodeTest extends Specification {

    @Requires(TestPrecondition.UNIX)
    def "can add unix style children"() {
        def node = new RootNode()

        def directChild = node
            .replaceDescendant(Paths.get("/var"), { parent -> new DefaultNode(Paths.get("/var")) })
        def existingDirectChild = node.getDescendant(Paths.get("/var"))
        def childPath = "${File.separator}var"
        expect:
        directChild.absolutePath.toString() == childPath
        existingDirectChild.absolutePath.toString() == childPath
        directChild
            .replaceDescendant(Paths.get("log")) { parent -> new DefaultNode(Paths.get("/var/log")) }
            .absolutePath.toString() == ["", "var", "log"].join(File.separator)
        existingDirectChild.getDescendant(Paths.get("log")).absolutePath.toString() == ["", "var", "log"].join(File.separator)
        node.getDescendant(Paths.get("/var/log")).absolutePath.toString() == ["", "var", "log"].join(File.separator)
    }

    @Ignore("Ignore for now - let's fix Windows later")
    @Requires(TestPrecondition.WINDOWS)
    def "can add Windows style children"() {
        def node = new RootNode()

        def directChild = node
            .replaceDescendant(Paths.get("C:\\")) { parent -> new DefaultNode(Paths.get("C:")) }
        def existingDirectChild = node.getDescendant(Paths.get("C:"))
        expect:
        directChild.absolutePath.toString() == "C:"
        existingDirectChild.absolutePath.toString() == "C:"
        directChild
            .replaceDescendant(Paths.get("Users")) { parent -> new DefaultNode(Paths.get("C:\\Users")) }
            .absolutePath.toString() == ["C:", "Users"].join(File.separator)
        existingDirectChild.getDescendant(Paths.get("Users")).absolutePath.toString() == ["C:", "Users"].join(File.separator)
    }
}
