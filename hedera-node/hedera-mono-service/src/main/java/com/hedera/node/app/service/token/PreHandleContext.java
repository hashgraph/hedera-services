package com.hedera.node.app.service.token;

import com.hedera.services.sigs.order.SignatureWaivers;

/**
 * This class should be moved to spi once all modules are seperated
 * @param signatureWaivers
 */
public record PreHandleContext(SignatureWaivers signatureWaivers) {
}