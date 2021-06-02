package com.hedera.services.test;

import com.hedera.services.usage.state.UsageAccumulator;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;

import static com.hederahashgraph.fee.FeeBuilder.FEE_MATRICES_CONST;

public class AdapterUtils {
	public static FeeData feeDataFrom(UsageAccumulator usage) {
		var usages = FeeData.newBuilder();

		var network = FeeComponents.newBuilder()
				.setConstant(FEE_MATRICES_CONST)
				.setBpt(usage.getNetworkBpt())
				.setVpt(usage.getNetworkVpt())
				.setRbh(usage.getNetworkRbh());
		var node = FeeComponents.newBuilder()
				.setConstant(FEE_MATRICES_CONST)
				.setBpt(usage.getNodeBpt())
				.setVpt(usage.getNodeVpt())
				.setBpr(usage.getNodeBpr())
				.setSbpr(usage.getNodeSbpr());
		var service = FeeComponents.newBuilder()
				.setConstant(FEE_MATRICES_CONST)
				.setRbh(usage.getServiceRbh())
				.setSbh(usage.getServiceSbh());
		return usages
				.setNetworkdata(network)
				.setNodedata(node)
				.setServicedata(service)
				.build();
	}
}
