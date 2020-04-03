package io.takamaka.code.engine.internal.transactions;

import java.lang.reflect.Method;

import io.hotmoka.beans.TransactionException;
import io.hotmoka.beans.references.TransactionReference;
import io.hotmoka.beans.requests.InstanceMethodCallTransactionRequest;
import io.hotmoka.beans.requests.NonInitialTransactionRequest;
import io.hotmoka.nodes.Node;
import io.takamaka.code.constants.Constants;

/**
 * Builds the creator of a transaction that executes an instance method of Takamaka code
 * annotated as {@linkplain io.hotmoka.code.lang.View}.
 */
public class InstanceViewMethodCallTransactionBuilder extends InstanceMethodCallTransactionBuilder {

	/**
	 * Builds the creator of a transaction that executes an instance method of Takamaka code
	 * annotated as {@linkplain io.hotmoka.code.lang.View}. 
	 * 
	 * @param request the request of the transaction
	 * @param current the reference that must be used for the transaction
	 * @param node the node that is running the transaction
	 * @throws TransactionException if the transaction cannot be created
	 */
	public InstanceViewMethodCallTransactionBuilder(InstanceMethodCallTransactionRequest request, TransactionReference current, Node node) throws TransactionException {
		super(request, current, node);
	}

	@Override
	protected void validateTarget(Method methodJVM) throws NoSuchMethodException {
		super.validateTarget(methodJVM);

		if (!hasAnnotation(methodJVM, Constants.VIEW_NAME))
			throw new NoSuchMethodException("cannot call a method not annotated as @View");
	}

	@Override
	protected final void nonceOfCallerMustMatch(NonInitialTransactionRequest<?> request) {
		// we disable the check, since the nonce is not checked in view transactions
	}

	@Override
	protected void setNonceAfter(NonInitialTransactionRequest<?> request) {
		// we disable the nonce increment for view transactions
	}
}