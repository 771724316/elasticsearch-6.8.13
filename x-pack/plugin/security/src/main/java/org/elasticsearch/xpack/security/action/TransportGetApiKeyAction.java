/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.security.action;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.HandledTransportAction;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;
import org.elasticsearch.xpack.core.security.action.GetApiKeyAction;
import org.elasticsearch.xpack.core.security.action.GetApiKeyRequest;
import org.elasticsearch.xpack.core.security.action.GetApiKeyResponse;
import org.elasticsearch.xpack.security.authc.ApiKeyService;

public final class TransportGetApiKeyAction extends HandledTransportAction<GetApiKeyRequest, GetApiKeyResponse> {

    private final ApiKeyService apiKeyService;

    @Inject
    public TransportGetApiKeyAction(Settings settings, ThreadPool threadPool, TransportService transportService,
            ActionFilters actionFilters, ApiKeyService apiKeyService, IndexNameExpressionResolver indexNameExpressionResolver) {
        super(settings, GetApiKeyAction.NAME, threadPool, transportService, actionFilters, indexNameExpressionResolver,
                GetApiKeyRequest::new);
        this.apiKeyService = apiKeyService;
    }

    @Override
    protected void doExecute(GetApiKeyRequest request, ActionListener<GetApiKeyResponse> listener) {
        if (Strings.hasText(request.getRealmName()) || Strings.hasText(request.getUserName())) {
            apiKeyService.getApiKeysForRealmAndUser(request.getRealmName(), request.getUserName(), listener);
        } else if (Strings.hasText(request.getApiKeyId())) {
            apiKeyService.getApiKeyForApiKeyId(request.getApiKeyId(), listener);
        } else if (Strings.hasText(request.getApiKeyName())) {
            apiKeyService.getApiKeyForApiKeyName(request.getApiKeyName(), listener);
        } else {
            listener.onFailure(new IllegalArgumentException("One of [api key id, api key name, username, realm name] must be specified"));
        }
    }

}
