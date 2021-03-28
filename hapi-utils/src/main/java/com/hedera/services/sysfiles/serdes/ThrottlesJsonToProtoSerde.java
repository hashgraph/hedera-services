package com.hedera.services.sysfiles.serdes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hederahashgraph.api.proto.java.ThrottleDefinitions;

import java.io.IOException;
import java.io.InputStream;

public class ThrottlesJsonToProtoSerde {
	public static ThrottleDefinitions loadProtoDefs(InputStream in) throws IOException {
		return loadPojoDefs(in).toProto();
	}

	public static com.hedera.services.sysfiles.domain.throttling.ThrottleDefinitions loadPojoDefs(InputStream in) throws IOException {
		var om = new ObjectMapper();
		return om.readValue(in, com.hedera.services.sysfiles.domain.throttling.ThrottleDefinitions.class);
	}
}
