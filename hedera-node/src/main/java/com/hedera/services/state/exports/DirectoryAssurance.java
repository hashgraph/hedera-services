package com.hedera.services.state.exports;

import java.io.IOException;

@FunctionalInterface
public interface DirectoryAssurance {
	void ensureExistenceOf(String loc) throws IOException;
}
