package com.hedera.services.bdd.suites.utils.sysfiles;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScopedResourcePricesPojo {
	ResourcePricesPojo nodedata, networkdata, servicedata;

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
