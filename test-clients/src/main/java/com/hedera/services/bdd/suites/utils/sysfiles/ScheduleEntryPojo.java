package com.hedera.services.bdd.suites.utils.sysfiles;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScheduleEntryPojo {
	String hederaFunctionality;
	ScopedResourcePricesPojo feeData;

	public static ScheduleEntryPojo from(TransactionFeeSchedule grpc) {
		var pojo = new ScheduleEntryPojo();

		pojo.setHederaFunctionality(grpc.getHederaFunctionality().toString());

		var nodePrices = ResourcePricesPojo.from(grpc.getFeeData().getNodedata());
		var servicePrices = ResourcePricesPojo.from(grpc.getFeeData().getServicedata());
		var networkPrices = ResourcePricesPojo.from(grpc.getFeeData().getNetworkdata());

		var scopedPrices = new ScopedResourcePricesPojo();
		scopedPrices.setNodedata(nodePrices);
		scopedPrices.setNetworkdata(networkPrices);
		scopedPrices.setServicedata(servicePrices);
		pojo.setFeeData(scopedPrices);

		return pojo;
	}

	public String getHederaFunctionality() {
		return hederaFunctionality;
	}

	public void setHederaFunctionality(String hederaFunctionality) {
		this.hederaFunctionality = hederaFunctionality;
	}

	public ScopedResourcePricesPojo getFeeData() {
		return feeData;
	}

	public void setFeeData(ScopedResourcePricesPojo feeData) {
		this.feeData = feeData;
	}
}
