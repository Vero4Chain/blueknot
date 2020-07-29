package io.takamaka.code.tests.errors;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.beans.CodeExecutionException;
import io.hotmoka.beans.TransactionException;
import io.hotmoka.beans.TransactionRejectedException;
import io.hotmoka.beans.signatures.ConstructorSignature;
import io.hotmoka.beans.signatures.NonVoidMethodSignature;
import io.hotmoka.beans.types.BasicTypes;
import io.hotmoka.beans.types.ClassType;
import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.beans.values.StringValue;
import io.hotmoka.nodes.NonWhiteListedCallException;
import io.takamaka.code.tests.TakamakaTest;

class IllegalCallToNonWhiteListedMethod12 extends TakamakaTest {
	private static final BigInteger _20_000 = BigInteger.valueOf(20_000);
	private static final BigInteger _1_000_000_000 = BigInteger.valueOf(1_000_000_000);

	@BeforeEach
	void beforeEach() throws Exception {
		setNode(_1_000_000_000);
	}

	@Test @DisplayName("new ExternallyOwnedAccount().hashCode()")
	void testNonWhiteListedCall() throws TransactionException, CodeExecutionException, TransactionRejectedException, InvalidKeyException, SignatureException, NoSuchAlgorithmException {
		KeyPair keys = signature().getKeyPair();
		String publicKey = Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
		StorageReference eoa = addConstructorCallTransaction(privateKey(0), account(0), _20_000, BigInteger.ONE, takamakaCode(), new ConstructorSignature(ClassType.EOA, ClassType.STRING), new StringValue(publicKey));

		throwsTransactionExceptionWithCause(NonWhiteListedCallException.class, () ->
			addInstanceMethodCallTransaction(privateKey(0), account(0), _20_000, BigInteger.ONE, takamakaCode(), new NonVoidMethodSignature(ClassType.OBJECT, "hashCode", BasicTypes.INT), eoa)
		);
	}
}