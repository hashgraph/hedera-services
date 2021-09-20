package com.hedera.services.state.jasperdb.files.hashmap;

class FindResult {
    public final int entryOffset;
    public final boolean found;

    public FindResult(int entryOffset, boolean found) {
        this.entryOffset = entryOffset;
        this.found = found;
    }
}
