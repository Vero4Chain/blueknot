/*
Copyright 2021 Fausto Spoto

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package io.hotmoka.nodes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An exception thrown when a storage reference cannot be deserialized.
 */
@SuppressWarnings("serial")
public class DeserializationError extends Error {
	private final static Logger logger = LoggerFactory.getLogger(DeserializationError.class);

	public DeserializationError(String message) {
		super(message);
		logger.error(message, this);
	}

	public DeserializationError(Throwable cause) {
		super("Cannot deserialize value", cause);
		logger.error(getMessage(), this);
	}
}
