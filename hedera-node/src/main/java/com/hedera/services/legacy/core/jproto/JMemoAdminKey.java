package com.hedera.services.legacy.core.jproto;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.Serializable;

import com.hedera.services.legacy.exception.DeserializationException;
import com.hedera.services.legacy.exception.SerializationException;

/**
 * Custom class for storing both smart contract memo and admin key.
 *
 * @author Hua Li
 * 		Created on 2019-06-25
 */
public class JMemoAdminKey implements Serializable {
	private static final long serialVersionUID = 1L;
	private JKey adminKey = null; // file AdminKey as a JKey of type JKeyList
	private String memo = "";

	public String getMemo() {
		return memo;
	}

	public void setMemo(String memo) {
		this.memo = memo;
	}

	public JMemoAdminKey(String memo, JKey adminKey) {
		this.adminKey = adminKey;
		this.memo = memo;
	}

	/**
	 * Serialize this JMemoAdminKey object.
	 */
	public byte[] serialize() throws SerializationException {
		return JMemoAdminKeySerializer.serialize(this);
	}

	/**
	 * Deserializes bytes into a JMemoAdminKey object.
	 *
	 * @param bytes
	 * 		JMemoAdminKey object serialized bytes
	 * @throws DeserializationException
	 */
	public static JMemoAdminKey deserialize(byte[] bytes) throws DeserializationException {
		DataInputStream stream = new DataInputStream(new ByteArrayInputStream(bytes));
		return JMemoAdminKeySerializer.deserialize(stream);
	}

	public JKey getAdminKey() {
		return adminKey;
	}


	@Override
	public String toString() {
		return "<JMemoAdminKey: memo=" + memo + ", adminKey=" + adminKey + ">";
	}
}
