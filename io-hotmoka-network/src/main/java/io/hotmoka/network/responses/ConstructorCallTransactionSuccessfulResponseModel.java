/*
Copyright 2021 Dinu Berinde and Fausto Spoto

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package io.hotmoka.network.responses;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

import io.hotmoka.beans.responses.ConstructorCallTransactionSuccessfulResponse;
import io.hotmoka.network.updates.UpdateModel;
import io.hotmoka.network.values.StorageReferenceModel;

public class ConstructorCallTransactionSuccessfulResponseModel extends ConstructorCallTransactionResponseModel {

	/**
     * The events generated by this transaction.
     */
    public List<StorageReferenceModel> events;

    /**
     * The object that has been created by the constructor call.
     */
    public StorageReferenceModel newObject;

    public ConstructorCallTransactionSuccessfulResponseModel(ConstructorCallTransactionSuccessfulResponse response) {
        super(response);

        this.events = response.getEvents().map(StorageReferenceModel::new).collect(Collectors.toList());
        this.newObject = new StorageReferenceModel(response.newObject);
    }

    public ConstructorCallTransactionSuccessfulResponseModel() {}

    public ConstructorCallTransactionSuccessfulResponse toBean() {
        return new ConstructorCallTransactionSuccessfulResponse(
        	newObject.toBean(),
            updates.stream().map(UpdateModel::toBean),
            events.stream().map(StorageReferenceModel::toBean).collect(Collectors.toList()).stream(),
            new BigInteger(gasConsumedForCPU),
            new BigInteger(gasConsumedForRAM),
            new BigInteger(gasConsumedForStorage)
        );
    }
}
