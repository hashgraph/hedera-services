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

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.builder.RequestBuilder;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.FCDataInputStream;
import com.swirlds.common.io.FCDataOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.StringUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This is custom class equivalent to ContractFunctionResult proto
 *
 * @author Akshay
 * @Date : 1/9/2019
 */
public class JContractFunctionResult implements FastCopyable {

	private static final Logger log = LogManager.getLogger(JContractFunctionResult.class);
	private static final long LEGACY_VERSION_1 = 1;
	private static final long LEGACY_VERSION_2 = 2;
	private static final long CURRENT_VERSION = 3;

	private JAccountID contractID;
	private byte[] result;
	private String error;
	private byte[] bloom;
	private long gasUsed;
	private List<JContractLogInfo> jContractLogInfo;
	private List<JAccountID> jCreatedContractIDs;

	private OptionalLong versionToSerialize = OptionalLong.empty();

	public JContractFunctionResult() {
		result = new byte[0];
		bloom = new byte[0];
		this.jContractLogInfo = new ArrayList<>();
		this.jCreatedContractIDs = new ArrayList<>();
	}

	public JContractFunctionResult(final JAccountID contractID, final byte[] result, final String error,
			final byte[] bloom,
			final long gasUsed, final List<JContractLogInfo> jContractLogInfo,
			final List<JAccountID> jCreatedContractIDs) {
		this.contractID = contractID;
		this.result = result;
		this.error = error;
		this.bloom = bloom;
		this.gasUsed = gasUsed;
		this.jContractLogInfo = jContractLogInfo;
		this.jCreatedContractIDs = jCreatedContractIDs;
	}

	public JContractFunctionResult(final JContractFunctionResult other) {
		this.contractID = (other.contractID != null) ? (JAccountID) other.contractID.copy() : null;
		this.result = (other.result != null) ? Arrays.copyOf(other.result, other.result.length) : null;
		this.error = other.error;
		this.bloom = (other.bloom != null) ? Arrays.copyOf(other.bloom, other.bloom.length) : null;
		this.gasUsed = other.gasUsed;
		this.jContractLogInfo = (other.jContractLogInfo != null) ? new ArrayList<>(
				other.jContractLogInfo) : new ArrayList<>();
		this.jCreatedContractIDs = (other.jCreatedContractIDs != null) ? new ArrayList<>(other.jCreatedContractIDs)
				: new ArrayList<>();
	}

	public void setVersionToSerialize(long versionToSerialize) {
		this.versionToSerialize = OptionalLong.of(versionToSerialize);
	}

	public JAccountID getContractID() {
		return contractID;
	}

	public void setContractID(final JAccountID contractID) {
		this.contractID = contractID;
	}

	public byte[] getResult() {
		return result;
	}

	public void setResult(final byte[] result) {
		this.result = result;
	}

	public String getError() {
		return error;
	}

	public void setError(final String error) {
		this.error = error;
	}

	public byte[] getBloom() {
		return bloom;
	}

	public void setBloom(final byte[] bloom) {
		this.bloom = bloom;
	}

	public long getGasUsed() {
		return gasUsed;
	}

	public void setGasUsed(final long gasUsed) {
		this.gasUsed = gasUsed;
	}

	public List<JContractLogInfo> getjContractLogInfo() {
		return jContractLogInfo;
	}

	public List<JAccountID> getjCreatedContractIDs() {
		return jCreatedContractIDs;
	}

	public static JContractFunctionResult convert(final ContractFunctionResult contractCallResult) {
		List<JContractLogInfo> jContractLogInfoList = new ArrayList<>();
		if (!contractCallResult.getLogInfoList().isEmpty()) {
			jContractLogInfoList = contractCallResult.getLogInfoList()
					.stream()
					.map(JContractFunctionResult::getjContractLogInfo)
					.collect(Collectors.toList());
		}

		List<JAccountID> jCreatedContractIDsList = new ArrayList<>();
		if (!contractCallResult.getCreatedContractIDsList().isEmpty())
		{
			jCreatedContractIDsList = contractCallResult.getCreatedContractIDsList()
					.stream()
					.map(JAccountID::convert)
					.collect(Collectors.toList());
		}

		JAccountID contractID = contractCallResult.hasContractID() ?
				JAccountID.convert(contractCallResult.getContractID()) : null;
		byte[] result = new byte[0];
		if (!contractCallResult.getContractCallResult().isEmpty()) {
			result = contractCallResult.getContractCallResult().toByteArray();
		}
		byte[] bloom = new byte[0];
		if (!contractCallResult.getBloom().isEmpty()) {
			bloom = contractCallResult.getBloom().toByteArray();
		}
		String error = null;
		if (!contractCallResult.getContractCallResult().isEmpty()) {
			error = contractCallResult.getErrorMessage();
		}
		return new JContractFunctionResult(contractID, result, error, bloom,
				contractCallResult.getGasUsed(), jContractLogInfoList, jCreatedContractIDsList);
	}

	private static JContractLogInfo getjContractLogInfo(final ContractLoginfo logInfo) {
		List<byte[]> topicList = new ArrayList<>();
		if (CollectionUtils.isNotEmpty(logInfo.getTopicList())) {
			topicList = logInfo.getTopicList().stream().map(ByteString::toByteArray)
					.collect(Collectors.toList());
		}

		JAccountID contractID = logInfo.hasContractID() ?
				JAccountID.convert(logInfo.getContractID()) : null;
		byte[] bloom = new byte[0];
		if (!logInfo.getBloom().isEmpty()) {
			bloom = logInfo.getBloom().toByteArray();
		}

		byte[] data = new byte[0];
		if (!logInfo.getData().isEmpty()) {
			data = logInfo.getData().toByteArray();
		}
		return new JContractLogInfo(contractID, bloom, topicList, data);
	}

	public static ContractFunctionResult convert(final JContractFunctionResult jContractCallResult) {

		ContractFunctionResult.Builder builder = ContractFunctionResult.newBuilder();
		if (jContractCallResult.getContractID() != null) {
			builder.setContractID(JAccountID.convert(jContractCallResult.getContractID()));
		}
		if (jContractCallResult.getResult() != null) {
			builder.setContractCallResult(ByteString.copyFrom(jContractCallResult.getResult()));
		}

		if (jContractCallResult.getBloom() != null) {
			builder.setBloom(ByteString.copyFrom(jContractCallResult.getBloom()));
		}

		if (CollectionUtils.isNotEmpty(jContractCallResult.getjContractLogInfo())) {
			List<ContractLoginfo> contractLogInfos = jContractCallResult.getjContractLogInfo().stream()
					.map(JContractFunctionResult::convert).collect(Collectors.toList());
			builder.addAllLogInfo(contractLogInfos);
		}

		if (CollectionUtils.isNotEmpty(jContractCallResult.getjCreatedContractIDs())) {
			List<ContractID> createdContractIDs = jContractCallResult.getjCreatedContractIDs().stream()
					.map(JAccountID::convert).collect(Collectors.toList());
			builder.addAllCreatedContractIDs(createdContractIDs);
		}

		if (jContractCallResult.getError() != null) {
			builder.setErrorMessage(jContractCallResult.getError());
		}
		return builder.setGasUsed(jContractCallResult.getGasUsed()).build();
	}

	public static ContractLoginfo convert(final JContractLogInfo jInfo) {
		ContractID contractId = RequestBuilder.getContractIdBuild(
				jInfo.getContractID().getAccountNum(),
				jInfo.getContractID().getRealmNum(),
				jInfo.getContractID().getShardNum());
		ContractLoginfo.Builder builder = ContractLoginfo.newBuilder().setContractID(contractId);

		if (jInfo.getBloom() != null) {
			builder.setBloom(ByteString.copyFrom(jInfo.getBloom()));
		}

		if (jInfo.getData() != null) {
			builder.setData(ByteString.copyFrom(jInfo.getData()));
		}

		if (CollectionUtils.isNotEmpty(jInfo.getTopic())) {
			List<ByteString> topicList = jInfo.getTopic().stream().map(ByteString::copyFrom)
					.collect(Collectors.toList());
			builder.addAllTopic(topicList);
		}

		return builder.build();
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		JContractFunctionResult that = (JContractFunctionResult) o;
		return gasUsed == that.gasUsed &&
				Objects.equals(contractID, that.contractID) &&
				Arrays.equals(result, that.result) &&
				Objects.equals(error, that.error) &&
				Arrays.equals(bloom, that.bloom) &&
				Objects.equals(jContractLogInfo, that.jContractLogInfo) &&
				Objects.equals(jCreatedContractIDs, that.jCreatedContractIDs);
	}

	@Override
	public int hashCode() {
		int result1 = Objects.hash(contractID, error, gasUsed, jContractLogInfo, jCreatedContractIDs);
		result1 = 31 * result1 + Arrays.hashCode(result);
		result1 = 31 * result1 + Arrays.hashCode(bloom);
		return result1;
	}

	@Override
	public String toString() {
		return "JContractFunctionResult{" +
				"contractID=" + contractID +
				", result=" + Arrays.toString(result) +
				", error='" + error + '\'' +
				", bloom=" + Arrays.toString(bloom) +
				", gasUsed=" + gasUsed +
				", jContractLogInfo=" + jContractLogInfo +
				", jCreatedContractIDs=" + jCreatedContractIDs +
				'}';
	}

	/**
	 * Custom serialize method. If some field is null then it will set 0 byte for that field otherwise
	 * it add length of the byte first and then actual byte of the field.
	 *
	 * @return serialized byte array of this class
	 */

	private void serialize(final FCDataOutputStream outStream) throws IOException {
		outStream.writeLong(versionToSerialize.orElse(CURRENT_VERSION));
		outStream.writeLong(JObjectType.JContractFunctionResult.longValue());

		if (this.contractID != null) {
			outStream.writeBoolean(true);
			this.contractID.copyTo(outStream);
			this.contractID.copyToExtra(outStream);

		} else {
			outStream.writeBoolean(false);
		}

		if (this.result != null && this.result.length > 0) {
			outStream.writeInt(this.result.length);
			outStream.write(this.result);
		} else {
			outStream.writeInt(0);
		}

		if (this.error != null) {
			byte[] sBytes = StringUtils.getBytesUtf8(this.error);
			outStream.writeInt(sBytes.length);
			outStream.write(sBytes);
		} else {
			outStream.writeInt(0);
		}

		if (this.bloom != null && this.bloom.length > 0) {
			outStream.writeInt(this.bloom.length);
			outStream.write(this.bloom);
		} else {
			outStream.writeInt(0);
		}

		outStream.writeLong(gasUsed);

		if (CollectionUtils.isNotEmpty(jContractLogInfo)) {
			outStream.writeInt(jContractLogInfo.size());
			for (JContractLogInfo element : jContractLogInfo) {
				element.copyTo(outStream);
				element.copyToExtra(outStream);
			}
		} else {
			outStream.writeInt(0);
		}

		if (versionToSerialize.orElse(CURRENT_VERSION) > LEGACY_VERSION_2) {
			if (CollectionUtils.isNotEmpty(jCreatedContractIDs)) {
				outStream.writeInt(jCreatedContractIDs.size());
				for (JAccountID createdContractID : jCreatedContractIDs) {
					createdContractID.copyTo(outStream);
					createdContractID.copyToExtra(outStream);
				}
			} else {
				outStream.writeInt(0);
			}
		}
	}

	/**
	 * Custom deserialization  of this class. It read first length of the field if it is 0 then sets
	 * field to null otherwise it read bytes from DataInputStream of specified length and deserialize
	 * those byte for the field.
	 *
	 * @return deserialize JContractFunctionResult
	 */
	@SuppressWarnings("unchecked")
	public static <T extends FastCopyable> T deserialize(final DataInputStream inStream) throws IOException {
		final JContractFunctionResult functionResult = new JContractFunctionResult();

		deserialize(inStream, functionResult);
		return (T) functionResult;
	}

	private static void deserialize(final DataInputStream inStream,
			final JContractFunctionResult functionResult) throws IOException {
		final long version = inStream.readLong();
		if (version < LEGACY_VERSION_1 || version > CURRENT_VERSION) {
			throw new IllegalStateException("Illegal version was read from the stream");
		}

		if (version != CURRENT_VERSION) {
			functionResult.versionToSerialize = OptionalLong.of(version);
		}

		final long objectType = inStream.readLong();
		final JObjectType type = JObjectType.valueOf(objectType);
		if (!JObjectType.JContractFunctionResult.equals(type)) {
			throw new IllegalStateException("Illegal JObjectType was read from the stream");
		}

		boolean contractIDPresent;

		if (version == LEGACY_VERSION_1) {
			contractIDPresent = inStream.readInt() > 0;
		} else {
			contractIDPresent = inStream.readBoolean();
		}

		if (contractIDPresent) {
			functionResult.contractID = JAccountID.deserialize(inStream);
		} else {
			functionResult.contractID = null;
		}

		final byte[] RBytes = new byte[inStream.readInt()];
		if (RBytes.length > 0) {
			inStream.readFully(RBytes);
		}
		functionResult.result = RBytes;

		final byte[] eBytes = new byte[inStream.readInt()];
		if (eBytes.length > 0) {
			inStream.readFully(eBytes);
			functionResult.error = StringUtils.newStringUtf8(eBytes);
		} else {
			functionResult.error = null;
		}

		final byte[] BBytes = new byte[inStream.readInt()];
		if (BBytes.length > 0) {
			inStream.readFully(BBytes);
		}
		functionResult.bloom = BBytes;

		functionResult.gasUsed = inStream.readLong();

		final List<JContractLogInfo> jContractLogInfoList = new ArrayList<>();
		final int listSize = inStream.readInt();
		if (listSize > 0) {
			for (int i = 0; i < listSize; i++) {
				jContractLogInfoList.add(JContractLogInfo.deserialize(inStream));
			}
		}
		functionResult.jContractLogInfo = jContractLogInfoList;

		final List<JAccountID> jCreatedContractIDs = new ArrayList<>();
		if (version > LEGACY_VERSION_2) {
			final int numberOfCreatedContractID = inStream.readInt();
			if (numberOfCreatedContractID > 0) {
				for (int i = 0; i < numberOfCreatedContractID; i++) {
					jCreatedContractIDs.add(JAccountID.deserialize(inStream));
				}
			}
		}
		functionResult.jCreatedContractIDs = jCreatedContractIDs;
	}

	@Override
	public FastCopyable copy() {
		return new JContractFunctionResult(this);
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
}
