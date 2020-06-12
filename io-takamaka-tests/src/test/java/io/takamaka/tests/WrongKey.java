/**
 * 
 */
package io.takamaka.tests;

import static io.hotmoka.beans.Coin.panarea;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SignatureException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.beans.CodeExecutionException;
import io.hotmoka.beans.TransactionException;
import io.hotmoka.beans.TransactionRejectedException;
import io.hotmoka.beans.requests.ConstructorCallTransactionRequest;
import io.hotmoka.beans.requests.NonInitialTransactionRequest;
import io.hotmoka.beans.requests.NonInitialTransactionRequest.Signer;
import io.hotmoka.beans.signatures.ConstructorSignature;
import io.hotmoka.beans.types.BasicTypes;
import io.hotmoka.beans.types.ClassType;
import io.hotmoka.beans.values.IntValue;
import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.crypto.SignatureAlgorithm;

/**
 * A test for wrong use of keys for signing a transaction.
 */
class WrongKey extends TakamakaTest {
	private static final ConstructorSignature ABSTRACT_FAIL_IMPL_CONSTRUCTOR = new ConstructorSignature(new ClassType("io.takamaka.tests.abstractfail.AbstractFailImpl"), BasicTypes.INT);
	private static final BigInteger _20_000 = BigInteger.valueOf(20_000);

	@BeforeEach
	void beforeEach() throws Exception {
		setNode("abstractfail.jar", _20_000, _20_000);
	}

	@Test @DisplayName("constructor call with wrong key fails")
	void createAbstractFailImpl() throws TransactionException, CodeExecutionException, TransactionRejectedException, InvalidKeyException, SignatureException, NoSuchAlgorithmException {
		SignatureAlgorithm<NonInitialTransactionRequest<?>> signature = node.signatureAlgorithmForRequests();

		// key 1 for account 0 !
		PrivateKey key = privateKey(1);
		StorageReference caller = account(0);

		throwsTransactionRejectedWithCause("invalid request signature", () -> {
			node.addConstructorCallTransaction(new ConstructorCallTransactionRequest(Signer.with(signature, key), caller, BigInteger.ZERO, _20_000, panarea(1), jar(), ABSTRACT_FAIL_IMPL_CONSTRUCTOR, new IntValue(42)));
		});
	}
}