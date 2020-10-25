package com.hedera.test.forensics.domain;

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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.hedera.test.utils.IdUtils.asAccountString;
import static com.hedera.test.utils.IdUtils.fromKey;
import static com.hedera.test.forensics.domain.PojoRecord.asString;
import static java.util.stream.Collectors.toList;

@JsonPropertyOrder({
		"id",
		"numPayerRecords",
		"hasMutablePayerRecords",
		"payerRecords",
		"smartContract",
		"deleted",
		"balance",
		"keys",
		"numRecords",
		"hasMutableRecords",
		"records",
		"expiry",
		"autoRenewPeriod",
		"sendThreshold",
		"receiveThreshold",
		"receiverSigRequired",
		"memo",
		"proxyId"
})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PojoAccount {
	private int numRecords;
	private int numPayerRecords;
	private boolean hasMutablePayerRecords;
	private boolean hasMutableRecords;
	private long balance;
	private long expiry;
	private long autoRenewPeriod;
	private long sendThreshold;
	private long receiveThreshold;
	private List<PojoRecord> records = Collections.emptyList();
	private List<PojoRecord> payerRecords = Collections.emptyList();
	private String id;
	private String proxyId = "0.0.0";
	private String memo;
	private String keys;
	private boolean smartContract;
	private boolean deleted;
	private boolean receiverSigRequired;

	public static PojoAccount fromEntry(Map.Entry<MerkleEntityId, MerkleAccount> e) {
		return from(e.getKey(), e.getValue());
	}

	public static PojoAccount from(MerkleEntityId mk, MerkleAccount value) {
		var pojo = new PojoAccount();
		if (mk.getNum() == 6237) {
			System.out.println(value);
		}
		pojo.setId(asAccountString(fromKey(mk)));
		pojo.setBalance(value.getBalance());
		pojo.setSmartContract(value.isSmartContract());
		pojo.setKeys(value.getKey().toString());
		pojo.setNumPayerRecords(value.payerRecords().size());
		pojo.setHasMutablePayerRecords(!value.payerRecords().isImmutable());
		if (pojo.getNumPayerRecords() > 0) {
			pojo.setPayerRecords(value.payerRecords().stream().map(PojoRecord::from).collect(toList()));
		}
		pojo.setExpiry(value.getExpiry());
		pojo.setAutoRenewPeriod(value.getAutoRenewSecs());
		pojo.setMemo(value.getMemo());
		pojo.setReceiverSigRequired(value.isReceiverSigRequired());
		pojo.setDeleted(value.isDeleted());
		if (value.getProxy() != null) {
			pojo.setProxyId(asString(value.getProxy()));
		}
		return pojo;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
	}

	public List<PojoRecord> getRecords() {
		return records;
	}

	public void setRecords(List<PojoRecord> records) {
		this.records = records;
	}

	public String getProxyId() {
		return proxyId;
	}

	public void setProxyId(String proxyId) {
		this.proxyId = proxyId;
	}

	public boolean isReceiverSigRequired() {
		return receiverSigRequired;
	}

	public void setReceiverSigRequired(boolean receiverSigRequired) {
		this.receiverSigRequired = receiverSigRequired;
	}

	public String getMemo() {
		return memo;
	}

	public void setMemo(String memo) {
		this.memo = memo;
	}

	public long getReceiveThreshold() {
		return receiveThreshold;
	}

	public void setReceiveThreshold(long receiveThreshold) {
		this.receiveThreshold = receiveThreshold;
	}

	public long getSendThreshold() {
		return sendThreshold;
	}

	public void setSendThreshold(long sendThreshold) {
		this.sendThreshold = sendThreshold;
	}

	public long getAutoRenewPeriod() {
		return autoRenewPeriod;
	}

	public void setAutoRenewPeriod(long autoRenewPeriod) {
		this.autoRenewPeriod = autoRenewPeriod;
	}

	public long getExpiry() {
		return expiry;
	}

	public void setExpiry(long expiry) {
		this.expiry = expiry;
	}

	public int getNumRecords() {
		return numRecords;
	}

	public void setNumRecords(int numRecords) {
		this.numRecords = numRecords;
	}

	public long getBalance() {
		return balance;
	}

	public void setBalance(long balance) {
		this.balance = balance;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getKeys() {
		return keys;
	}

	public void setKeys(String keys) {
		this.keys = keys;
	}

	public boolean isSmartContract() {
		return smartContract;
	}

	public void setSmartContract(boolean smartContract) {
		this.smartContract = smartContract;
	}

	public int getNumPayerRecords() {
		return numPayerRecords;
	}

	public void setNumPayerRecords(int numPayerRecords) {
		this.numPayerRecords = numPayerRecords;
	}

	public List<PojoRecord> getPayerRecords() {
		return payerRecords;
	}

	public void setPayerRecords(List<PojoRecord> payerRecords) {
		this.payerRecords = payerRecords;
	}

	public boolean isHasMutablePayerRecords() {
		return hasMutablePayerRecords;
	}

	public void setHasMutablePayerRecords(boolean hasMutablePayerRecords) {
		this.hasMutablePayerRecords = hasMutablePayerRecords;
	}

	public boolean isHasMutableRecords() {
		return hasMutableRecords;
	}

	public void setHasMutableRecords(boolean hasMutableRecords) {
		this.hasMutableRecords = hasMutableRecords;
	}
}
