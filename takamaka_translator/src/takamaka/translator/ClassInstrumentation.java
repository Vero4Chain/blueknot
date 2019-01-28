package takamaka.translator;

import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarOutputStream;

import org.apache.bcel.classfile.ClassFormatException;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.generic.ClassGen;

class ClassInstrumentation {

	public ClassInstrumentation(InputStream input, String className, JarOutputStream instrumentedJar) throws ClassFormatException, IOException {
		System.out.println("Instrumenting " + className);
		ClassGen classGen = new ClassGen(new ClassParser(input, className).parse());
		instrument(classGen);
		classGen.getJavaClass().dump(instrumentedJar);
	}

	private void instrument(ClassGen classGen) {
		//TODO
	}
}