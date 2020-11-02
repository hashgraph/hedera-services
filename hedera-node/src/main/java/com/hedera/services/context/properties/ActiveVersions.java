package com.hedera.services.context.properties;

import com.hederahashgraph.api.proto.java.SemanticVersion;

public class ActiveVersions {
	private final SemanticVersion proto;
	private final SemanticVersion services;

	public ActiveVersions(SemanticVersion proto, SemanticVersion services) {
		this.proto = proto;
		this.services = services;
	}

	public SemanticVersion protoSemVer() {
		return proto;
	}

	public SemanticVersion hederaSemVer() {
		return services;
	}
}
