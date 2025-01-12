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

package io.hotmoka.tests;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.SignatureException;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.beans.CodeExecutionException;
import io.hotmoka.beans.TransactionException;
import io.hotmoka.beans.TransactionRejectedException;
import io.hotmoka.beans.requests.ConstructorCallTransactionRequest;
import io.hotmoka.beans.requests.GameteCreationTransactionRequest;
import io.hotmoka.beans.requests.SignedTransactionRequest.Signer;
import io.hotmoka.beans.signatures.ConstructorSignature;
import io.hotmoka.beans.types.ClassType;
import io.hotmoka.beans.values.BigIntegerValue;
import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.beans.values.StringValue;
import io.hotmoka.remote.RemoteNode;
import io.hotmoka.takamaka.TakamakaBlockchain;

/**
 * A test for creating an account for free in the Takamaka blockchain.
 */
class CreateAccountForFree extends HotmokaTest {

	@BeforeEach
	void beforeEach() throws Exception {
		setAccounts(_1_000_000_000);
	}

	@Test @DisplayName("create account")
	void createAccount() throws TransactionRejectedException {
		KeyPair keys = signature().getKeyPair();
		String publicKey = Base64.getEncoder().encodeToString(signature().encodingOf(keys.getPublic()));

		if (node instanceof TakamakaBlockchain) {
			// the Takamaka blockchain admits this initial transaction also after initialization of the node
			StorageReference newAccount = node.addGameteCreationTransaction(new GameteCreationTransactionRequest(takamakaCode(), _50_000, _50_000, publicKey));

			assertNotNull(newAccount);
		}
		else if (!(node instanceof RemoteNode)){
			try { 
				// all other nodes are expected to reject this, since the node is already initialized
				node.addGameteCreationTransaction(new GameteCreationTransactionRequest
					(takamakaCode(), _50_000, _50_000, publicKey));
			}
			catch (TransactionRejectedException e) {
				assertTrue(e.getMessage().contains("cannot run a GameteCreationTransactionRequest in an already initialized node"));
				return;
			}

			fail();
		}
	}

	@Test @DisplayName("create account and use it to create another account")
	void createAccountAndUseIt() throws TransactionException, CodeExecutionException, TransactionRejectedException, InvalidKeyException, SignatureException {
		if (node instanceof TakamakaBlockchain) {
			KeyPair keys = signature().getKeyPair();
			String publicKey = Base64.getEncoder().encodeToString(signature().encodingOf(keys.getPublic()));

			// the Takamaka blockchain admits this initial transaction also after initialization of the node
			StorageReference newAccount = node.addGameteCreationTransaction(new GameteCreationTransactionRequest(takamakaCode(), _50_000, _50_000, publicKey));

			// the second account has the same public key as the new account: not really clever
			StorageReference secondAccount = node.addConstructorCallTransaction(new ConstructorCallTransactionRequest
				(Signer.with(signature(), keys), newAccount, BigInteger.ZERO, chainId,
				_50_000, BigInteger.ONE, takamakaCode(),
				new ConstructorSignature(ClassType.EOA, ClassType.BIG_INTEGER, ClassType.STRING),
				new BigIntegerValue(BigInteger.valueOf(100L)), new StringValue(publicKey)));

			assertNotNull(secondAccount);
			assertNotEquals(newAccount, secondAccount);
		}
	}
}