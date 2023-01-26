package com.hedera.node.app.hapi.utils.records;

import com.hedera.services.stream.proto.RecordStreamFile;
import com.hedera.services.stream.proto.SidecarFile;

import java.util.List;

public record RecordWithSidecars(RecordStreamFile recordFile, List<SidecarFile> sidecarFiles) {}
