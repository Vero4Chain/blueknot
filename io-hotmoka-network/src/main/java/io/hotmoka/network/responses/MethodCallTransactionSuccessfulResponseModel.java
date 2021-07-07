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

import io.hotmoka.beans.responses.MethodCallTransactionSuccessfulResponse;
import io.hotmoka.network.updates.UpdateModel;
import io.hotmoka.network.values.StorageReferenceModel;
import io.hotmoka.network.values.StorageValueModel;

public class MethodCallTransactionSuccessfulResponseModel extends MethodCallTransactionResponseModel {

    /**
     * The return value of the method.
     */
    public StorageValueModel result;

    /**
     * The events generated by this transaction.
     */
    private List<StorageReferenceModel> events;

    public MethodCallTransactionSuccessfulResponseModel(MethodCallTransactionSuccessfulResponse response) {
        super(response);

        this.result = new StorageValueModel(response.result);
        this.events = response.getEvents().map(StorageReferenceModel::new).collect(Collectors.toList());
    }

    public MethodCallTransactionSuccessfulResponseModel() {}

    public MethodCallTransactionSuccessfulResponse toBean() {
        return new MethodCallTransactionSuccessfulResponse(
        	result.toBean(),
        	selfCharged,
        	updates.stream().map(UpdateModel::toBean),
        	events.stream().map(StorageReferenceModel::toBean),
        	new BigInteger(gasConsumedForCPU),
        	new BigInteger(gasConsumedForRAM),
        	new BigInteger(gasConsumedForStorage)
        );
    }
}