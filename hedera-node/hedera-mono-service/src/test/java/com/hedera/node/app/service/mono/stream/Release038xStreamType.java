package com.hedera.node.app.service.mono.stream;

public enum Release038xStreamType implements RecordStreamType {
    RELEASE_038x_STREAM_TYPE;

    private static final int[] RELEASE_038x_FILE_HEADER = new int[] {5, 0, 38, 0};

    @Override
    public int[] getFileHeader() {
        return RELEASE_038x_FILE_HEADER;
    }

    @Override
    public byte[] getSigFileHeader() {
        return new byte[] {(byte) RELEASE_038x_FILE_HEADER[0]};
    }
}
