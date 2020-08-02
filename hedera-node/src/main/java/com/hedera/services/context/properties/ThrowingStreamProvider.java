package com.hedera.services.context.properties;

import java.io.IOException;
import java.io.InputStream;

@FunctionalInterface
interface ThrowingStreamProvider {
	InputStream newInputStream(String loc) throws IOException;
}
