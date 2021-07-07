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

package io.hotmoka.service.internal.services;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import io.hotmoka.network.requests.ConstructorCallTransactionRequestModel;
import io.hotmoka.network.requests.GameteCreationTransactionRequestModel;
import io.hotmoka.network.requests.InitializationTransactionRequestModel;
import io.hotmoka.network.requests.InstanceMethodCallTransactionRequestModel;
import io.hotmoka.network.requests.JarStoreInitialTransactionRequestModel;
import io.hotmoka.network.requests.JarStoreTransactionRequestModel;
import io.hotmoka.network.requests.StaticMethodCallTransactionRequestModel;
import io.hotmoka.network.values.StorageReferenceModel;
import io.hotmoka.network.values.StorageValueModel;
import io.hotmoka.network.values.TransactionReferenceModel;

@Service
public class AddServiceImpl extends AbstractService implements AddService {

	@Override
	public TransactionReferenceModel addJarStoreInitialTransaction(JarStoreInitialTransactionRequestModel request) {
		return wrapExceptions(() -> new TransactionReferenceModel(getNode().addJarStoreInitialTransaction(request.toBean())));
	}

    @Override
    public StorageReferenceModel addGameteCreationTransaction(GameteCreationTransactionRequestModel request) {
        return wrapExceptions(() -> new StorageReferenceModel(getNode().addGameteCreationTransaction(request.toBean())));
    }

    @Override
    public ResponseEntity<Void> addInitializationTransaction(InitializationTransactionRequestModel request) {
        return wrapExceptions(() -> {
            getNode().addInitializationTransaction(request.toBean());
            return ResponseEntity.noContent().build();
        });
    }

    @Override
    public TransactionReferenceModel addJarStoreTransaction(JarStoreTransactionRequestModel request) {
        return wrapExceptions(() -> new TransactionReferenceModel(getNode().addJarStoreTransaction(request.toBean())));
    }

    @Override
    public StorageReferenceModel addConstructorCallTransaction(ConstructorCallTransactionRequestModel request) {
        return wrapExceptions(() -> new StorageReferenceModel(getNode().addConstructorCallTransaction(request.toBean())));
    }

    @Override
    public StorageValueModel addInstanceMethodCallTransaction(InstanceMethodCallTransactionRequestModel request) {
        return wrapExceptions(() -> StorageValueModel.modelOfValueReturned(request, getNode().addInstanceMethodCallTransaction(request.toBean())));
    }

    @Override
    public StorageValueModel addStaticMethodCallTransaction(StaticMethodCallTransactionRequestModel request) {
        return wrapExceptions(() -> StorageValueModel.modelOfValueReturned(request, getNode().addStaticMethodCallTransaction(request.toBean())));
    }
}