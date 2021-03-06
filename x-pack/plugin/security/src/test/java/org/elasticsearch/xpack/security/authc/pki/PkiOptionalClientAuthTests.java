/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.security.authc.pki;

import org.apache.http.nio.conn.ssl.SSLIOSessionStrategy;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.common.network.NetworkModule;
import org.elasticsearch.common.settings.SecureString;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.test.SecuritySettingsSource;
import org.elasticsearch.test.SecuritySettingsSourceField;
import org.elasticsearch.test.SecuritySingleNodeTestCase;
import org.elasticsearch.xpack.core.security.authc.support.UsernamePasswordToken;
import org.elasticsearch.xpack.core.ssl.SSLClientAuth;
import org.junit.BeforeClass;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;

import static org.elasticsearch.test.SecuritySettingsSource.addSSLSettingsForNodePEMFiles;
import static org.hamcrest.Matchers.is;

public class PkiOptionalClientAuthTests extends SecuritySingleNodeTestCase {

    private static int randomClientPort;

    @BeforeClass
    public static void initPort() {
        randomClientPort = randomIntBetween(49000, 65500);
    }

    @Override
    protected Settings nodeSettings() {
        String randomClientPortRange = randomClientPort + "-" + (randomClientPort+100);

        Settings.Builder builder = Settings.builder()
                .put(super.nodeSettings());
        addSSLSettingsForNodePEMFiles(builder, "xpack.security.http.", true);
        builder.put(NetworkModule.HTTP_ENABLED.getKey(), true)
            .put("xpack.security.http.ssl.enabled", true)
            .put("xpack.security.http.ssl.client_authentication", SSLClientAuth.OPTIONAL)
            .put("xpack.security.authc.realms.file.type", "file")
            .put("xpack.security.authc.realms.file.order", "0")
            .put("xpack.security.authc.realms.pki1.type", "pki")
            .put("xpack.security.authc.realms.pki1.order", "1")
            .put("xpack.security.authc.realms.pki1.truststore.path",
                    getDataPath("/org/elasticsearch/xpack/security/transport/ssl/certs/simple/truststore-testnode-only.jks"))
            .put("xpack.security.authc.realms.pki1.files.role_mapping", getDataPath("role_mapping.yml"))
            .put("transport.profiles.want_client_auth.port", randomClientPortRange)
            .put("transport.profiles.want_client_auth.bind_host", "localhost")
            .put("transport.profiles.want_client_auth.xpack.security.ssl.client_authentication", SSLClientAuth.OPTIONAL);

        SecuritySettingsSource.addSecureSettings(builder, secureSettings ->
                secureSettings.setString("xpack.security.authc.realms.pki1.truststore.secure_password", "truststore-testnode-only"));
        return builder.build();

    }

    @Override
    protected boolean transportSSLEnabled() {
        return true;
    }

    public void testRestClientWithoutClientCertificate() throws Exception {
        SSLIOSessionStrategy sessionStrategy = new SSLIOSessionStrategy(getSSLContext());
        try (RestClient restClient = createRestClient(httpClientBuilder -> httpClientBuilder.setSSLStrategy(sessionStrategy), "https")) {
            ResponseException e = expectThrows(ResponseException.class, () -> restClient.performRequest(new Request("GET", "_nodes")));
            assertThat(e.getResponse().getStatusLine().getStatusCode(), is(401));

            Request request = new Request("GET", "_nodes");
            RequestOptions.Builder options = request.getOptions().toBuilder();
            options.addHeader("Authorization", UsernamePasswordToken.basicAuthHeaderValue(SecuritySettingsSource.TEST_USER_NAME,
                    new SecureString(SecuritySettingsSourceField.TEST_PASSWORD.toCharArray())));
            request.setOptions(options);
            Response response = restClient.performRequest(request);
            assertThat(response.getStatusLine().getStatusCode(), is(200));
        }
    }

    private SSLContext getSSLContext() throws Exception {
        SSLContext sc = SSLContext.getInstance("TLSv1.2");
        Path truststore = getDataPath("/org/elasticsearch/xpack/security/transport/ssl/certs/simple/truststore-testnode-only.jks");
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (InputStream stream = Files.newInputStream(truststore)) {
            keyStore.load(stream, "truststore-testnode-only".toCharArray());
        }
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(keyStore);
        sc.init(null, factory.getTrustManagers(), new SecureRandom());
        return sc;
    }
}
