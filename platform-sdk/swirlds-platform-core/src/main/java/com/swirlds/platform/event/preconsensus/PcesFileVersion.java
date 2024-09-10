package com.swirlds.platform.event.preconsensus;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public enum PcesFileVersion {
    /** The original version of the file format. */
    ORIGINAL(1),
    /** The version of the file format that serializes events as protobuf. */
    PROTOBUF_EVENTS(2);

    private static final Set<Integer> SUPPORTED_VERSIONS = Arrays.stream(PcesFileVersion.values()).map(PcesFileVersion::getVersionNumber).collect(Collectors.toSet());
    private static final Map<Integer, PcesFileVersion> VERSION_MAP = Arrays.stream(PcesFileVersion.values()).map(PcesFileVersion::getVersionNumber).collect(Collectors.toSet());

    private final int versionNumber;

    PcesFileVersion(final int versionNumber) {
        this.versionNumber = versionNumber;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public static int currentVersionNumber() {
        return PROTOBUF_EVENTS.getVersionNumber();
    }

    public static Set<Integer> supportedVersions() {
        return SUPPORTED_VERSIONS;
    }

    public static PcesFileVersion fromVersionNumber(final int versionNumber) {
        return Arrays.stream(PcesFileVersion.values()).filter(v -> v.getVersionNumber() == versionNumber).findFirst().orElse(null);
    }
}
