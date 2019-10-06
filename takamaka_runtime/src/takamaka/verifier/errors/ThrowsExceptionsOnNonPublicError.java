package takamaka.verifier.errors;

import org.apache.bcel.generic.ClassGen;

import takamaka.verifier.Error;

public class ThrowsExceptionsOnNonPublicError extends Error {

	public ThrowsExceptionsOnNonPublicError(ClassGen clazz, String methodName) {
		super(clazz, methodName, -1, "@ThrowsExceptions can only be applied to a public constructor or method");
	}
}