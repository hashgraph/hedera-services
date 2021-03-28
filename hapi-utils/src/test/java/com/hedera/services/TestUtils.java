package com.hedera.services;

import com.hedera.services.sysfiles.serdes.ThrottlesJsonToProtoSerde;
import com.hederahashgraph.api.proto.java.ThrottleDefinitions;

import java.io.IOException;
import java.io.InputStream;

public class TestUtils {
	public static ThrottleDefinitions protoDefs(
			String testResource
	) throws IOException {
		try (InputStream in = ThrottlesJsonToProtoSerde.class.getClassLoader().getResourceAsStream(testResource)) {
			return ThrottlesJsonToProtoSerde.loadProtoDefs(in);
		}
	}

	public static com.hedera.services.sysfiles.domain.throttling.ThrottleDefinitions pojoDefs(
			String testResource
	) throws IOException {
		try (InputStream in = ThrottlesJsonToProtoSerde.class.getClassLoader().getResourceAsStream(testResource)) {
			return ThrottlesJsonToProtoSerde.loadPojoDefs(in);
		}
	}
}
