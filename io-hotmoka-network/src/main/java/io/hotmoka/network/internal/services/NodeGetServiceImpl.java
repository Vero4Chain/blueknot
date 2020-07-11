package io.hotmoka.network.internal.services;

import io.hotmoka.beans.updates.UpdateOfField;
import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.network.internal.models.ClassTagModel;
import io.hotmoka.network.internal.models.StateModel;
import io.hotmoka.network.internal.models.function.ClassTagMapper;
import io.hotmoka.network.internal.models.function.TransactionReferenceMapper;
import io.hotmoka.network.internal.models.function.StorageReferenceMapper;
import io.hotmoka.network.internal.models.storage.StorageReferenceModel;
import io.hotmoka.network.internal.models.transactions.TransactionReferenceModel;
import io.hotmoka.network.internal.models.updates.ClassUpdateModel;
import io.hotmoka.network.internal.models.updates.FieldUpdateModel;
import io.hotmoka.network.internal.models.updates.UpdateModel;
import io.hotmoka.network.internal.util.StorageResolver;
import io.hotmoka.nodes.Node;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class NodeGetServiceImpl extends NetworkService implements NodeGetService {

    @Override
    public TransactionReferenceModel getTakamakaCode() {
        return wrapExceptions(() -> responseOf(getNode().getTakamakaCode(), new TransactionReferenceMapper()));
    }

    @Override
    public StorageReferenceModel getManifest() {
        return wrapExceptions(() -> responseOf(getNode().getManifest(), new StorageReferenceMapper()));
    }

    @Override
    public StateModel getState(StorageReferenceModel request) {
        return wrapExceptions(() -> {

            Node node = getNode();
            StorageReference storageReference = StorageResolver.resolveStorageReference(request);
            List<UpdateModel> updatesJson = node.getState(storageReference)
                    .map(NodeGetServiceImpl::buildUpdateModel)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            StateModel stateJson = new StateModel();
            stateJson.setTransaction(storageReference.transaction.getHash());
            stateJson.setProgressive(storageReference.progressive);
            stateJson.setUpdates(updatesJson);

            return stateJson;
        });
    }

    @Override
    public ClassTagModel getClassTag(StorageReferenceModel request) {
        return wrapExceptions(() -> responseOf(getNode().getClassTag(StorageResolver.resolveStorageReference(request)), new ClassTagMapper()));
    }

    /**
     * Build a json update model from an update item {@link io.hotmoka.beans.updates.Update} of a {@link io.hotmoka.nodes.Node} instance
     * @param updateItem the update from which to build a json model
     * @return a json model of an update instance {@link io.hotmoka.beans.updates.Update}  of a {@link io.hotmoka.nodes.Node}
     */
    private static UpdateModel buildUpdateModel(io.hotmoka.beans.updates.Update updateItem) {
        UpdateModel updateJson = null;

        if (updateItem instanceof UpdateOfField) {
            updateJson = new FieldUpdateModel();
            ((FieldUpdateModel) updateJson).setUpdateType(updateItem.getClass().getName());
            ((FieldUpdateModel) updateJson).setValue(((UpdateOfField) updateItem).getValue().toString());
            ((FieldUpdateModel) updateJson).setDefiningClass(((UpdateOfField) updateItem).getField().definingClass.name);
            ((FieldUpdateModel) updateJson).setType(((UpdateOfField) updateItem).getField().type.toString());
            ((FieldUpdateModel) updateJson).setName(((UpdateOfField) updateItem).getField().name);
        }

        if (updateItem instanceof io.hotmoka.beans.updates.ClassTag) {
            updateJson = new ClassUpdateModel();
            ((ClassUpdateModel) updateJson).setClassName(((io.hotmoka.beans.updates.ClassTag) updateItem).className);
            ((ClassUpdateModel) updateJson).setJar(((io.hotmoka.beans.updates.ClassTag) updateItem).jar.getHash());
        }

        return updateJson;
    }
}