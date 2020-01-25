package io.takamaka.code.engine;

import java.util.stream.Stream;

import io.hotmoka.beans.references.TransactionReference;
import io.hotmoka.beans.requests.TransactionRequest;
import io.hotmoka.beans.responses.TransactionResponse;
import io.hotmoka.beans.signatures.FieldSignature;
import io.hotmoka.beans.updates.Update;
import io.hotmoka.beans.updates.UpdateOfField;
import io.hotmoka.beans.values.StorageReference;

public interface Node {

	/**
	 * Yields a transaction reference whose {@code toString()} is the given string.
	 * 
	 * @param toString the result of {@code toString()} on the desired transaction reference
	 * @return the transaction reference
	 */
	TransactionReference getTransactionReferenceFor(String toString);

	/**
	 * Yields the request that generated the given transaction.
	 * 
	 * @param transaction the reference to the transaction
	 * @return the request
	 * @throws Exception if the request could not be found
	 */
	TransactionRequest<?> getRequestAt(TransactionReference transaction) throws Exception;

	/**
	 * Yields the response that was generated by the given transaction.
	 * 
	 * @param transaction the reference to the transaction
	 * @return the response
	 * @throws Exception if the response could not be found
	 */
	TransactionResponse getResponseAt(TransactionReference transaction) throws Exception;

	/**
	 * Yields the most recent eager updates for the given storage reference.
	 * 
	 * @param reference the storage reference
	 * @return the updates. These must include the class tag update for the reference
	 * @throws Exception if the updates cannot be found
	 */
	Stream<Update> getLastEagerUpdatesFor(StorageReference reference) throws Exception;

	/**
	 * Yields the most recent update for the given non-{@code final} field,
	 * of lazy type, of the object at given storage reference.
	 * Conceptually, this amounts to scanning backwards the blockchain, from its tip,
	 * looking for the latest update.
	 * 
	 * @param object the storage reference
	 * @param field the field whose update is being looked for
	 * @return the update, if any
	 * @throws Exception if the update could not be found
	 */
	UpdateOfField getLastLazyUpdateToNonFinalFieldOf(StorageReference object, FieldSignature field) throws Exception;

	/**
	 * Yields the most recent update for the given {@code final} field,
	 * of lazy type, of the object at given storage reference.
	 * Conceptually, this amounts to accessing the storage reference when the object was
	 * created and reading the value of the field there. Its implementation can be identical to
	 * that of {@link #getLastLazyUpdateToNonFinalFieldOf(StorageReference, FieldSignature)},
	 * or exploit the fact that the field is {@code final}, for an optimized look-up.
	 * 
	 * @param object the storage reference
	 * @param field the field whose update is being looked for
	 * @return the update, if any
	 * @throws Exception if the update could not be found
	 */
	UpdateOfField getLastLazyUpdateToFinalFieldOf(StorageReference object, FieldSignature field) throws Exception;

	/**
	 * Yields the UTC time when the currently executing transaction is being run.
	 * This might be for instance the time of creation of the block where the transaction
	 * occurs, but the detail is left to the implementation. In any case, this
	 * time must be the same for a given transaction, if it gets executed more times.
	 * 
	 * @return the UTC time, as returned by {@link java.lang.System#currentTimeMillis()}
	 */
	long getNow();

	/**
	 * Yields the gas cost model of this blockchain.
	 * 
	 * @return the standard gas cost model. Subclasses may redefine
	 */
	GasCostModel getGasCostModel();
}