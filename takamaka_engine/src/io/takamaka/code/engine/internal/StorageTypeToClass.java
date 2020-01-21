package io.takamaka.code.engine.internal;

import io.hotmoka.beans.types.BasicTypes;
import io.hotmoka.beans.types.ClassType;
import io.hotmoka.beans.types.StorageType;
import io.takamaka.code.engine.AbstractBlockchain;

/**
 * An object that translates storage types into their run-time class tag.
 */
public class StorageTypeToClass {

	/**
	 * The blockchain for which the translation is performed.
	 */
	private final AbstractBlockchain blockchain;

	/**
	 * Builds an object that translates storage types into their run-time class tag.
	 * 
	 * @param blockchain the blockchain for which the translation is performed
	 */
	public StorageTypeToClass(AbstractBlockchain blockchain) {
		this.blockchain = blockchain;
	}

	/**
	 * Yields the class object that represents the given storage type in the Java language,
	 * for the current transaction.
	 * 
	 * @param type the storage type
	 * @return the class object, if any
	 * @throws ClassNotFoundException if some class type cannot be found
	 */
	public Class<?> toClass(StorageType type) throws ClassNotFoundException {
		if (type instanceof BasicTypes) {
			switch ((BasicTypes) type) {
			case BOOLEAN: return boolean.class;
			case BYTE: return byte.class;
			case CHAR: return char.class;
			case SHORT: return short.class;
			case INT: return int.class;
			case LONG: return long.class;
			case FLOAT: return float.class;
			case DOUBLE: return double.class;
			}
		}
		else if (type instanceof ClassType)
			return blockchain.classLoader.loadClass(((ClassType) type).name);
	
		throw new IllegalArgumentException("unexpected storage type");
	}
}