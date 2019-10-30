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

package org.gradle.api.internal.tasks.scala;

import org.gradle.api.GradleException;
import scala.collection.JavaConverters;
import scala.runtime.BoxedUnit;
import scala.tools.nsc.ScalaDocReporter;
import scala.tools.nsc.doc.DocFactory;
import scala.tools.nsc.doc.Settings;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class ScalaDocInvoker {
    public static void invoke(ScalaDocParameters parameters) {
        Map<String, Object> o = parameters.getOptions().get();
        Settings docSettings = new Settings(ScalaDocInvoker::buildError, ScalaDocInvoker::println);
        docSettings.sourcepath().v_$eq(parameters.getSourceFiles().getAsPath());
        Optional.ofNullable(o.get("encoding")).ifPresent(v -> docSettings.encoding().v_$eq(v));
        Optional.ofNullable(o.get("doctitle")).ifPresent(v -> docSettings.doctitle().v_$eq(decodeEscapes(v)));
        Optional.ofNullable(o.get("docfooter")).ifPresent(v -> docSettings.docfooter().v_$eq(decodeEscapes(v)));
        Optional.ofNullable(o.get("docversion")).ifPresent(v -> docSettings.docversion().v_$eq(decodeEscapes(v)));
        Optional.ofNullable(o.get("docsourceurl")).ifPresent(v -> docSettings.docsourceurl().v_$eq(decodeEscapes(v)));
        Optional.ofNullable(o.get("docUncompilable")).ifPresent(v -> docSettings.docUncompilable().v_$eq(decodeEscapes(v)));

        // Boolean
        Optional.ofNullable(o.get("deprecation")).ifPresent(v -> docSettings.deprecation().v_$eq(decodeEscapes(v)));
        Optional.ofNullable(o.get("unchecked")).ifPresent(v -> docSettings.unchecked().v_$eq(decodeEscapes(v)));
        Optional.ofNullable(o.get("docImplicits")).ifPresent(v -> docSettings.docImplicits().v_$eq(decodeEscapes(v)));
        Optional.ofNullable(o.get("docImplicitsShowAll")).ifPresent(v -> docSettings.docImplicitsShowAll().v_$eq(decodeEscapes(v)));
        Optional.ofNullable(o.get("docDiagrams")).ifPresent(v -> docSettings.docDiagrams().v_$eq(decodeEscapes(v)));
        Optional.ofNullable(o.get("docDiagramsDebug")).ifPresent(v -> docSettings.docDiagramsDebug().v_$eq(decodeEscapes(v)));
        Optional.ofNullable(o.get("docRawOutput")).ifPresent(v -> docSettings.docRawOutput().v_$eq(decodeEscapes(v)));
        Optional.ofNullable(o.get("docNoPrefixes")).ifPresent(v -> docSettings.docNoPrefixes().v_$eq(decodeEscapes(v)));
        Optional.ofNullable(o.get("docGroups")).ifPresent(v -> docSettings.docGroups().v_$eq(decodeEscapes(v)));
        Optional.ofNullable(o.get("docSkipPackages")).ifPresent(v -> docSettings.docSkipPackages().v_$eq(decodeEscapes(v)));

        Optional.ofNullable(o.get("docDiagramsDotPath")).ifPresent(v -> docSettings.docDiagramsDotPath().v_$eq(v));

        Optional.ofNullable(o.get("docgenerator")).ifPresent(v -> docSettings.docgenerator().v_$eq(v));
        Optional.ofNullable(o.get("docrootcontent")).ifPresent(v -> docSettings.docRootContent().v_$eq(v));


        ScalaDocReporter reporter = new ScalaDocReporter(docSettings);
        DocFactory docProcessor = new DocFactory(reporter, docSettings);


        List<String> s = parameters.getSourceFiles().getFiles().stream().map(File::getAbsolutePath).collect(Collectors.toList());


        docProcessor.document(scala.collection.immutable.List.from(JavaConverters.asScala(s.iterator())));
        if (reporter.errorCount() > 0) {
            throw new GradleException("Document failed with " +
                    reporter.errorCount() + " error" +
                    (reporter.errorCount() > 1 ? "s" : "") +
                    "; see the documenter error output for details.");
        } else if (reporter.warningCount() > 0) {
            System.out.println(
                    "Document succeeded with " +
                            reporter.warningCount() + " warning" +
                            (reporter.warningCount() > 1 ? "s" : "") +
                            "; see the documenter output for details.");
        }
        /*reporter.printSummary()*/
        reporter.finish();
    }

    private static BoxedUnit buildError(String message) {
        throw new GradleException(message);
    }

    private static BoxedUnit println(String message) {
        System.out.println(message);
        return BoxedUnit.UNIT;
    }

    private static String decodeEscapes(Object s) {
        // In Ant script characters '<' and '>' must be encoded when
        // used in attribute values, e.g. for attributes "doctitle", "header", ..
        // in task Scaladoc you may write:
        //   doctitle="&lt;div&gt;Scala&lt;/div&gt;"
        // so we have to decode them here.
        return s.toString().replaceAll("&lt;", "<").replaceAll("&gt;",">")
                .replaceAll("&amp;", "&").replaceAll("&quot;", "\"");
    }
}
