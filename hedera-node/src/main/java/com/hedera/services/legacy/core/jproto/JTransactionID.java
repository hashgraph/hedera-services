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

import com.hedera.services.legacy.logic.ApplicationConstants;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionID.Builder;
import com.hederahashgraph.builder.RequestBuilder;
import com.swirlds.common.FastCopyable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Objects;

import com.swirlds.common.io.FCDataInputStream;
import com.swirlds.common.io.FCDataOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This is custom class equivalent to Transaction ID proto
 *
 * @author Akshay
 * @Date : 1/9/2019
 */
public class JTransactionID implements FastCopyable, Serializable {

	private static final Logger log = LogManager.getLogger(JTransactionID.class);
	private static final long LEGACY_VERSION_1 = 1;
	private static final long CURRENT_VERSION = 2;
	private JAccountID payerAccount;
	private JTimestamp startTime;

	public JTransactionID() {
	}

	public JTransactionID(final JAccountID payerAccount, final JTimestamp startTime) {
		this.payerAccount = payerAccount;
		this.startTime = startTime;
	}

	public JTransactionID(final JTransactionID other) {
		this.payerAccount = other.payerAccount;
		this.startTime = other.startTime;
	}

	public JAccountID getPayerAccount() {
		return payerAccount;
	}

	public void setPayerAccount(final JAccountID payerAccount) {
		this.payerAccount = payerAccount;
	}

	public JTimestamp getStartTime() {
		return startTime;
	}

	public void setStartTime(final JTimestamp startTime) {
		this.startTime = startTime;
	}

	/**
	 * Custom serialize method. If some field is null then it will set 0 byte for that field otherwise
	 * it add length of the byte first and then actual byte of the field.
	 *
	 * @return serialized byte array of this class
	 */
	private void serialize(final FCDataOutputStream outStream) throws IOException {
		outStream.writeLong(CURRENT_VERSION);
		outStream.writeLong(JObjectType.JTransactionID.longValue());

		if (this.payerAccount != null) {
			outStream.writeChar(ApplicationConstants.P);
			this.payerAccount.copyTo(outStream);
			this.payerAccount.copyToExtra(outStream);
		} else {
			outStream.writeChar(ApplicationConstants.N);
		}

		if (this.startTime != null) {
			outStream.writeBoolean(true);
			this.startTime.copyTo(outStream);
			this.startTime.copyToExtra(outStream);

		} else {
			outStream.writeBoolean(false);
		}

	}

	@SuppressWarnings("unchecked")
	public static <T extends FastCopyable> T deserialize(final DataInputStream inStream) throws IOException {
		final JTransactionID transactionID = new JTransactionID();

		deserialize((FCDataInputStream)inStream, transactionID);
		return (T) transactionID;
	}

	/**
	 * Custom deserialization  of this class. It read first length of the field if it is 0 then sets
	 * field to null otherwise it read bytes from DataInputStream of specified length and deserialize
	 * those byte for the field.
	 */

	private static void deserialize(
			final FCDataInputStream inStream,
			final JTransactionID transactionID
	) throws IOException {

		long version = inStream.readLong();
		if (version < LEGACY_VERSION_1 || version > CURRENT_VERSION) {
			throw new IllegalStateException("Illegal version was read from the stream");
		}

		long objectType = inStream.readLong();
		JObjectType type = JObjectType.valueOf(objectType);
		if (!JObjectType.JTransactionID.equals(type)) {
			throw new IllegalStateException("Illegal JObjectType was read from the stream");
		}

		if (inStream.readChar() == ApplicationConstants.P) {
			transactionID.payerAccount = JAccountID.deserialize(inStream);
		}

		final boolean startTimePresent = inStream.readBoolean();
		if (startTimePresent) {
			transactionID.startTime = JTimestamp.deserialize(inStream);
		}


	}

	@Override
	public FastCopyable copy() {
		return new JTransactionID(this);
	}

	@Override
	public void copyTo(final FCDataOutputStream outStream) throws IOException {
		serialize(outStream);
	}

	@Override
	public void copyFrom(final FCDataInputStream inStream) throws IOException {

	}

	@Override
	public void copyToExtra(final FCDataOutputStream outStream) throws IOException {

	}

	@Override
	public void copyFromExtra(final FCDataInputStream inStream) throws IOException {

	}

	@Override
	public void diffCopyTo(final FCDataOutputStream outStream, final FCDataInputStream inStream) throws IOException {
		serialize(outStream);
	}

	@Override
	public void diffCopyFrom(final FCDataOutputStream outStream, final FCDataInputStream inStream) throws IOException {
		deserialize(inStream, this);
	}

	@Override
	public void delete() {

	}

	public static JTransactionID convert(final TransactionID transactionID) {
		JTimestamp jTimestamp = transactionID.hasTransactionValidStart() ?
				JTimestamp.convert(transactionID.getTransactionValidStart()) : null;
		JAccountID payerAccount = transactionID.hasAccountID() ?
				JAccountID.convert(transactionID.getAccountID()) : null;
		return new JTransactionID(payerAccount, jTimestamp);
	}

	public static TransactionID convert(final JTransactionID transactionID) {
	    Builder builder = TransactionID.newBuilder();
        if(transactionID.getPayerAccount() != null) {
          AccountID payerAccountID = RequestBuilder.getAccountIdBuild(
                transactionID.getPayerAccount().getAccountNum(),
                transactionID.getPayerAccount().getRealmNum(),
                transactionID.getPayerAccount().getShardNum());
          builder.setAccountID(payerAccountID);
        }
        
        if(transactionID.getStartTime() != null) {
          Timestamp startTime = JTimestamp.convert(transactionID.getStartTime());
          builder.setTransactionValidStart(startTime);
        }
        
		return builder.build();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		JTransactionID that = (JTransactionID) o;
		return Objects.equals(payerAccount, that.payerAccount) &&
				Objects.equals(startTime, that.startTime);
	}

	@Override
	public int hashCode() {
		return Objects.hash(payerAccount, startTime);
	}

	@Override
	public String toString() {
		return "JTransactionID{" +
				"payerAccount=" + payerAccount +
				", startTime=" + startTime +
				'}';
	}
}
