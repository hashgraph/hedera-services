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
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScheduleEntryPojo {
	String hederaFunctionality;
	List<ScopedResourcePricesPojo> feeDataList;

	public static ScheduleEntryPojo from(TransactionFeeSchedule grpc) {
		var pojo = new ScheduleEntryPojo();

		pojo.setHederaFunctionality(grpc.getHederaFunctionality().toString());

		List<ScopedResourcePricesPojo> feeDataList = new ArrayList<>();

		for (FeeData feeData : grpc.getFeeDataListList()) {
			var subType = feeData.getSubType();
			var nodePrices = ResourcePricesPojo.from(feeData.getNodedata());
			var servicePrices = ResourcePricesPojo.from(feeData.getServicedata());
			var networkPrices = ResourcePricesPojo.from(feeData.getNetworkdata());

			var scopedPrices = new ScopedResourcePricesPojo();
			scopedPrices.setNodedata(nodePrices);
			scopedPrices.setNetworkdata(networkPrices);
			scopedPrices.setServicedata(servicePrices);
			scopedPrices.setSubType(subType);

			feeDataList.add(scopedPrices);
		}

		pojo.setFeeDataList(feeDataList);
		return pojo;
	}

	public String getHederaFunctionality() {
		return hederaFunctionality;
	}

	public void setHederaFunctionality(String hederaFunctionality) {
		this.hederaFunctionality = hederaFunctionality;
	}

	public List<ScopedResourcePricesPojo> getFeeDataList() {
		return feeDataList;
	}

	public void setFeeDataList(List<ScopedResourcePricesPojo> feeData) {
		this.feeDataList = feeData;
	}
}
