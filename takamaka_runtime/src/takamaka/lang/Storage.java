package takamaka.lang;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import takamaka.blockchain.AbstractBlockchain;
import takamaka.blockchain.FieldSignature;
import takamaka.blockchain.Update;
import takamaka.blockchain.types.BasicTypes;
import takamaka.blockchain.types.ClassType;
import takamaka.blockchain.values.BigIntegerValue;
import takamaka.blockchain.values.BooleanValue;
import takamaka.blockchain.values.ByteValue;
import takamaka.blockchain.values.CharValue;
import takamaka.blockchain.values.DoubleValue;
import takamaka.blockchain.values.FloatValue;
import takamaka.blockchain.values.IntValue;
import takamaka.blockchain.values.LongValue;
import takamaka.blockchain.values.NullValue;
import takamaka.blockchain.values.ShortValue;
import takamaka.blockchain.values.StorageReference;
import takamaka.blockchain.values.StringValue;

public abstract class Storage {
	public final StorageReference storageReference;
	protected final boolean inStorage;
	private static AbstractBlockchain blockchain;
	private static BigInteger nextProgressive;

	/**
	 * Resets static data at the beginning of a transaction.
	 * 
	 * @param blockchain the blockchain used for the new transaction
	 */
	public static void init(AbstractBlockchain blockchain) {
		Storage.blockchain = blockchain;
		nextProgressive = BigInteger.ZERO;
	}

	/**
	 * Constructs an object that can be stored in blockchain.
	 */
	@WhiteListed
	protected Storage() {
		this.inStorage = false;
		this.storageReference = new StorageReference(blockchain.getCurrentTransactionReference(), nextProgressive);
		nextProgressive = nextProgressive.add(BigInteger.ONE);
	}

	@WhiteListed
	protected final void event(String tag, Object... objects) {
		blockchain.event(tag + ": " + Arrays.toString(objects));
	}

	// ALL SUBSEQUENT METHODS ARE USED IN INSTRUMENTED CODE

	/**
	 * Collects the updates to this object and to
	 * the objects that are reachable from it. This is used at the end of a
	 * transaction, to collect and then store the updates resulting from
	 * the transaction.
	 * 
	 * @param result the set where the updates will be added
	 * @param seen a set of storage references that have already been scanned
	 */
	public final void updates(Set<Update> result, Set<StorageReference> seen) {
		if (seen.add(storageReference)) {
			List<Storage> workingSet = new ArrayList<>(16);
			workingSet.add(this);

			do {
				workingSet.remove(workingSet.size() - 1).extractUpdates(result, seen, workingSet);
			}
			while (!workingSet.isEmpty());
		}
	}

	/**
	 * Constructor used for deserialization from blockchain, in instrumented code.
	 * 
	 * @param storageReference the reference to deserialize
	 */
	protected Storage(StorageReference storageReference) {
		this.inStorage = true;
		this.storageReference = storageReference;
	}

	/**
	 * Collects the updates to this object and to those reachable from it.
	 * The instrumentation of storage classes redefines this to include updates to all their fields.
	 * 
	 * @param updates the set where storage updates will be collected
	 * @param seen the storage references of the objects already considered during the scan of the storage
	 * @param workingSet the list of storage objects that still need to be processed. This can get enlarged by a call to this method,
	 *                   in order to simulate recursive calls without risking a Java stack overflow
	 */
	protected void extractUpdates(Set<Update> updates, Set<StorageReference> seen, List<Storage> workingSet) {
		if (!inStorage)
			updates.add(Update.mkForClassTag(storageReference, getClass().getName()));

		// subclasses will override, call this super-implementation and add potential updates to their instance fields
	}

	/**
	 * Utility method that will be used in subclasses to implement
	 * method extractUpdates to recur on the old value of fields of reference type.
	 */
	protected final void recursiveExtract(Object s, Set<Update> updates, Set<StorageReference> seen, List<Storage> workingSet) {
		if (s instanceof Storage) {
			if (seen.add(((Storage) s).storageReference))
				workingSet.add((Storage) s);
		}
		else if (s instanceof String || s instanceof BigInteger) {} // these types are not recursively followed
		else if (s != null)
			throw new RuntimeException("a field of a storage object cannot hold a " + s.getClass().getName());
	}

	protected final Object deserializeLastUpdateFor(String definingClass, String name, String className) throws Exception {
		return blockchain.deserializeLastLazyUpdateFor(storageReference, new FieldSignature(definingClass, name, className));
	}

	protected final void addUpdateFor(String fieldDefiningClass, String fieldName, Set<Update> updates, Set<StorageReference> seen, List<Storage> workingSet, String fieldClassName, Object s) {
		// these values are not recursively followed
		FieldSignature field = new FieldSignature(fieldDefiningClass, fieldName, fieldClassName);

		if (s == null)
			updates.add(new Update(storageReference, field, NullValue.INSTANCE));
		else if (s instanceof Storage) {
			Storage storage = (Storage) s;

			if (seen.add(storage.storageReference)) {
				// general case, recursively followed
				updates.add(new Update(storageReference, field, storage.storageReference));
				workingSet.add(storage);
			}
			else
				updates.add(new Update(storageReference, field, storageReference));
		}
		else
			throw new RuntimeException("field " + field + " of a storage class cannot hold a " + s.getClass().getName());
	}

	protected final void addUpdateFor(String fieldDefiningClass, String fieldName, Set<Update> updates, boolean s) {
		updates.add(new Update(storageReference, new FieldSignature(fieldDefiningClass, fieldName, BasicTypes.BOOLEAN), new BooleanValue(s)));
	}

	protected final void addUpdateFor(String fieldDefiningClass, String fieldName, Set<Update> updates, byte s) {
		updates.add(new Update(storageReference, new FieldSignature(fieldDefiningClass, fieldName, BasicTypes.BYTE), new ByteValue(s)));
	}

	protected final void addUpdateFor(String fieldDefiningClass, String fieldName, Set<Update> updates, char s) {
		updates.add(new Update(storageReference, new FieldSignature(fieldDefiningClass, fieldName, BasicTypes.CHAR), new CharValue(s)));
	}

	protected final void addUpdateFor(String fieldDefiningClass, String fieldName, Set<Update> updates, double s) {
		updates.add(new Update(storageReference, new FieldSignature(fieldDefiningClass, fieldName, BasicTypes.DOUBLE), new DoubleValue(s)));
	}

	protected final void addUpdateFor(String fieldDefiningClass, String fieldName, Set<Update> updates, float s) {
		updates.add(new Update(storageReference, new FieldSignature(fieldDefiningClass, fieldName, BasicTypes.FLOAT), new FloatValue(s)));
	}

	protected final void addUpdateFor(String fieldDefiningClass, String fieldName, Set<Update> updates, int s) {
		updates.add(new Update(storageReference, new FieldSignature(fieldDefiningClass, fieldName, BasicTypes.INT), new IntValue(s)));
	}

	protected final void addUpdateFor(String fieldDefiningClass, String fieldName, Set<Update> updates, long s) {
		updates.add(new Update(storageReference, new FieldSignature(fieldDefiningClass, fieldName, BasicTypes.LONG), new LongValue(s)));
	}

	protected final void addUpdateFor(String fieldDefiningClass, String fieldName, Set<Update> updates, short s) {
		updates.add(new Update(storageReference, new FieldSignature(fieldDefiningClass, fieldName, BasicTypes.SHORT), new ShortValue(s)));
	}

	protected final void addUpdateFor(String fieldDefiningClass, String fieldName, Set<Update> updates, String s) {
		updates.add(new Update(storageReference, new FieldSignature(fieldDefiningClass, fieldName, ClassType.STRING), s == null ? NullValue.INSTANCE : new StringValue(s)));
	}

	protected final void addUpdateFor(String fieldDefiningClass, String fieldName, Set<Update> updates, BigInteger bi) {
		updates.add(new Update(storageReference, new FieldSignature(fieldDefiningClass, fieldName, ClassType.BIG_INTEGER), bi == null ? NullValue.INSTANCE : new BigIntegerValue(bi)));
	}
}