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

package org.gradle.caching.http.internal;

import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang.IncompleteArgumentException;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpMessage;
import org.apache.http.HttpStatus;
import org.gradle.api.UncheckedIOException;
import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.BuildCacheService;
import org.gradle.caching.internal.CacheFormat;
import org.gradle.internal.UncheckedException;
import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;

/**
 * Build cache implementation that delegates to a service accessible via HTTP.
 */
public class Http2BuildCacheService implements BuildCacheService {
    private static final Logger LOGGER = LoggerFactory.getLogger(Http2BuildCacheService.class);
    static final String BUILD_CACHE_CONTENT_TYPE = "application/vnd.gradle.build-cache-artifact.v" + CacheFormat.CACHE_ENTRY_FORMAT;

    private static final Set<Integer> FATAL_HTTP_ERROR_CODES = ImmutableSet.of(
        HttpStatus.SC_USE_PROXY,
        HttpStatus.SC_BAD_REQUEST,
        HttpStatus.SC_UNAUTHORIZED, HttpStatus.SC_FORBIDDEN, HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED,
        HttpStatus.SC_METHOD_NOT_ALLOWED,
        HttpStatus.SC_NOT_ACCEPTABLE, HttpStatus.SC_LENGTH_REQUIRED, HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE, HttpStatus.SC_EXPECTATION_FAILED,
        426, // Upgrade required
        HttpStatus.SC_HTTP_VERSION_NOT_SUPPORTED,
        511 // network authentication required
    );

    private final URI root;
    private final HttpClientFactory httpClientFactory;

    public Http2BuildCacheService(URI url, HttpClientFactory httpClientFactory) {
        this.httpClientFactory = httpClientFactory;
        if (!url.getPath().endsWith("/")) {
            throw new IncompleteArgumentException("HTTP cache root URI must end with '/'");
        }
        this.root = url;
    }

    @Override
    public boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
        final URI uri = root.resolve("./" + key.getHashCode());
        HttpRequest request = HttpRequest.newBuilder(uri)
            .GET()
            .headers(
                HttpHeaders.ACCEPT, BUILD_CACHE_CONTENT_TYPE + ", */*",
                "X-Gradle-Version", GradleVersion.current().getVersion()
            )
            .version(HttpClient.Version.HTTP_2)
            .build();


        try {
            HttpResponse<InputStream> response = httpClientFactory.getClient().send(request, HttpResponse.BodyHandlers.ofInputStream());
            int statusCode = response.statusCode();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Response for GET {}: {}", safeUri(uri), statusCode);
            }
            LOGGER.warn("Protocol version for response: {}", response.version());
            if (isHttpSuccess(statusCode)) {
                reader.readFrom(response.body());
                return true;
            } else if (statusCode == HttpStatus.SC_NOT_FOUND) {
                return false;
            } else {
                String defaultMessage = String.format("Loading entry from '%s' response status %d", safeUri(uri), statusCode);
                return throwHttpStatusCodeException(statusCode, defaultMessage);
            }
        } catch (IOException | InterruptedException e) {
            throw wrap(e);
        }
    }

    private boolean isRedirect(int statusCode) {
        return statusCode == HttpStatus.SC_MOVED_PERMANENTLY || statusCode == HttpStatus.SC_MOVED_TEMPORARILY || statusCode == HttpStatus.SC_TEMPORARY_REDIRECT;
    }

    private void addDiagnosticHeaders(HttpMessage request) {
        request.addHeader("X-Gradle-Version", GradleVersion.current().getVersion());
    }

    @Override
    public void store(BuildCacheKey key, final BuildCacheEntryWriter output) throws BuildCacheException {
        final URI uri = root.resolve(key.getHashCode());
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                .PUT(HttpRequest.BodyPublishers.ofFile(output.getFile().toPath()))
                .headers(
                    HttpHeaders.ACCEPT, BUILD_CACHE_CONTENT_TYPE + ", */*",
                    "X-Gradle-Version", GradleVersion.current().getVersion()
                )
                .version(HttpClient.Version.HTTP_2)
                .build();
            HttpResponse<Void> response = httpClientFactory.getClient().send(request, HttpResponse.BodyHandlers.discarding());
            int statusCode = response.statusCode();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Response for PUT {}: {}", safeUri(uri), statusCode);
            }
            LOGGER.warn("Protocol version for response: {}", response.version());

            if (!isHttpSuccess(statusCode)) {
                String defaultMessage = String.format("Storing entry at '%s' response status %d", safeUri(uri), statusCode);
                throwHttpStatusCodeException(statusCode, defaultMessage);
            }
        } catch (IOException | InterruptedException e) {
            throw wrap(e);
        }
    }

    private static BuildCacheException wrap(Throwable e) {
        if (e instanceof Error) {
            throw (Error) e;
        }

        throw new BuildCacheException(e.getMessage(), e);
    }

    private boolean isHttpSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private boolean throwHttpStatusCodeException(int statusCode, String message) {
        if (FATAL_HTTP_ERROR_CODES.contains(statusCode)) {
            throw new UncheckedIOException(message);
        } else {
            throw new BuildCacheException(message);
        }
    }

    @Override
    public void close() throws IOException {
        // TODO: release resources
    }

    /**
     * Create a safe URI from the given one by stripping out user info.
     *
     * @param uri Original URI
     * @return a new URI with no user info
     */
    private static URI safeUri(URI uri) {
        try {
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
        } catch (URISyntaxException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
