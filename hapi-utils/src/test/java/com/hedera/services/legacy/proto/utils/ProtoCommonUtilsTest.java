package com.hedera.services.legacy.proto.utils;

/*-
 * ‌
 * Hedera Services API Utilities
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

class ProtoCommonUtilsTest {
	@Test
	void unsafeWrappingWorks() {
		final var wrappedData = ProtoCommonUtils.wrapUnsafely(data);
		assertArrayEquals(data, wrappedData.toByteArray());
	}

	@Test
	void unsafeUnwrappingWorks() {
		final var wrapper = ProtoCommonUtils.wrapUnsafely(data);
		final var unwrapped = ProtoCommonUtils.unwrapUnsafelyIfPossible(wrapper);
		assertSame(data, unwrapped);
	}

	@Test
	void supportsOnlyReasonable() {
		final var wrapper = Mockito.mock(ByteString.class);
		given(wrapper.size()).willReturn(ProtoCommonUtils.UnsafeByteOutput.SIZE - 1);
		assertFalse(ProtoCommonUtils.UnsafeByteOutput.supports(wrapper));
		given(wrapper.size()).willReturn(42);
		assertFalse(ProtoCommonUtils.UnsafeByteOutput.supports(wrapper));
		assertTrue(ProtoCommonUtils.UnsafeByteOutput.supports(ByteString.copyFrom(data)));
	}

	@Test
	void unsafeOutputUnsupportedAsExpected() {
		final var subject = new ProtoCommonUtils.UnsafeByteOutput();

		assertThrows(UnsupportedOperationException.class, () -> subject.write((byte) 1));
		assertThrows(UnsupportedOperationException.class, () -> subject.write(ByteBuffer.allocate(1)));
		assertThrows(UnsupportedOperationException.class, () -> subject.writeLazy(ByteBuffer.allocate(1)));
	}

	@Test
	void unsafeOutputSupportedAsExpected() throws IOException {
		final var subject = new ProtoCommonUtils.UnsafeByteOutput();

		subject.write(data, 0, data.length);

		assertSame(data, subject.getBytes());
	}

	private static final byte[] data = "Between the idea and the reality".getBytes();
}
