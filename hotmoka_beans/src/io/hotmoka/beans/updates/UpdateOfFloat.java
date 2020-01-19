package io.hotmoka.beans.updates;

import io.hotmoka.beans.annotations.Immutable;
import io.hotmoka.beans.signatures.FieldSignature;
import io.hotmoka.beans.values.FloatValue;
import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.beans.values.StorageValue;

/**
 * An update of a field states that a float field of a given storage object has been
 * modified to a given value. Updates are stored in blockchain and
 * describe the shape of storage objects.
 */
@Immutable
public final class UpdateOfFloat extends AbstractUpdateOfField {

	private static final long serialVersionUID = 3816173313618262315L;

	/**
	 * The new value of the field.
	 */
	public final float value;

	/**
	 * Builds an update of an {@code float} field.
	 * 
	 * @param object the storage reference of the object whose field is modified
	 * @param field the field that is modified
	 * @param value the new value of the field
	 */
	public UpdateOfFloat(StorageReference object, FieldSignature field, float value) {
		super(object, field);

		this.value = value;
	}

	@Override
	public StorageValue getValue() {
		return new FloatValue(value);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof UpdateOfFloat && super.equals(other) && ((UpdateOfFloat) other).value == value;
	}

	@Override
	public int hashCode() {
		return super.hashCode() ^ Float.hashCode(value);
	}

	@Override
	public int compareTo(Update other) {
		int diff = super.compareTo(other);
		if (diff != 0)
			return diff;
		else
			return Float.compare(value, ((UpdateOfFloat) other).value);
	}
}