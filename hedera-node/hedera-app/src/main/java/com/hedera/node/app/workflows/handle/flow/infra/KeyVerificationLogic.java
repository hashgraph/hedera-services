package com.hedera.node.app.workflows.handle.flow.infra;

import com.hedera.node.app.signature.DefaultKeyVerifier;
import com.hedera.node.app.signature.KeyVerifier;

import javax.inject.Singleton;

public class KeyVerificationLogic {
    private final KeyVerifier keyVerifier;
    public KeyVerificationLogic(DefaultKeyVerifier defaultKeyVerifier) {
        this.keyVerifier = defaultKeyVerifier;
    }
}
