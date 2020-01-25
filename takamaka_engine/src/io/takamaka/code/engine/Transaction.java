package io.takamaka.code.engine;

import io.hotmoka.beans.TransactionException;
import io.hotmoka.beans.references.TransactionReference;
import io.hotmoka.beans.requests.JarStoreInitialTransactionRequest;
import io.hotmoka.beans.requests.TransactionRequest;
import io.hotmoka.beans.responses.JarStoreInitialTransactionResponse;
import io.hotmoka.beans.responses.TransactionResponse;
import io.takamaka.code.engine.internal.transactions.AbstractTransaction;
import io.takamaka.code.engine.internal.transactions.JarStoreInitialTransactionRun;

/**
 * A transaction of HotMoka code: it is the execution of a
 * request, that led to a response.
 *
 * @param <Request> the type of the request of this transaction
 * @param <Response> the type of the response of this transaction
 */
public interface Transaction<Request extends TransactionRequest<Response>, Response extends TransactionResponse> {

	/**
	 * The request from where this transaction started.
	 * 
	 * @return the request
	 */
	Request getRequest();

	/**
	 * The response into which this transaction led.
	 * 
	 * @return the response
	 */
	Response getResponse();

	/**
	 * Yields a transaction that installs a jar in the given node. This transaction can only occur during initialization
	 * of the node. It has no caller and requires no gas. The goal is to install, in the
	 * node, some basic jars that are likely needed as dependencies by future jars.
	 * For instance, the jar containing the basic Takamaka classes. This method runs the transaction
	 * specified by the request, after the given transaction reference.
	 * 
	 * @param request the transaction request
	 * @param current the reference to the transaction after which the new transaction must be executed
	 * @return the transaction
	 * @throws TransactionException if the transaction could not be completed successfully
	 */
	static AbstractTransaction<JarStoreInitialTransactionRequest, JarStoreInitialTransactionResponse> mkFor(JarStoreInitialTransactionRequest request, TransactionReference current, Node node) throws TransactionException {
		return new AbstractTransaction<>(request, new JarStoreInitialTransactionRun(request, current, node).response);
	}
}