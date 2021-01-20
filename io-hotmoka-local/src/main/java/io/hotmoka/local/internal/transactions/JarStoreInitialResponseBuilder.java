package io.hotmoka.local.internal.transactions;

import io.hotmoka.beans.InternalFailureException;
import io.hotmoka.beans.TransactionRejectedException;
import io.hotmoka.beans.references.TransactionReference;
import io.hotmoka.beans.requests.JarStoreInitialTransactionRequest;
import io.hotmoka.beans.responses.JarStoreInitialTransactionResponse;
import io.hotmoka.local.EngineClassLoader;
import io.hotmoka.local.InitialResponseBuilder;
import io.hotmoka.local.internal.EngineClassLoaderImpl;
import io.hotmoka.local.internal.NodeInternal;
import io.takamaka.code.instrumentation.InstrumentedJar;
import io.takamaka.code.verification.VerificationException;
import io.takamaka.code.verification.VerifiedJar;

/**
 * Builds the creator of response for a transaction that installs a jar in the node, during its initialization.
 */
public class JarStoreInitialResponseBuilder extends InitialResponseBuilder<JarStoreInitialTransactionRequest, JarStoreInitialTransactionResponse> {

	/**
	 * Creates the builder of the response.
	 * 
	 * @param reference the reference to the transaction that is building the response
	 * @param request the request of the transaction
	 * @param node the node that is running the transaction
	 * @throws TransactionRejectedException if the builder cannot be created
	 */
	public JarStoreInitialResponseBuilder(TransactionReference reference, JarStoreInitialTransactionRequest request, NodeInternal node) throws TransactionRejectedException {
		super(reference, request, node);
	}

	@Override
	protected EngineClassLoader mkClassLoader() throws Exception {
		// we redefine this method, since the class loader must be able to access the
		// jar that is being installed and its dependencies, in order to instrument them
		return new EngineClassLoaderImpl(request.getJar(), request.getDependencies(), node, true, consensus);
	}

	@Override
	public JarStoreInitialTransactionResponse getResponse() throws TransactionRejectedException {
		return new ResponseCreator() {

			@Override
			protected JarStoreInitialTransactionResponse body() {
				try {
					InstrumentedJar instrumentedJar = InstrumentedJar.of(VerifiedJar.of(request.getJar(), classLoader, consensus.verificationVersion, true, consensus.allowsSelfCharged), node.getGasCostModel());
					return new JarStoreInitialTransactionResponse(instrumentedJar.toBytes(), request.getDependencies(), consensus.verificationVersion);
				}
				catch (VerificationException e) {
					throw e;
				}
				catch (Throwable t) {
					throw InternalFailureException.of(t);
				}
			}
		}
		.create();
	}
}