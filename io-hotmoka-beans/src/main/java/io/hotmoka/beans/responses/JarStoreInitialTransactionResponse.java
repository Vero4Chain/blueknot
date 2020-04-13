package io.hotmoka.beans.responses;

import java.util.stream.Stream;

import io.hotmoka.beans.annotations.Immutable;
import io.hotmoka.beans.references.Classpath;
import io.hotmoka.beans.references.TransactionReference;

/**
 * A response for a transaction that installs a jar in a yet not initialized blockchain.
 */
@Immutable
public class JarStoreInitialTransactionResponse implements InitialTransactionResponse, TransactionResponseWithInstrumentedJar {

	private static final long serialVersionUID = 7320005929052884412L;

	/**
	 * The bytes of the jar to install, instrumented.
	 */
	private final byte[] instrumentedJar;

	/**
	 * The dependencies of the jar, previously installed in blockchain.
	 * This is a copy of the same information contained in the request.
	 */
	private final Classpath[] dependencies;

	/**
	 * Builds the transaction response.
	 * 
	 * @param instrumentedJar the bytes of the jar to install, instrumented
	 * @param dependencies the dependencies of the jar, previously installed in blockchain
	 */
	public JarStoreInitialTransactionResponse(byte[] instrumentedJar, Stream<Classpath> dependencies) {
		this.instrumentedJar = instrumentedJar.clone();
		this.dependencies = dependencies.toArray(Classpath[]::new);
	}

	@Override
	public byte[] getInstrumentedJar() {
		return instrumentedJar.clone();
	}

	@Override
	public int getInstrumentedJarLength() {
		return instrumentedJar.length;
	}

	@Override
	public Stream<Classpath> getDependencies() {
		return Stream.of(dependencies);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
        for (byte b: instrumentedJar)
            sb.append(String.format("%02x", b));

        return getClass().getSimpleName() + ":\n  instrumented jar: " + sb.toString();
	}

	/**
	 * Yields the outcome of the execution having this response, performed
	 * at the given transaction reference.
	 * 
	 * @param transactionReference the transaction reference
	 * @return the outcome
	 */
	public TransactionReference getOutcomeAt(TransactionReference transactionReference) {
		// the result of installing a jar in a node is the reference to the transaction that installed the jar
		return transactionReference;
	}
}