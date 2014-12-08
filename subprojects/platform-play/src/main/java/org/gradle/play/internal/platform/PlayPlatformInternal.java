/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.internal.platform;

import org.gradle.api.JavaVersion;
import org.gradle.language.scala.platform.ScalaPlatform;
import org.gradle.model.Managed;
import org.gradle.platform.base.internal.PlatformManaged;
import org.gradle.play.platform.PlayPlatform;

@Managed
public interface PlayPlatformInternal extends PlayPlatform, PlatformManaged {

    void setPlayVersion(String playVersion);

    void setScalaPlatform(ScalaPlatform scalaPlatform);

    void setTwirlVersion(String twirlVersion);

    void setJavaVersion(JavaVersion javaVersion);
}
