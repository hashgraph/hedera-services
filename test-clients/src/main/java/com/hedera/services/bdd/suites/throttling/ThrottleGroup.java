package com.hedera.services.bdd.suites.throttling;

import com.hederahashgraph.api.proto.java.HederaFunctionality;

import java.util.ArrayList;
import java.util.List;

public class ThrottleGroup {
	int opsPerSec;
	List<HederaFunctionality> operations = new ArrayList<>();

	public int getOpsPerSec() {
		return opsPerSec;
	}

	public int getMilliOpsPerSec() {
		return opsPerSec * 1_000;
	}

	public void setOpsPerSec(int opsPerSec) {
		this.opsPerSec = opsPerSec;
	}

	public List<HederaFunctionality> getOperations() {
		return operations;
	}

	public void setOperations(List<HederaFunctionality> operations) {
		this.operations = operations;
	}

	public static ThrottleGroup fromProto(com.hederahashgraph.api.proto.java.ThrottleGroup group) {
		var pojo = new ThrottleGroup();
		pojo.setOpsPerSec(group.getOpsPerSec());
		pojo.operations.addAll(group.getOperationsList());
		return pojo;
	}

	public com.hederahashgraph.api.proto.java.ThrottleGroup toProto() {
		return com.hederahashgraph.api.proto.java.ThrottleGroup.newBuilder()
				.setOpsPerSec(opsPerSec)
				.addAllOperations(operations)
				.build();
	}
}
