package com.hedera.services.bdd.spec;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.RealmID;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import com.hederahashgraph.api.proto.java.ShardID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.spec.props.JutilPropertySource;
import com.hedera.services.bdd.spec.props.MapPropertySource;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public interface HapiPropertySource {
	String get(String property);
	boolean has(String property);

	static HapiPropertySource inPriorityOrder(HapiPropertySource... sources) {
		if (sources.length == 1) {
			return sources[0];
		} else {
			HapiPropertySource overrides = sources[0];
			HapiPropertySource defaults = inPriorityOrder(Arrays.copyOfRange(sources, 1, sources.length));

			return new HapiPropertySource() {
				@Override
				public String get(String property) {
					return overrides.has(property) ? overrides.get(property) : defaults.get(property);
				}

				@Override
				public boolean has(String property) {
					return overrides.has(property) || defaults.has(property);
				}
			};
		}
	}

	default HapiApiSpec.CostSnapshotMode getCostSnapshotMode(String property) {
		return HapiApiSpec.CostSnapshotMode.valueOf(get(property));
	}
	default HapiApiSpec.UTF8Mode getUTF8Mode(String property){
		return HapiApiSpec.UTF8Mode.valueOf(get(property));
	}
	default FileID getFile(String property) {
		try {
			return asFile(get(property));
		} catch (Exception ignore) {}
		return FileID.getDefaultInstance();
	}
	default AccountID getAccount(String property) {
		try {
			return asAccount(get(property));
		} catch (Exception ignore) {}
		return AccountID.getDefaultInstance();
	}
	default ContractID getContract(String property) {
		try {
			return asContract(get(property));
		} catch (Exception ignore) {}
		return ContractID.getDefaultInstance();
	}
	default RealmID getRealm(String property) {
		return RealmID.newBuilder().setRealmNum(Long.parseLong(get(property))).build();
	}
	default ShardID getShard(String property) {
		return ShardID.newBuilder().setShardNum(Long.parseLong(get(property))).build();
	}
	default TimeUnit getTimeUnit(String property) {
		return TimeUnit.valueOf(get(property));
	}
	default double getDouble(String property) {
		return Double.parseDouble(get(property));
	}
	default long getLong(String property) {
		return Long.parseLong(get(property));
	}
	default HapiSpecSetup.TlsConfig getTlsConfig(String property) {
		return HapiSpecSetup.TlsConfig.valueOf(get(property).toUpperCase());
	}
	default HapiSpecSetup.TxnConfig getTxnConfig(String property) {
		return HapiSpecSetup.TxnConfig.valueOf(get(property).toUpperCase());
	}
	default HapiSpecSetup.NodeSelection getNodeSelector(String property) {
		return HapiSpecSetup.NodeSelection.valueOf(get(property).toUpperCase());
	}
	default int getInteger(String property) {
		return Integer.parseInt(get(property));
	}
	default Duration getDurationFromSecs(String property) {
		return Duration.newBuilder().setSeconds(getInteger(property)).build();
	}
	default boolean getBoolean(String property) {
		return Boolean.parseBoolean(get(property));
	}
	default byte[] getBytes(String property) {
		return get(property).getBytes();
	}
	default KeyFactory.KeyType getKeyType(String property) {
		return KeyFactory.KeyType.valueOf(get(property));
	}
	default HapiApiSpec.SpecStatus getSpecStatus(String property) {
		return HapiApiSpec.SpecStatus.valueOf(get(property));
	}

	static HapiPropertySource[] asSources(Object... sources) {
		return Stream.of(sources)
				.map(s -> (s instanceof HapiPropertySource) ? s
						: ((s instanceof Map) ? new MapPropertySource((Map)s)
						: new JutilPropertySource((String)s)))
				.toArray(n -> new HapiPropertySource[n]);
	}

	static TokenID asToken(String v) {
		long[] nativeParts = asDotDelimitedLongArray(v);
		return TokenID.newBuilder()
				.setShardNum(nativeParts[0])
				.setRealmNum(nativeParts[1])
				.setTokenNum(nativeParts[2])
				.build();
	}
	static String asTokenString(TokenID account) {
		return String.format("%d.%d.%d", account.getShardNum(), account.getRealmNum(), account.getTokenNum());
	}

	static AccountID asAccount(String v) {
		 long[] nativeParts = asDotDelimitedLongArray(v);
		 return AccountID.newBuilder()
				 .setShardNum(nativeParts[0])
				 .setRealmNum(nativeParts[1])
				 .setAccountNum(nativeParts[2])
				 .build();
	}
	static String asAccountString(AccountID account) {
		return String.format("%d.%d.%d", account.getShardNum(), account.getRealmNum(), account.getAccountNum());
	}

	static TopicID asTopic(String v) {
		long[] nativeParts = asDotDelimitedLongArray(v);
		return TopicID.newBuilder()
				.setShardNum(nativeParts[0])
				.setRealmNum(nativeParts[1])
				.setTopicNum(nativeParts[2])
				.build();
	}
	static String asTopicString(TopicID topic) {
		return String.format("%d.%d.%d", topic.getShardNum(), topic.getRealmNum(), topic.getTopicNum());
	}

	static ContractID asContract(String v) {
		long[] nativeParts = asDotDelimitedLongArray(v);
		return ContractID.newBuilder()
				.setShardNum(nativeParts[0])
				.setRealmNum(nativeParts[1])
				.setContractNum(nativeParts[2])
				.build();
	}

	static SemanticVersion asSemVer(String v) {
		long[] nativeParts = asDotDelimitedLongArray(v);
		return SemanticVersion.newBuilder()
				.setMajor((int)nativeParts[0])
				.setMinor((int)nativeParts[1])
				.setPatch((int)nativeParts[2])
				.build();
	}

	static FileID asFile(String v) {
		long[] nativeParts = asDotDelimitedLongArray(v);
		return FileID.newBuilder()
				.setShardNum(nativeParts[0])
				.setRealmNum(nativeParts[1])
				.setFileNum(nativeParts[2])
				.build();
	}

	static String asFileString(FileID file) {
		return String.format("%d.%d.%d", file.getShardNum(), file.getRealmNum(), file.getFileNum());
	}

	static long[] asDotDelimitedLongArray(String s) {
		String[] parts = s.split("[.]");
		return Stream.of(parts).mapToLong(Long::valueOf).toArray();
	}
}
