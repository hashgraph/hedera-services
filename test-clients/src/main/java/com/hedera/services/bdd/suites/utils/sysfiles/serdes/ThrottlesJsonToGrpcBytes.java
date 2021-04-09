package com.hedera.services.bdd.suites.utils.sysfiles.serdes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.ThrottleDefinitions;

public class ThrottlesJsonToGrpcBytes implements SysFileSerde<String> {
	private final ObjectMapper mapper = new ObjectMapper();

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
		try {
			var pojo = mapper.readValue(styledFile, com.hedera.services.sysfiles.domain.throttling.ThrottleDefinitions.class);
			return pojo.toProto().toByteArray();
		} catch (Exception e) {
			throw new IllegalArgumentException("Unusable styled throttle definitions", e);
		}
	}

	@Override
	public String preferredFileName() {
		return "throttles.json";
	}
}
