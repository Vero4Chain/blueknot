package io.hotmoka.network.internal.services;

import io.hotmoka.beans.updates.ClassTag;
import io.hotmoka.beans.updates.UpdateOfField;
import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.network.internal.models.State;
import io.hotmoka.network.internal.models.updates.ClassUpdateModel;
import io.hotmoka.network.internal.models.updates.FieldUpdateModel;
import io.hotmoka.network.internal.models.updates.UpdateModel;
import io.hotmoka.nodes.Node;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class NodeGetServiceImpl extends NetworkService implements NodeGetService {

    @Override
    public ResponseEntity<Object> getTakamakaCode() {
        return okResponseOf(getNode().getTakamakaCode());
    }

    @Override
    public ResponseEntity<Object> getManifest() {
        return okResponseOf(getNode().getManifest());
    }

    @Override
    public ResponseEntity<Object> getState() {
    	Node node = getNode();
    	StorageReference manifest = node.getManifest(); // TODO
    	List<UpdateModel> updatesJson = node.getState(manifest)
    			.map(NodeGetServiceImpl::buildUpdateModel)
    			.filter(Objects::nonNull)
    			.collect(Collectors.toList());

    	State stateJson = new State();
    	stateJson.setTransaction(manifest.transaction.getHash());
    	stateJson.setProgressive(manifest.progressive);
    	stateJson.setUpdates(updatesJson);

    	return okResponseOf(stateJson);
    }

    @Override
    public ResponseEntity<Object> getClassTag() {
        return okResponseOf(getNode().getClassTag(getNode().getManifest())); // TODO
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

        if (updateItem instanceof ClassTag) {
            updateJson = new ClassUpdateModel();
            ((ClassUpdateModel) updateJson).setClassName(((ClassTag) updateItem).className);
            ((ClassUpdateModel) updateJson).setJar(((ClassTag) updateItem).jar.getHash());
        }

        return updateJson;
    }
}