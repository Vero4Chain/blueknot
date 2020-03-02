package takamaka.tests.errors;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.beans.TransactionException;
import io.hotmoka.beans.references.Classpath;
import io.hotmoka.beans.references.TransactionReference;
import io.hotmoka.beans.requests.JarStoreTransactionRequest;
import io.hotmoka.beans.requests.StaticMethodCallTransactionRequest;
import io.hotmoka.beans.signatures.VoidMethodSignature;
import io.hotmoka.beans.types.ClassType;
import io.hotmoka.nodes.CodeExecutionException;
import io.takamaka.code.memory.InitializedMemoryBlockchain;

class LegalCall5 {
	private static final BigInteger _20_000 = BigInteger.valueOf(20_000);
	private static final BigInteger _1_000_000_000 = BigInteger.valueOf(1_000_000_000);

	/**
	 * The blockchain under test. This is recreated before each test.
	 */
	private InitializedMemoryBlockchain blockchain;

	@BeforeEach
	void beforeEach() throws Exception {
		blockchain = new InitializedMemoryBlockchain(Paths.get("../distribution/dist/io-takamaka-code-1.0.jar"), _1_000_000_000);
	}

	@Test @DisplayName("install jar")
	void installJar() throws TransactionException, CodeExecutionException, IOException {
		blockchain.addJarStoreTransaction
			(new JarStoreTransactionRequest(blockchain.account(0), _20_000, BigInteger.ONE, blockchain.takamakaBase,
			Files.readAllBytes(Paths.get("../takamaka_examples/dist/legalcall5.jar")), blockchain.takamakaBase));
	}

	@Test @DisplayName("new C().foo()")
	void newTestToString() throws TransactionException, CodeExecutionException, IOException {
		TransactionReference jar = blockchain.addJarStoreTransaction
			(new JarStoreTransactionRequest(blockchain.account(0), _20_000, BigInteger.ONE, blockchain.takamakaBase,
			Files.readAllBytes(Paths.get("../takamaka_examples/dist/legalcall5.jar")), blockchain.takamakaBase));

		blockchain.addStaticMethodCallTransaction(new StaticMethodCallTransactionRequest
			(blockchain.account(0), _20_000, BigInteger.ONE, new Classpath(jar, true),
			new VoidMethodSignature(new ClassType("io.takamaka.tests.errors.legalcall5.C"), "foo")));
	}
}