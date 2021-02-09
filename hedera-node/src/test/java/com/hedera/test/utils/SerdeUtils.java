package com.hedera.test.utils;

/*-
 * ‌
 * Hedera Services Node
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

import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

public class SerdeUtils {
	public static byte[] serOutcome(ThrowingConsumer<DataOutputStream> serializer) throws Exception {
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			try (SerializableDataOutputStream out = new SerializableDataOutputStream(baos)) {
				serializer.accept(out);
			}
			return baos.toByteArray();
		}
	}

	public static <T> T deOutcome(ThrowingFunction<DataInputStream, T> deserializer, byte[] repr) throws Exception {
		try (ByteArrayInputStream bais = new ByteArrayInputStream(repr)) {
			try (SerializableDataInputStream in = new SerializableDataInputStream(bais)) {
				return deserializer.apply(in);
			}
		}
	}

	@FunctionalInterface
	public interface ThrowingConsumer<T> {
		void accept(T t) throws Exception;
	}

	@FunctionalInterface
	public interface ThrowingFunction<T, R> {
		R apply(T t) throws Exception;
	}
}
