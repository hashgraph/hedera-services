package com.hedera.services.state.merkle.virtual;

import com.hedera.services.state.jasperdb.files.DataFileCommon;
import com.hedera.services.state.jasperdb.files.DataItemHeader;
import com.hedera.services.state.jasperdb.files.DataItemSerializer;
import com.swirlds.common.io.SerializableDataOutputStream;

import java.io.IOException;
import java.nio.ByteBuffer;

public class ContractKeySerializer implements DataItemSerializer<ContractKey> {
    private final long currentDataVersion;

    public ContractKeySerializer() {
        currentDataVersion = new ContractKey().getVersion();
    }

    /**
     * Get the number of bytes used for data item header
     *
     * @return size of header in bytes
     */
    @Override
    public int getHeaderSize() {
        return 1;
    }

    /**
     * Deserialize data item header from the given byte buffer
     *
     * @param buffer Buffer to read from
     * @return The read header
     */
    @Override
    public DataItemHeader deserializeHeader(ByteBuffer buffer) {
        return new DataItemHeader(ContractKey.readKeySize(buffer),-1);
    }

    /**
     * Get if the number of bytes a data item takes when serialized is variable or fixed
     *
     * @return true if getSerializedSize() == DataFileCommon.VARIABLE_DATA_SIZE
     */
    @Override
    public boolean isVariableSize() {
        return true;
    }

    /**
     * Get the number of bytes a data item takes when serialized
     *
     * @return Either a number of bytes or DataFileCommon.VARIABLE_DATA_SIZE if size is variable
     */
    @Override
    public int getSerializedSize() {
        return DataFileCommon.VARIABLE_DATA_SIZE;
    }

    /**
     * For variable sized data get the typical  number of bytes a data item takes when serialized
     *
     * @return Either for fixed size same as getSerializedSize() or an estimated typical size for data items
     */
    @Override
    public int getTypicalSerializedSize() {
        return 20; // assume 50% full typically, max size is (1 + 8 + 32)
    }

    /**
     * Get the current data item serialization version
     */
    @Override
    public long getCurrentDataVersion() {
        return currentDataVersion;
    }

    /**
     * Deserialize a data item from a byte buffer, that was written with given data version
     *
     * @param buffer      The buffer to read from containing the data item including its header
     * @param dataVersion The serialization version the data item was written with
     * @return Deserialized data item
     */
    @Override
    public ContractKey deserialize(ByteBuffer buffer, long dataVersion) throws IOException {
        ContractKey contractKey = new ContractKey();
        contractKey.deserialize(buffer,(int)dataVersion);
        return contractKey;
    }

    /**
     * Serialize a data item including header to the output stream returning the size of the data written
     *
     * @param data         The data item to serialize
     * @param outputStream Output stream to write to
     */
    @Override
    public int serialize(ContractKey data, SerializableDataOutputStream outputStream) throws IOException {
        return  data.serializeReturningByteWritten(outputStream);
    }

    /**
     * Copy the serialized data item in dataItemData into the writingStream. Important if serializedVersion is not the
     * same as current serializedVersion then update the data to the latest serialization.
     *
     * @param serializedVersion The serialized version of the data item in dataItemData
     * @param dataItemSize      The size in bytes of the data item dataItemData
     * @param dataItemData      Buffer containing complete data item including the data item header
     * @param writingStream     The stream to write data item out to
     * @return the number of bytes written, this could be the same as dataItemSize or bigger or smaller if
     * serialization version has changed.
     * @throws IOException if there was a problem writing data item to stream or converting it
     */
    @Override
    public int copyItem(long serializedVersion, int dataItemSize, ByteBuffer dataItemData, SerializableDataOutputStream writingStream) throws IOException {
        return DataItemSerializer.super.copyItem(serializedVersion, dataItemSize, dataItemData, writingStream);
    }
}
