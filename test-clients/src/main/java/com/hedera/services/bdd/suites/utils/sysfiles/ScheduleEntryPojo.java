package com.hedera.services.bdd.suites.utils.sysfiles;

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
