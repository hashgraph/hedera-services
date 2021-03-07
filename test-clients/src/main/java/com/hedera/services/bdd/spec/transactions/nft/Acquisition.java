package com.hedera.services.bdd.spec.transactions.nft;

/*-
 * ‌
 * Hedera Services Test Clients
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
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.NftTransferList;

import java.util.Arrays;

public class Acquisition {
	public static final int NUM_NFT_SERIAL_NO_BYTES = 32;

	private final String nftType;
	private final String serialNo;
	private final String fromAccount;
	private final String toAccount;

	public Acquisition(String nftType, String serialNo, String fromAccount, String toAccount) {
		this.nftType = nftType;
		this.serialNo = serialNo;
		this.fromAccount = fromAccount;
		this.toAccount = toAccount;
	}

	public static ByteString asSerialNo(String shortUtf8) {
		int used = shortUtf8.length();
		byte[] bytes = new byte[NUM_NFT_SERIAL_NO_BYTES];
		System.arraycopy(shortUtf8.getBytes(), 0, bytes, 0, used);
		Arrays.fill(bytes, used, NUM_NFT_SERIAL_NO_BYTES, " ".getBytes()[0]);
		return ByteString.copyFrom(bytes);
	}

	public NftTransferList specializedFor(HapiApiSpec spec) {
		var registry = spec.registry();
		var scopedChanges = NftTransferList.newBuilder()
				.setNft(registry.getNftID(nftType))
				.addTransfer(NftTransfer.newBuilder()
						.setFromAccount(registry.getAccountID(fromAccount))
						.setToAccount(registry.getAccountID(toAccount))
						.setSerialNo(asSerialNo(serialNo)));
		return scopedChanges.build();
	}

	public static Change ofNft(String nftType) {
		return new Change(nftType);
	}

	public static class BeneficiaryReadyChange {
		private final String from;
		private final TypedChange typedChange;

		public BeneficiaryReadyChange(TypedChange typedChange, String from) {
			this.from = from;
			this.typedChange = typedChange;
		}

		public Acquisition to(String to) {
			return new Acquisition(
					typedChange.getChange().getNftType(),
					typedChange.getSerialNo(),
					from,
					to);
		}
	}

	public static class TypedChange {
		private final Change change;
		private final String serialNo;

		public TypedChange(Change change, String serialNo) {
			this.change = change;
			this.serialNo = serialNo;
		}

		public BeneficiaryReadyChange from(String from) {
			return new BeneficiaryReadyChange(this, from);
		}

		Change getChange() {
			return change;
		}

		String getSerialNo() {
			return serialNo;
		}
	}

	public static class Change {
		final String nftType;

		public Change(String nftType) {
			this.nftType = nftType;
		}

		public TypedChange serialNo(String serialNo) {
			return new TypedChange(this, serialNo);
		}

		String getNftType() {
			return nftType;
		}
	}

	public String getFromAccount() {
		return fromAccount;
	}

	public String getToAccount() {
		return toAccount;
	}
}
