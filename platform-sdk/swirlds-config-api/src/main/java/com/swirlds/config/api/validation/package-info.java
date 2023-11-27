/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

/**
 * The package provides an api to validate configuration properties at creation time of a configuration. The interface
 * {@link com.swirlds.config.api.validation.ConfigValidator} is the most basic way to add a validation (see
 * {@link
 * com.swirlds.config.api.ConfigurationBuilder#withValidator(com.swirlds.config.api.validation.ConfigValidator)}). When
 * working with config data object (see {@link com.swirlds.config.api.ConfigData}) the
 * {@link com.swirlds.config.api.validation.annotation} package provides annotations to add validation to the config
 * data objects.
 *
 * @see com.swirlds.config.api.validation.ConfigValidator
 * @see com.swirlds.config.api.ConfigurationBuilder#withValidator(com.swirlds.config.api.validation.ConfigValidator)
 */
package com.swirlds.config.api.validation;
