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

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.GradleException;
import org.gradle.authentication.Authentication;
import org.gradle.caching.BuildCacheService;
import org.gradle.caching.BuildCacheServiceFactory;
import org.gradle.caching.http.Http2BuildCache;
import org.gradle.caching.http.HttpBuildCacheCredentials;
import org.gradle.internal.authentication.DefaultBasicAuthentication;
import org.gradle.internal.resource.transport.http.DefaultHttpSettings;
import org.gradle.internal.resource.transport.http.SslContextFactory;

import javax.inject.Inject;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;

/**
 * Build cache factory for HTTP backend.
 */
public class DefaultHttp2BuildCacheServiceFactory implements BuildCacheServiceFactory<Http2BuildCache> {

    private final SslContextFactory sslContextFactory;

    @Inject
    public DefaultHttp2BuildCacheServiceFactory(SslContextFactory sslContextFactory) {
        this.sslContextFactory = sslContextFactory;
    }

    @Override
    public BuildCacheService createBuildCacheService(Http2BuildCache configuration, Describer describer) {
        URI url = configuration.getUrl();
        if (url == null) {
            throw new IllegalStateException("HTTP build cache has no URL configured");
        }
        URI noUserInfoUrl = stripUserInfo(url);

        HttpBuildCacheCredentials credentials = configuration.getCredentials();
        if (!credentialsPresent(credentials) && url.getUserInfo() != null) {
            credentials = extractCredentialsFromUserInfo(url);
        }

        Collection<Authentication> authentications = Collections.emptyList();
        if (credentialsPresent(credentials)) {
            DefaultBasicAuthentication basicAuthentication = new DefaultBasicAuthentication("basic");
            basicAuthentication.setCredentials(credentials);
            authentications = Collections.<Authentication>singleton(basicAuthentication);
        }

        boolean authenticated = !authentications.isEmpty();
        boolean allowUntrustedServer = configuration.isAllowUntrustedServer();

        describer.type("HTTP/2")
            .config("url", noUserInfoUrl.toASCIIString())
            .config("authenticated", Boolean.toString(authenticated))
            .config("allowUntrustedServer", Boolean.toString(allowUntrustedServer));

        DefaultHttpSettings.Builder builder = DefaultHttpSettings.builder()
            .withAuthenticationSettings(authentications)
            .followRedirects(false);
        if (allowUntrustedServer) {
            builder.allowUntrustedConnections();
        } else {
            builder.withSslContextFactory(sslContextFactory);
        }
        HttpClientFactory httpClientFactory = new HttpClientFactory(builder.build());


        return new Http2BuildCacheService(noUserInfoUrl, httpClientFactory);
    }

    @VisibleForTesting
    static HttpBuildCacheCredentials extractCredentialsFromUserInfo(URI url) {
        HttpBuildCacheCredentials credentials = new HttpBuildCacheCredentials();
        String userInfo = url.getUserInfo();
        int indexOfSeparator = userInfo.indexOf(':');
        if (indexOfSeparator > -1) {
            String username = userInfo.substring(0, indexOfSeparator);
            String password = userInfo.substring(indexOfSeparator + 1);
            credentials.setUsername(username);
            credentials.setPassword(password);
        }
        return credentials;
    }

    private static URI stripUserInfo(URI uri) {
        try {
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
        } catch (URISyntaxException e) {
            throw new GradleException("Error constructing URL for http build cache", e);
        }
    }

    private static boolean credentialsPresent(HttpBuildCacheCredentials credentials) {
        return credentials.getUsername() != null && credentials.getPassword() != null;
    }

}
