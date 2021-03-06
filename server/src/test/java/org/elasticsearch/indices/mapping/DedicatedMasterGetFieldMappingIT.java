/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.indices.mapping;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.Node;
import org.junit.Before;

import static org.elasticsearch.test.ESIntegTestCase.ClusterScope;
import static org.elasticsearch.test.ESIntegTestCase.Scope;

@ClusterScope(scope = Scope.TEST, numDataNodes = 0)
public class DedicatedMasterGetFieldMappingIT extends SimpleGetFieldMappingsIT {

    @Override
    protected boolean forbidPrivateIndexSettings() {
        return false;
    }

    @Before
    public void before1() throws Exception {
        Settings settings = Settings.builder()
                .put(Node.NODE_DATA_SETTING.getKey(), false)
                .build();
        internalCluster().startNodes(settings, Settings.EMPTY);
    }

}
