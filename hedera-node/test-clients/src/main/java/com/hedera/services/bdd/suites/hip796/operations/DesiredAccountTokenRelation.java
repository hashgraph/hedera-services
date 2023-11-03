package com.hedera.services.bdd.suites.hip796.operations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DesiredAccountTokenRelation {
    private boolean frozen;
    private boolean kycGranted;
    private boolean locked;
    private long balance;
    private List<Long> ownedSerialNos = new ArrayList<>();
    private Map<String, DesiredAccountTokenRelation> desiredPartitionRelations = new HashMap<>();

     
}
