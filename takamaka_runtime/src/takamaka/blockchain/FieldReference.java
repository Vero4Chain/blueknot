package takamaka.blockchain;

import takamaka.blockchain.types.ClassType;
import takamaka.blockchain.types.StorageType;
import takamaka.lang.Immutable;

@Immutable
public final class FieldReference implements Comparable<FieldReference> {
	public final ClassType definingClass;
	public final String name;
	public final StorageType type;

	public FieldReference(ClassType definingClass, String name, StorageType type) {
		this.definingClass = definingClass;
		this.name = name;
		this.type = type;
	}

	public FieldReference(String definingClass, String name, StorageType type) {
		this(new ClassType(definingClass), name, type);
	}

	/**
	 * Builds a reference to a field of class type.
	 * 
	 * @param definingClass the name of the class where the field is defined
	 * @param name the name of the field
	 * @param className the name of the type (class) of the field
	 */
	public FieldReference(String definingClass, String name, String className) {
		this(new ClassType(definingClass), name, new ClassType(className));
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof FieldReference && ((FieldReference) other).definingClass.equals(definingClass)
			&& ((FieldReference) other).name.equals(name) && ((FieldReference) other).type.equals(type);
	}

	@Override
	public int hashCode() {
		return definingClass.hashCode() ^ name.hashCode() ^ type.hashCode();
	}

	@Override
	public String toString() {
		return definingClass + ";" + name + ";" + type;
	}

	@Override
	public int compareTo(FieldReference other) {
		int diff = definingClass.compareAgainst(other.definingClass);
		if (diff != 0)
			return diff;

		diff = name.compareTo(other.name);
		if (diff != 0)
			return diff;
		else
			return type.compareAgainst(other.type);
	}
}