package io.takamaka.code.blockchain.internal;

import java.math.BigInteger;

import io.hotmoka.beans.values.BigIntegerValue;
import io.hotmoka.beans.values.BooleanValue;
import io.hotmoka.beans.values.ByteValue;
import io.hotmoka.beans.values.CharValue;
import io.hotmoka.beans.values.DoubleValue;
import io.hotmoka.beans.values.EnumValue;
import io.hotmoka.beans.values.FloatValue;
import io.hotmoka.beans.values.IntValue;
import io.hotmoka.beans.values.LongValue;
import io.hotmoka.beans.values.NullValue;
import io.hotmoka.beans.values.ShortValue;
import io.hotmoka.beans.values.StorageValue;
import io.hotmoka.beans.values.StringValue;
import io.takamaka.code.blockchain.AbstractBlockchain;
import io.takamaka.code.constants.Constants;

/**
 * An object that translates RAM values into storage values.
 */
public class Serializer {

	/**
	 * The blockchain for which serialization is performed.
	 */
	private final AbstractBlockchain blockchain;

	/**
	 * Builds an object that translates RAM values into storage values.
	 * 
	 * @param blockchain the blockchain for which serialization is performed
	 */
	public Serializer(AbstractBlockchain blockchain) {
		this.blockchain = blockchain;
	}

	/**
	 * Yields the serialization of the given object, that is, yields its
	 * representation in blockchain.
	 * 
	 * @param object the object to serialize. This must be a storage object, a Java wrapper
	 *               object for numerical types, an enumeration
	 *               or a special Java object that is allowed
	 *               in blockchain, such as a {@link java.lang.String} or {@link java.math.BigInteger}
	 * @return the serialization of {@code object}, if any
	 * @throws IllegalArgumentException if the type of {@code object} is not allowed in blockchain
	 */
	public StorageValue serialize(Object object) throws IllegalArgumentException {
		if (blockchain.isStorage(object))
			return blockchain.getStorageReferenceOf(object);
		else if (object instanceof BigInteger)
			return new BigIntegerValue((BigInteger) object);
		else if (object instanceof Boolean)
			return new BooleanValue((Boolean) object);
		else if (object instanceof Byte)
			return new ByteValue((Byte) object);
		else if (object instanceof Character)
			return new CharValue((Character) object);
		else if (object instanceof Double)
			return new DoubleValue((Double) object);
		else if (object instanceof Float)
			return new FloatValue((Float) object);
		else if (object instanceof Integer)
			return new IntValue((Integer) object);
		else if (object instanceof Long)
			return new LongValue((Long) object);
		else if (object instanceof Short)
			return new ShortValue((Short) object);
		else if (object instanceof String)
			return new StringValue((String) object);
		else if (object instanceof Enum<?>)
			return new EnumValue(object.getClass().getName(), ((Enum<?>) object).name());
		else if (object == null)
			return NullValue.INSTANCE;
		else
			throw new IllegalArgumentException("an object of class " + object.getClass().getName()
				+ " cannot be kept in blockchain since it does not implement " + Constants.STORAGE_NAME);
	}
}