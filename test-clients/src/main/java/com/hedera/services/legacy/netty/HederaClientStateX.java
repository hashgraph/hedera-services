package com.hedera.services.legacy.netty;

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


import com.hedera.services.legacy.core.HexUtils;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hederahashgraph.api.proto.java.AccountID;
import net.i2p.crypto.eddsa.EdDSAPublicKey;

import java.io.Serializable;
import java.security.PrivateKey;
import java.sql.Timestamp;

import java.security.KeyPair;

/**
 * this is the client state for loading the accounts
 * to be used during the startx tests
 *
 * @author oc
 */
public class HederaClientStateX implements Serializable {
	private static final long serialVersionUID = 696979798989L;
	AccountID accountID;
	long accountBalance;
	KeyPairObj keyPairObj;

	Timestamp createdTime;
	byte[] originalTx;

	public AccountID getAccountID() {
		return accountID;
	}

	public void setAccountID(AccountID accountID) {
		this.accountID = accountID;
	}

	public KeyPairObj getKeyPairObj() {
		return keyPairObj;
	}

	public void setKeyPairObj(KeyPairObj keyPairObj) {
		this.keyPairObj = keyPairObj;
	}

	public long getAccountBalance() {
		return accountBalance;
	}

	public void setAccountBalance(long accountBalance) {
		this.accountBalance = accountBalance;
	}

	public HederaClientStateX(AccountID accID, long accountBalance, KeyPair keyPair, Timestamp createdTs,
			byte[] originalTxBytes) {
		this.accountID = accID;
		this.accountBalance = accountBalance;
		this.createdTime = createdTs;
		this.originalTx = originalTxBytes;

		byte[] pubKey = ((EdDSAPublicKey) keyPair.getPublic()).getEncoded();
		String pubKeyStr = HexUtils.bytes2Hex(pubKey);
		PrivateKey priv = keyPair.getPrivate();
		String privkeyStr = HexUtils.bytes2Hex(priv.getEncoded());
		this.keyPairObj = new KeyPairObj(pubKeyStr, privkeyStr);
	}


	public Timestamp getCreatedTime() {
		return createdTime;
	}

	public void setCreatedTime(Timestamp createdTime) {
		this.createdTime = createdTime;
	}

	public byte[] getOriginalTx() {
		return originalTx;
	}

	public void setOriginalTx(byte[] originalTx) {
		this.originalTx = originalTx;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		try {
			sb.append(" Account Id: ").append(this.accountID);
			sb.append(" Account Balance: ").append(this.accountBalance);
			sb.append(" Created TimeS ").append(this.createdTime);
			if (this.originalTx != null) {
				sb.append(" Original Byte Size ").append(this.originalTx.length);
			}
		} catch (Exception sbx) {
			sb.append("Invalid Content Err");
		}

		return sb.toString();
	}
}
