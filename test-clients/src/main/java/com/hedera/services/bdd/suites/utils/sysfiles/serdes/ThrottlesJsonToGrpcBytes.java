package com.hedera.services.bdd.suites.utils.sysfiles.serdes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.ThrottleDefinitions;

public class ThrottlesJsonToGrpcBytes implements SysFileSerde<String> {
	private static final int MINIMUM_NETWORK_SIZE = 1;

	private final int believedNetworkSize;
	private final ObjectMapper mapper = new ObjectMapper();

	public ThrottlesJsonToGrpcBytes() {
		this.believedNetworkSize = MINIMUM_NETWORK_SIZE;
	}

	public ThrottlesJsonToGrpcBytes(int believedNetworkSize) {
		this.believedNetworkSize = believedNetworkSize;
	}

	@Override
	public String fromRawFile(byte[] bytes) {
		try {
			var defs = ThrottleDefinitions.parseFrom(bytes);
			var pojo = com.hedera.services.sysfiles.domain.throttling.ThrottleDefinitions.fromProto(defs);
			return mapper
					.writerWithDefaultPrettyPrinter()
					.writeValueAsString(pojo);
		} catch (InvalidProtocolBufferException | JsonProcessingException e) {
			throw new IllegalArgumentException("Unusable raw throttle definitions!", e);
		}
	}

	@Override
	public byte[] toRawFile(String styledFile) {
		return toPojo(styledFile).toProto().toByteArray();
	}

	@Override
	public byte[] toValidatedRawFile(String styledFile) {
		var pojo = toPojo(styledFile);
		for (var bucket : pojo.getBuckets()) {
			bucket.asThrottleMapping(believedNetworkSize);
		}
		return pojo.toProto().toByteArray();
	}

	private com.hedera.services.sysfiles.domain.throttling.ThrottleDefinitions toPojo(String styledFile) {
		try {
			return mapper.readValue(styledFile, com.hedera.services.sysfiles.domain.throttling.ThrottleDefinitions.class);
		} catch (Exception e) {
			throw new IllegalArgumentException("Unusable styled throttle definitions", e);
		}
	}

	@Override
	public String preferredFileName() {
		return "throttles.json";
	}
}
