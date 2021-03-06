/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.security;

import org.elasticsearch.bootstrap.BootstrapCheck;
import org.elasticsearch.bootstrap.BootstrapContext;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.xpack.core.XPackSettings;
import org.elasticsearch.xpack.core.security.authc.RealmSettings;
import org.elasticsearch.xpack.core.security.authc.pki.PkiRealmSettings;
import org.elasticsearch.xpack.core.ssl.SSLConfiguration;
import org.elasticsearch.xpack.core.ssl.SSLService;

import java.util.ArrayList;
import java.util.List;

import static org.elasticsearch.xpack.core.XPackSettings.HTTP_SSL_ENABLED;
import static org.elasticsearch.xpack.core.security.SecurityField.setting;

class PkiRealmBootstrapCheck implements BootstrapCheck {

    private final SSLService sslService;

    PkiRealmBootstrapCheck(SSLService sslService) {
        this.sslService = sslService;
    }

    /**
     * If a PKI realm is enabled, checks to see if SSL and Client authentication are enabled on at
     * least one network communication layer.
     */
    @Override
    public BootstrapCheckResult check(BootstrapContext context) {
        final Settings settings = context.settings();
        final boolean pkiRealmEnabled = settings.getGroups(RealmSettings.PREFIX).values().stream()
                .filter(s -> PkiRealmSettings.TYPE.equals(s.get("type")))
                .anyMatch(s -> s.getAsBoolean("enabled", true));
        if (pkiRealmEnabled) {
            for (String contextName : getSslContextNames(settings)) {
                final SSLConfiguration configuration = sslService.getSSLConfiguration(contextName);
                if (sslService.isSSLClientAuthEnabled(configuration)) {
                    return BootstrapCheckResult.success();
                }
            }
            return BootstrapCheckResult.failure(
                    "a PKI realm is enabled but cannot be used as neither HTTP or Transport have SSL and client authentication enabled");
        } else {
            return BootstrapCheckResult.success();
        }
    }

    private List<String> getSslContextNames(Settings settings) {
        final List<String> list = new ArrayList<>();
        if (HTTP_SSL_ENABLED.get(settings)) {
            list.add(setting("http.ssl"));
        }

        if (XPackSettings.TRANSPORT_SSL_ENABLED.get(settings)) {
            list.add(setting("transport.ssl"));
            list.addAll(sslService.getTransportProfileContextNames());
        }

        return list;
    }

    @Override
    public boolean alwaysEnforce() {
        return true;
    }
}
