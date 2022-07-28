/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hedera.services.sigs.verification;

/**
 * Defines a type of precheck validation failure in which the payer account does not exist or is
 * otherwise invalid. (As opposed to an invalid non-payer account involved in a query payment.)
 *
 * <p>This allows control flow in the {@link PrecheckVerifier} to maintain the behavior of the
 * existing implementation of synchronous verification.
 */
public class InvalidPayerAccountException extends Exception {}
