package io.hotmoka.network.models.responses;

import io.hotmoka.beans.annotations.Immutable;
import io.hotmoka.beans.responses.ConstructorCallTransactionSuccessfulResponse;
import io.hotmoka.network.models.updates.UpdateModel;
import io.hotmoka.network.models.values.StorageReferenceModel;

import java.util.List;
import java.util.stream.Collectors;

@Immutable
public class ConstructorCallTransactionSuccessfulResponseModel extends ConstructorCallTransactionResponseModel {

	/**
     * The events generated by this transaction.
     */
    public final List<StorageReferenceModel> events;

    /**
     * The object that has been created by the constructor call.
     */
    public final StorageReferenceModel newObject;

    public ConstructorCallTransactionSuccessfulResponseModel(ConstructorCallTransactionSuccessfulResponse response) {
        super(response);

        this.events = response.getEvents().map(StorageReferenceModel::new).collect(Collectors.toList());
        this.newObject = new StorageReferenceModel(response.newObject);
    }

    public ConstructorCallTransactionSuccessfulResponse toBean() {
        return new ConstructorCallTransactionSuccessfulResponse(
        	newObject.toBean(),
            updates.stream().map(UpdateModel::toBean),
            events.stream().map(StorageReferenceModel::toBean).collect(Collectors.toList()).stream(),
            gasConsumedForCPU,
            gasConsumedForRAM,
            gasConsumedForStorage
        );
    }
}