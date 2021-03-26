package com.hedera.services.sysfiles.serdes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hederahashgraph.api.proto.java.ThrottleDefinitions;

import java.io.InputStream;

public class ThrottlesJsonToProtoSerde {
	public static ThrottleDefinitions loadProtoDefs(String jsonResource) {
		return loadPojoDefs(jsonResource).toProto();
	}

	public static com.hedera.services.sysfiles.domain.throttling.ThrottleDefinitions loadPojoDefs(String jsonResource) {
		try (InputStream in = ThrottlesJsonToProtoSerde.class.getClassLoader().getResourceAsStream(jsonResource)) {
			var om = new ObjectMapper();
			return om.readValue(in, com.hedera.services.sysfiles.domain.throttling.ThrottleDefinitions.class);
		} catch (Exception e) {
			throw new IllegalStateException(String.format("Cannot load throttle definitions '%s'!", jsonResource), e);
		}
	}
}
