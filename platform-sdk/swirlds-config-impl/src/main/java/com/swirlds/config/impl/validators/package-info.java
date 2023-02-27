/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is the confidential and proprietary information of
 * Hedera Hashgraph, LLC. ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Hedera Hashgraph.
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. HEDERA HASHGRAPH SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */

/**
 * This package provides concrete implementations of the {@link com.swirlds.config.api.validation.ConfigValidator} and
 * {@link com.swirlds.config.api.validation.ConfigPropertyConstraint} interfaces. The instances can be added by using
 * {@link com.swirlds.config.api.ConfigurationBuilder#withValidator(com.swirlds.config.api.validation.ConfigValidator)}
 * By default no validator is added.
 */
package com.swirlds.config.impl.validators;
