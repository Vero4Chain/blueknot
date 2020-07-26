package io.hotmoka.network.internal.models.requests;

import io.hotmoka.beans.requests.InstanceMethodCallTransactionRequest;
import io.hotmoka.beans.signatures.MethodSignature;
import io.hotmoka.beans.values.StorageValue;
import io.hotmoka.network.internal.models.storage.StorageReferenceModel;
import io.hotmoka.network.internal.models.storage.StorageValueModel;
import io.hotmoka.network.internal.util.StorageResolver;

public class InstanceMethodCallTransactionRequestModel extends MethodCallTransactionRequestModel {
	private StorageReferenceModel receiver;

    public void setReceiver(StorageReferenceModel receiver) {
        this.receiver = receiver;
    }

    public InstanceMethodCallTransactionRequest toBean() {
    	MethodSignature methodSignature = StorageResolver.resolveMethodSignature(this);

    	return new InstanceMethodCallTransactionRequest(
        	decodeBase64(getSignature()),
            getCaller().toBean(),
            getNonce(),
            getChainId(),
            getGasLimit(),
            getGasPrice(),
            getClasspath().toBean(),
            methodSignature,
            receiver.toBean(),
            getActuals().stream().map(StorageValueModel::toBean).toArray(StorageValue[]::new));
    }
}