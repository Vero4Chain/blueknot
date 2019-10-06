package takamaka.verifier.errors;

import org.apache.bcel.generic.ClassGen;

import takamaka.verifier.Error;

public class IllegalJsrInstructionError extends Error {

	public IllegalJsrInstructionError(ClassGen clazz, String methodName, int line) {
		super(clazz, methodName, line, "bytecode JSR is not allowed");
	}
}