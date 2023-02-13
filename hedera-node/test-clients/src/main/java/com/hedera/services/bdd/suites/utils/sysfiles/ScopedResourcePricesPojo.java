/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.suites.utils.sysfiles;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hederahashgraph.api.proto.java.SubType;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScopedResourcePricesPojo {
    SubType subType;

    ResourcePricesPojo nodedata, networkdata, servicedata;

    public SubType getSubType() {
        return subType;
    }

    public void setSubType(SubType subType) {
        this.subType = subType;
    }

    public ResourcePricesPojo getNodedata() {
        return nodedata;
    }

    public void setNodedata(ResourcePricesPojo nodedata) {
        this.nodedata = nodedata;
    }

    public ResourcePricesPojo getNetworkdata() {
        return networkdata;
    }

    public void setNetworkdata(ResourcePricesPojo networkdata) {
        this.networkdata = networkdata;
    }

    public ResourcePricesPojo getServicedata() {
        return servicedata;
    }

    public void setServicedata(ResourcePricesPojo servicedata) {
        this.servicedata = servicedata;
    }
}
