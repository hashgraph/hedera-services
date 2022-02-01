package com.hedera.services.stream;

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

import com.swirlds.common.stream.StreamType;

/**
 * Contains properties related to RecordStream file type;
 * Its constructor is private. Users need to use the singleton to denote this type.
 */
public final class RecordStreamType implements StreamType {
	/**
	 * description of the streamType, used for logging
	 */
	public static final String RECORD_DESCRIPTION = "records";
	/**
	 * file name extension
	 */
	public static final String RECORD_EXTENSION = "rcd";
	/**
	 * file name extension of signature file
	 */
	public static final String RECORD_SIG_EXTENSION = "rcd_sig";
	/**
	 * The {@link com.swirlds.common.stream.TimestampStreamFileWriter} writes this header at the beginning of a
	 * stream file (*.rcd), immediately before its internal stream version.
	 *
	 * The four ints in ths header denote, in order:
	 * <ol>
	 *     <li>The Services record stream version {@literal X}</li>
	 *     <li>The HAPI protobuf major version</li>
	 *     <li>The HAPI protobuf minor version</li>
	 *     <li>The HAPI protobuf patch version</li>
	 * </ol>
	 * So the header below denotes a V5 record stream that uses semantic version 0.23.0 of the HAPI protobufs.
	 */
	private static final int[] RECORD_FILE_HEADER = new int[] { 5, 0, 23, 0 };
	/**
	 * The {@link com.swirlds.common.stream.TimestampStreamFileWriter} writes this header at the beginning of a
	 * stream signature file (*.rcd_sig), immediately before its internal stream signature version.
	 *
	 * The byte in this header denotes the version of the Services signature stream file.
	 */
	private static final byte[] RECORD_SIG_FILE_HEADER = new byte[] { 5 };

	private RecordStreamType() {
	}

	/**
	 * a singleton denotes RecordStreamType
	 */
	public static final RecordStreamType RECORD = new RecordStreamType();

	@Override
	public String getDescription() {
		return RECORD_DESCRIPTION;
	}

	@Override
	public String getExtension() {
		return RECORD_EXTENSION;
	}

	@Override
	public String getSigExtension() {
		return RECORD_SIG_EXTENSION;
	}

	@Override
	public int[] getFileHeader() {
		return RECORD_FILE_HEADER;
	}

	@Override
	public byte[] getSigFileHeader() {
		return RECORD_SIG_FILE_HEADER;
	}
}

