package com.hedera.services.bdd.suites.utils.sysfiles.serdes;

import com.hedera.services.sysfiles.serdes.ThrottlesJsonToProtoSerde;
import com.hederahashgraph.api.proto.java.ThrottleDefinitions;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

public class ThrottleDefsLoader {
	public static ThrottleDefinitions protoDefsFromResource(String testResource) {
		try (InputStream in = ThrottlesJsonToProtoSerde.class.getClassLoader().getResourceAsStream(testResource)) {
			return ThrottlesJsonToProtoSerde.loadProtoDefs(in);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static com.hedera.services.sysfiles.domain.throttling.ThrottleDefinitions pojoDefs(
			String testResource
	) {
		try (InputStream in = ThrottlesJsonToProtoSerde.class.getClassLoader().getResourceAsStream(testResource)) {
			return ThrottlesJsonToProtoSerde.loadPojoDefs(in);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
