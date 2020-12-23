package com.hedera.services.stream;

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
	 * Header which is written in the beginning of a stream file, before writing the Object Stream Version.
	 * the ints in fileHeader denote: version 5, hapiProtoVersion: 0.9.0
	 */
	private static final int[] RECORD_FILE_HEADER = new int[] { 5, 0, 9, 0 };
	/**
	 * Header which is written in the beginning of a stream signature file, before writing the Object Stream Signature
	 * Version.
	 * the int in sigFileHeader denotes version 5
	 */
	private static final int[] RECORD_SIG_FILE_HEADER = new int[] { 5 };

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
	public int[] getSigFileHeader() {
		return RECORD_SIG_FILE_HEADER;
	}
}

