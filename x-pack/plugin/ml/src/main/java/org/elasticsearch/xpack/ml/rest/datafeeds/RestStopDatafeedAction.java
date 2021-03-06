/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.ml.rest.datafeeds;

import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestResponse;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.rest.action.RestBuilderListener;
import org.elasticsearch.xpack.ml.MachineLearning;
import org.elasticsearch.xpack.core.ml.action.StopDatafeedAction;
import org.elasticsearch.xpack.core.ml.action.StopDatafeedAction.Response;
import org.elasticsearch.xpack.core.ml.datafeed.DatafeedConfig;

import java.io.IOException;

public class RestStopDatafeedAction extends BaseRestHandler {

    public RestStopDatafeedAction(Settings settings, RestController controller) {
        super(settings);
        controller.registerHandler(RestRequest.Method.POST, MachineLearning.BASE_PATH + "datafeeds/{"
                + DatafeedConfig.ID.getPreferredName() + "}/_stop", this);
        controller.registerHandler(RestRequest.Method.POST, MachineLearning.V7_BASE_PATH + "datafeeds/{"
                + DatafeedConfig.ID.getPreferredName() + "}/_stop", this);
    }

    @Override
    public String getName() {
        return "xpack_ml_stop_datafeed_action";
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest restRequest, NodeClient client) throws IOException {
        String datafeedId = restRequest.param(DatafeedConfig.ID.getPreferredName());
        StopDatafeedAction.Request request;
        if (restRequest.hasContentOrSourceParam()) {
            XContentParser parser = restRequest.contentOrSourceParamParser();
            request = StopDatafeedAction.Request.parseRequest(datafeedId, parser);
        } else {
            request = new StopDatafeedAction.Request(datafeedId);
            if (restRequest.hasParam(StopDatafeedAction.Request.TIMEOUT.getPreferredName())) {
                TimeValue stopTimeout = restRequest.paramAsTime(
                        StopDatafeedAction.Request.TIMEOUT.getPreferredName(), StopDatafeedAction.DEFAULT_TIMEOUT);
                request.setStopTimeout(stopTimeout);
            }
            if (restRequest.hasParam(StopDatafeedAction.Request.FORCE.getPreferredName())) {
                request.setForce(restRequest.paramAsBoolean(StopDatafeedAction.Request.FORCE.getPreferredName(), request.isForce()));
            }
            if (restRequest.hasParam(StopDatafeedAction.Request.ALLOW_NO_DATAFEEDS.getPreferredName())) {
                request.setAllowNoDatafeeds(restRequest.paramAsBoolean(StopDatafeedAction.Request.ALLOW_NO_DATAFEEDS.getPreferredName(),
                        request.allowNoDatafeeds()));
            }
        }
        return channel -> client.execute(StopDatafeedAction.INSTANCE, request, new RestBuilderListener<Response>(channel) {

            @Override
            public RestResponse buildResponse(Response response, XContentBuilder builder) throws Exception {
                builder.startObject();
                builder.field("stopped", response.isStopped());
                builder.endObject();
                return new BytesRestResponse(RestStatus.OK, builder);
            }
        });
    }
}
