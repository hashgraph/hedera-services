package com.swirlds.baseapi.persistence;

public record Version(int version) {

    public Version checkAgainst(Version other) {
        if (this.version != other.version()) {
            throw new VersionMismatchException();
        }
        return new Version(this.version + 1);
    }

    public static class VersionMismatchException extends RuntimeException {
    }
}