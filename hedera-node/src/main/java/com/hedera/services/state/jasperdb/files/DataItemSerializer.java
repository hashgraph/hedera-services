package com.hedera.services.state.jasperdb.files;

import java.nio.ByteBuffer;

/**
 * Interface for serializers of DataItems, a data item consists of a key and a value.
 *
 * @param <T> The type for the data item, expected to contain the key/value pair
 */
public interface DataItemSerializer<T> extends BaseSerializer<T>{

     /**
      * Get the number of bytes used for data item header
      *
      * @return size of header in bytes
      */
     int getHeaderSize();

     /**
      * Deserialize data item header from the given byte buffer
      *
      * @param buffer Buffer to read from
      * @return The read header
      */
     DataItemHeader deserializeHeader(ByteBuffer buffer);
}
