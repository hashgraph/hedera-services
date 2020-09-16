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
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.legacy.core.jproto.TxnId;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import org.apache.commons.codec.binary.Hex;

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

	public static PojoRecord from(ExpirableTxnRecord value) {
		var pojo = new PojoRecord();
		pojo.setTxnId(asString(value.getTxnId()));
		pojo.setReceipt(asString(value.getReceipt()));
		pojo.setHash(Hex.encodeHexString(value.getTxnHash()));
		pojo.setTimestamp(asString(value.getConsensusTimestamp()));
		pojo.setMemo(value.getMemo());
		pojo.setFee(value.getFee());
		pojo.setExpiry(value.getExpiry());
		pojo.setTransfers(MiscUtils.readableTransferList(value.getHbarAdjustments().toGrpc()));
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

	public static String asString(EntityId id) {
		if (id == null) {
			return null;
		}
		return String.format("%d.%d.%d", id.shard(), id.realm(), id.num());
	}

	public static String asString(RichInstant stamp) {
		return String.format("%d.%d", stamp.getSeconds(), stamp.getNanos());
	}

	public static String asString(TxnId txnId) {
		var ts = String.format("%d.%d", txnId.getValidStart().getSeconds(), txnId.getValidStart().getNanos());
		return String.format("From %s @ %s", asString(txnId.getPayerAccount()), ts);
	}

	public static String asString(TxnReceipt receipt) {
		var createdId = "";
		if (receipt.getAccountId() != null) {
			createdId = "+Account" + asString(receipt.getAccountId());
		} else if (receipt.getContractId() != null) {
			createdId = "+Contract" + asString(receipt.getContractId());
		} else if (receipt.getFileId() != null) {
			createdId = "+File" + asString(receipt.getFileId());
		} else if (receipt.getTopicId() != null) {
			createdId = "+Topic" + asString(receipt.getTopicId());
		}

		var rates = "N/A";
		if (receipt.getExchangeRates() != null) {
			rates = String.format(
					"%d <-> %d til %d | %d <-> %d til %d",
					receipt.getExchangeRates().getCurrHbarEquiv(),
					receipt.getExchangeRates().getCurrCentEquiv(),
					receipt.getExchangeRates().getCurrExpiry(),
					receipt.getExchangeRates().getNextHbarEquiv(),
					receipt.getExchangeRates().getNextCentEquiv(),
					receipt.getExchangeRates().getNextExpiry());
		}

		return String.format("%s (%s, %s)", receipt.getStatus(), createdId, rates);
	}
}
