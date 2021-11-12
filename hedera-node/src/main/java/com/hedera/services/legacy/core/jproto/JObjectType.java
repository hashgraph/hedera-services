package com.hedera.services.legacy.core.jproto;

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

import java.util.HashMap;

/**
 * Mapping of Class name and Object Id
 */
public enum JObjectType {
	JKEY,
	JKEY_LIST,
	JTHRESHOLD_KEY,
	JED25519_KEY,
	JECDSA_384KEY,
	JRSA_3072KEY,
	JCONTRACTID_KEY,
	JECDSA_SECP256K1_KEY,
	JFILE_INFO,
	JMEMO_ADMIN_KEY;

	private static final HashMap<JObjectType, Long> LOOKUP_TABLE = new HashMap<>();
	private static final HashMap<Long, JObjectType> REV_LOOKUP_TABLE = new HashMap<>();

	static {
		addLookup(JKEY, 15503731);
		addLookup(JKEY_LIST, 15512048);
		addLookup(JTHRESHOLD_KEY, 15520365);
		addLookup(JED25519_KEY, 15528682);
		addLookup(JECDSA_384KEY, 15536999);
		addLookup(JRSA_3072KEY, 15620169);
		addLookup(JCONTRACTID_KEY, 15545316);
		addLookup(JFILE_INFO, 15636803);
		addLookup(JMEMO_ADMIN_KEY, 15661754);
        addLookup(JECDSA_SECP256K1_KEY, 15661654);
	}

	private static void addLookup(final JObjectType type, final long value) {
		LOOKUP_TABLE.put(type, value);
		REV_LOOKUP_TABLE.put(value, type);
	}

	public static JObjectType valueOf(final long value) {
		return REV_LOOKUP_TABLE.get(value);
	}

	public long longValue() {
		return LOOKUP_TABLE.get(this);
	}
}
