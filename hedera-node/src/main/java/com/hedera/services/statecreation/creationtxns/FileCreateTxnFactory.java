package com.hedera.services.statecreation.creationtxns;

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

import com.google.protobuf.ByteString;
import com.hedera.services.statecreation.creationtxns.utils.KeyFactory;
import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;

import java.security.SecureRandom;
import java.time.Instant;

import static com.hedera.services.statecreation.creationtxns.utils.TempUtils.randomUtf8ByteString;

public class FileCreateTxnFactory extends CreateTxnFactory<FileCreateTxnFactory> {

	SecureRandom random = new SecureRandom();
	private static KeyList waclKeys;
	static {
		waclKeys = KeyList.newBuilder().addKeys(0, KeyFactory.getKey()).build();
	}

	private boolean forContractFile = false;
	private ByteString contents = null;

	// balanceLookup contract bin
	private static byte[] byteCode = ("60806040525b5b61000b565b60ef806100196000396000f3fe608060405260043610601f57" +
			"60003560e01c8063f455e22014602357601f565b5b5b005b348015602f5760006000fd5b50606460048036036020811015" +
			"60455760006000fd5b81019080803567ffffffffffffffff169060200190929190505050607a565b604051808281526020" +
			"0191505060405180910390f35b60008167ffffffffffffffff166effffffffffffffffffffffffffffff1673ffffffff"  +
			"ffffffffffffffffffffffffffffffff1631905060b5565b91905056fea265627a7a72315820" +
			"91b68339fb303c3596e17c44e477a61c4e6d3eb13b817c47e4f181f0319407f864736f6c634300050b0032").getBytes();

	private FileCreateTxnFactory() {}
	public static FileCreateTxnFactory newSignedFileCreate() {
		return new FileCreateTxnFactory();
	}

	@Override
	protected FileCreateTxnFactory self() {
		return this;
	}

	@Override
	protected long feeFor(Transaction signedTxn, int numPayerKeys) {
		return 0;
	}

	@Override
	protected void customizeTxn(TransactionBody.Builder txn) {
		Timestamp.Builder expiry = Timestamp.newBuilder()
				.setSeconds(Instant.now().getEpochSecond() + 1000000)
				.setNanos(0);

		if(forContractFile) {
			contents = ByteString.copyFrom(byteCode);
		}
		else if(contents == null) {
			contents = randomUtf8ByteString(64 + random.nextInt(256));
		}

		FileCreateTransactionBody.Builder op = FileCreateTransactionBody.newBuilder()
				.setContents(contents)
				.setExpirationTime(expiry)
				.setKeys(waclKeys);
		txn.setFileCreate(op);
	}

	public FileCreateTxnFactory contents(final ByteString contents) {
		this.contents = contents;
		return this;
	}

	public FileCreateTxnFactory forContractFile(final boolean forContractFile) {
		this.forContractFile = forContractFile;
		return this;
	}
}
