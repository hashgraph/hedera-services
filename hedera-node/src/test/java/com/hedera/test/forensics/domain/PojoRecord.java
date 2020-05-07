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
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hedera.services.legacy.core.jproto.JAccountID;
import com.hedera.services.legacy.core.jproto.JTimestamp;
import com.hedera.services.legacy.core.jproto.JTransactionID;
import com.hedera.services.legacy.core.jproto.JTransactionReceipt;
import com.hedera.services.legacy.core.jproto.JTransactionRecord;
import com.hedera.services.legacy.core.jproto.JTransferList;
import org.apache.commons.codec.binary.Hex;

import static java.util.stream.Collectors.toList;

@JsonPropertyOrder({
		"receipt",
		"txnId",
		"timestamp",
		"hash",
		"memo",
		"fee",
		"expiry",
		"transfers",
		"callResult",
		"createResult"
})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PojoRecord {
	private long fee;
	private long expiry;
	private String hash;
	private String txnId;
	private String receipt;
	private String memo;
	private String timestamp;
	private String transfers;
	private PojoFunctionResult callResult;
	private PojoFunctionResult createResult;

	public static PojoRecord from(JTransactionRecord value) {
		var pojo = new PojoRecord();
		pojo.setTxnId(asString(value.getTransactionID()));
		pojo.setReceipt(asString(value.getTxReceipt()));
		pojo.setHash(Hex.encodeHexString(value.getTxHash()));
		pojo.setTimestamp(asString(value.getConsensusTimestamp()));
		pojo.setMemo(value.getMemo());
		pojo.setFee(value.getTransactionFee());
		pojo.setExpiry(value.getExpirationTime());
		pojo.setTransfers(MiscUtils.readableTransferList(xfersFrom(value.getjTransferList())));
		if (value.getContractCallResult() != null) {
			pojo.setCallResult(PojoFunctionResult.from(value.getContractCallResult()));
		}
		if (value.getContractCreateResult() != null) {
			pojo.setCreateResult(PojoFunctionResult.from(value.getContractCreateResult()));
		}
		return pojo;
	}

	public PojoFunctionResult getCallResult() {
		return callResult;
	}

	public void setCallResult(PojoFunctionResult callResult) {
		this.callResult = callResult;
	}

	public PojoFunctionResult getCreateResult() {
		return createResult;
	}

	public void setCreateResult(PojoFunctionResult createResult) {
		this.createResult = createResult;
	}

	public String getTransfers() {
		return transfers;
	}

	public void setTransfers(String transfers) {
		this.transfers = transfers;
	}

	public long getExpiry() {
		return expiry;
	}

	public void setExpiry(long expiry) {
		this.expiry = expiry;
	}

	public long getFee() {
		return fee;
	}

	public void setFee(long fee) {
		this.fee = fee;
	}

	public String getMemo() {
		return memo;
	}

	public void setMemo(String memo) {
		this.memo = memo;
	}

	public String getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(String timestamp) {
		this.timestamp = timestamp;
	}

	public String getHash() {
		return hash;
	}

	public void setHash(String hash) {
		this.hash = hash;
	}

	public String getTxnId() {
		return txnId;
	}

	public void setTxnId(String txnId) {
		this.txnId = txnId;
	}

	public String getReceipt() {
		return receipt;
	}

	public void setReceipt(String receipt) {
		this.receipt = receipt;
	}

	public static String asString(JAccountID id) {
		if (id == null) {
			return null;
		}
		return String.format("%d.%d.%d", id.getShardNum(), id.getRealmNum(), id.getAccountNum());
	}

	public static String asString(JTimestamp stamp) {
		return String.format("%d.%d", stamp.getSeconds(), stamp.getNano());
	}

	public static String asString(JTransactionID txnId) {
		var ts = String.format("%d.%d", txnId.getStartTime().getSeconds(), txnId.getStartTime().getNano());
		return String.format("From %s @ %s", asString(txnId.getPayerAccount()), ts);
	}

	public static TransferList xfersFrom(JTransferList list) {
		return TransferList.newBuilder()
				.addAllAccountAmounts(
						list.getjAccountAmountsList().stream().map(JTransferList::convert).collect(toList()))
				.build();
	}

	public static String asString(JTransactionReceipt receipt) {
		var createdId = "";
		if (receipt.getAccountID() != null) {
			createdId = "+Account" + asString(receipt.getAccountID());
		} else if (receipt.getContractID() != null) {
			createdId = "+Contract" + asString(receipt.getContractID());
		} else if (receipt.getFileID() != null) {
			createdId = "+File" + asString(receipt.getFileID());
		} else if (receipt.getTopicID() != null) {
			createdId = "+Topic" + asString(receipt.getTopicID());
		}

		var rates = "N/A";
		if (receipt.getExchangeRate() != null) {
			rates = String.format(
					"%d <-> %d til %d | %d <-> %d til %d",
					receipt.getExchangeRate().getCurrentRate().getHbarEquiv(),
					receipt.getExchangeRate().getCurrentRate().getCentEquiv(),
					receipt.getExchangeRate().getCurrentRate().getExpirationTime(),
					receipt.getExchangeRate().getNextRate().getHbarEquiv(),
					receipt.getExchangeRate().getNextRate().getCentEquiv(),
					receipt.getExchangeRate().getNextRate().getExpirationTime()
			);
		}

		return String.format("%s (%s, %s)", receipt.getStatus(), createdId, rates);
	}
}
