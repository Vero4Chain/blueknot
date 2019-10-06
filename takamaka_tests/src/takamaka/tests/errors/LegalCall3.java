package takamaka.tests.errors;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import takamaka.blockchain.Classpath;
import takamaka.blockchain.CodeExecutionException;
import takamaka.blockchain.NonVoidMethodSignature;
import takamaka.blockchain.TransactionException;
import takamaka.blockchain.TransactionReference;
import takamaka.blockchain.request.JarStoreTransactionRequest;
import takamaka.blockchain.request.StaticMethodCallTransactionRequest;
import takamaka.blockchain.types.BasicTypes;
import takamaka.blockchain.types.ClassType;
import takamaka.blockchain.values.BooleanValue;
import takamaka.memory.InitializedMemoryBlockchain;

class LegalCall3 {
	private static final BigInteger _20_000 = BigInteger.valueOf(20_000);
	private static final BigInteger _1_000_000_000 = BigInteger.valueOf(1_000_000_000);

	/**
	 * The blockchain under test. This is recreated before each test.
	 */
	private InitializedMemoryBlockchain blockchain;

	@BeforeEach
	void beforeEach() throws Exception {
		blockchain = new InitializedMemoryBlockchain(Paths.get("../takamaka_runtime/dist/takamaka_base.jar"), _1_000_000_000);
	}

	@Test @DisplayName("C.test() == false")
	void callTest() throws TransactionException, CodeExecutionException, IOException {
		TransactionReference jar = blockchain.addJarStoreTransaction
			(new JarStoreTransactionRequest(blockchain.account(0), _20_000, blockchain.takamakaBase,
			Files.readAllBytes(Paths.get("../takamaka_examples/dist/legalcall3.jar")), blockchain.takamakaBase));

		BooleanValue result = (BooleanValue) blockchain.addStaticMethodCallTransaction(new StaticMethodCallTransactionRequest
				(blockchain.account(0), _20_000, new Classpath(jar, true),
				new NonVoidMethodSignature(new ClassType("takamaka.tests.errors.legalcall3.C"), "test", BasicTypes.BOOLEAN)));

		assertFalse(result.value);
	}
}