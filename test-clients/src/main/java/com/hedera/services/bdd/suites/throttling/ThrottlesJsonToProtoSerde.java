package com.hedera.services.bdd.suites.throttling;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;

public class ThrottlesJsonToProtoSerde {
	public static com.hederahashgraph.api.proto.java.ThrottleDefinitions loadProtoDefs(String jsonResource) {
		return loadPojoDefs(jsonResource).toProto();
	}

	public static ThrottleDefinitions loadPojoDefs(String jsonResource) {
		try (InputStream in = ThrottlesJsonToProtoSerde.class.getClassLoader().getResourceAsStream(jsonResource)) {
			var om = new ObjectMapper();
			return om.readValue(in, ThrottleDefinitions.class);
		} catch (Exception e) {
			throw new IllegalStateException(String.format("Cannot load throttle definitions '%s'!", jsonResource), e);
		}
	}
}
