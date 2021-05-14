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

package io.hotmoka.crypto;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.NoSuchAlgorithmException;

import io.hotmoka.beans.requests.SignedTransactionRequest;

/**
 * An algorithm that signs transaction requests and verifies such signatures back.
 */
public interface SignatureAlgorithmForTransactionRequests extends SignatureAlgorithm<SignedTransactionRequest> {

	/**
	 * Yields a signature algorithm for transaction requests that uses the SHA256 hashing algorithm and then the DSA algorithm.
	 * 
	 * @return the algorithm
	 * @throws NoSuchAlgorithmException if the installation of Java does not include the SHA256withDSA algorithm
	 */
	static SignatureAlgorithm<SignedTransactionRequest> sha256dsa() throws NoSuchAlgorithmException {
		return SignatureAlgorithm.sha256dsa(SignedTransactionRequest::toByteArrayWithoutSignature);
	}

	/**
	 * Yields the ed25519 signature algorithm for transaction requests.
	 * 
	 * @return the algorithm
	 * @throws NoSuchAlgorithmException if the installation does not include the ed25519 algorithm
	 */
	static SignatureAlgorithm<SignedTransactionRequest> ed25519() throws NoSuchAlgorithmException {
		return SignatureAlgorithm.ed25519(SignedTransactionRequest::toByteArrayWithoutSignature);
	}

	/**
	 * Yields a signature algorithm for transaction requests that uses the ed25519 cryptography. It generates
	 * keys in a deterministic order, hence must NOT be used in production.
	 * It is useful instead for testing, since it makes deterministic the
	 * sequence of keys of the accounts in the tests and consequently
	 * also the gas costs of such accounts when they are put into maps, for instance.
	 */
	static SignatureAlgorithm<SignedTransactionRequest> ed25519det() throws NoSuchAlgorithmException {
		return SignatureAlgorithm.ed25519det(SignedTransactionRequest::toByteArrayWithoutSignature);
	}

	/**
	 * Yields the qTESLA-p-I signature algorithm for transaction requests.
	 *
	 * @return the algorithm
	 * @throws NoSuchAlgorithmException if the installation does not include the qTESLA-p-I algorithm
	 */
	static SignatureAlgorithm<SignedTransactionRequest> qtesla1() throws NoSuchAlgorithmException {
		return SignatureAlgorithm.qtesla1(SignedTransactionRequest::toByteArrayWithoutSignature);
	}

	/**
	 * Yields the qTESLA-p-III signature algorithm for transaction requests.
	 *
	 * @return the algorithm
	 * @throws NoSuchAlgorithmException if the installation does not include the qTESLA-p-III algorithm
	 */
	static SignatureAlgorithm<SignedTransactionRequest> qtesla3() throws NoSuchAlgorithmException {
		return SignatureAlgorithm.qtesla3(SignedTransactionRequest::toByteArrayWithoutSignature);
	}

	/**
	 * Yields an empty signature algorithm that signs all transaction requests with an empty array of bytes.
	 * 
	 * @return the algorithm
	 */
	static SignatureAlgorithm<SignedTransactionRequest> empty() {
		return SignatureAlgorithm.empty(SignedTransactionRequest::toByteArrayWithoutSignature);
	}

	/**
	 * Yields the signature algorithm for transaction requests with the given name.
	 * It looks for a factory method with the given name and invokes it.
	 * 
	 * @param name the name of the algorithm, case-insensitive
	 * @return the algorithm
	 * @throws NoSuchAlgorithmException if the installation does not include the given algorithm
	 */
	@SuppressWarnings("unchecked")
	static SignatureAlgorithm<SignedTransactionRequest> mk(String name) throws NoSuchAlgorithmException {
		name = name.toLowerCase();

		try {
			// only sha256dsa, ed25519, empty, qtesla1 and qtesla3 are currently found below
			Method method = SignatureAlgorithmForTransactionRequests.class.getMethod(name);
			return (SignatureAlgorithm<SignedTransactionRequest>) method.invoke(null);
		}
		catch (NoSuchMethodException | SecurityException | InvocationTargetException | IllegalAccessException | IllegalArgumentException e) {
			throw new NoSuchAlgorithmException("unknown signature algorithm named " + name, e);
		}
	}

	/**
	 * Yields the signature algorithm for transaction requests with the given type.
	 * 
	 * @param type the type of the algorithm
	 * @return the algorithm
	 * @throws NoSuchAlgorithmException if the installation does not include the given algorithm
	 */
	static SignatureAlgorithm<SignedTransactionRequest> mk(TYPES type) throws NoSuchAlgorithmException {
		return mk(type.name());
	}
}