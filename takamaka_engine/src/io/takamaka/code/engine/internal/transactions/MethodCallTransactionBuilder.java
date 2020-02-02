package io.takamaka.code.engine.internal.transactions;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Optional;

import io.hotmoka.beans.TransactionException;
import io.hotmoka.beans.references.TransactionReference;
import io.hotmoka.beans.requests.MethodCallTransactionRequest;
import io.hotmoka.beans.responses.MethodCallTransactionResponse;
import io.hotmoka.beans.signatures.FieldSignature;
import io.hotmoka.beans.signatures.MethodSignature;
import io.hotmoka.beans.signatures.NonVoidMethodSignature;
import io.hotmoka.beans.updates.UpdateOfField;
import io.hotmoka.nodes.Node;
import io.hotmoka.nodes.NonWhiteListedCallException;

/**
 * A generic implementation of a blockchain. Specific implementations can subclass this class
 * and just implement the abstract template methods. The rest of code should work instead
 * as a generic layer for all blockchain implementations.
 */
public abstract class MethodCallTransactionBuilder<Request extends MethodCallTransactionRequest> extends CodeCallTransactionBuilder<Request, MethodCallTransactionResponse> {

	/**
	 * The method that is being called.
	 */
	protected final MethodSignature method;

	protected MethodCallTransactionBuilder(Request request, TransactionReference current, Node node) throws TransactionException {
		super(request, current, node);

		this.method = request.method;
	}

	/**
	 * Resolves the method that must be called.
	 * 
	 * @return the method
	 * @throws NoSuchMethodException if the method could not be found
	 * @throws SecurityException if the method could not be accessed
	 * @throws ClassNotFoundException if the class of the method or of some parameter or return type cannot be found
	 */
	protected final Method getMethod() throws ClassNotFoundException, NoSuchMethodException {
		Class<?> returnType = method instanceof NonVoidMethodSignature ? storageTypeToClass.toClass(((NonVoidMethodSignature) method).returnType) : void.class;
		Class<?>[] argTypes = formalsAsClass();

		return getClassLoader().resolveMethod(method.definingClass.name, method.methodName, argTypes, returnType)
			.orElseThrow(() -> new NoSuchMethodException(method.toString()));
	}

	/**
	 * Determines if the execution only affected the balance of the caller contract.
	 * 
	 * @param result the returned value for method calls or created object for constructor calls, if any
	 * @return true if and only if that condition holds
	 */
	protected final boolean onlyAffectedBalanceOfCaller(Object result) {
		return updates(result).allMatch
			(update -> update.object.equals(getClassLoader().getStorageReferenceOf(getDeserializedCaller()))
						&& update instanceof UpdateOfField
						&& ((UpdateOfField) update).getField().equals(FieldSignature.BALANCE_FIELD));
	}

	/**
	 * Checks that the method called by this transaction
	 * is white-listed and its white-listing proof-obligations hold.
	 * 
	 * @param executable the method
	 * @param actuals the actual arguments passed to {@code executable}, including the
	 *                receiver for instance methods
	 * @throws ClassNotFoundException if some class could not be found during the check
	 */
	protected void ensureWhiteListingOf(Method executable, Object[] actuals) throws ClassNotFoundException {
		Optional<Method> model = getClassLoader().getWhiteListingWizard().whiteListingModelOf(executable);
		if (!model.isPresent())
			throw new NonWhiteListedCallException("illegal call to non-white-listed method " + method.definingClass.name + "." + method.methodName);

		Annotation[][] anns = model.get().getParameterAnnotations();
		for (int pos = 0; pos < anns.length; pos++)
			checkWhiteListingProofObligations(model.get().getName(), actuals[pos], anns[pos]);
	}

	@Override
	protected final MethodSignature getMethodOrConstructor() {
		return method;
	}
}