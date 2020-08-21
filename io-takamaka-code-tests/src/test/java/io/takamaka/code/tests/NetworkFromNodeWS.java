/**
 *
 */
package io.takamaka.code.tests;

import io.hotmoka.beans.InternalFailureException;
import io.hotmoka.beans.TransactionRejectedException;
import io.hotmoka.beans.references.TransactionReference;
import io.hotmoka.beans.requests.ConstructorCallTransactionRequest;
import io.hotmoka.beans.requests.JarStoreInitialTransactionRequest;
import io.hotmoka.beans.requests.NonInitialTransactionRequest;
import io.hotmoka.beans.signatures.ConstructorSignature;
import io.hotmoka.beans.values.IntValue;
import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.network.NodeService;
import io.hotmoka.network.NodeServiceConfig;
import io.hotmoka.network.internal.websocket.WebsocketClient;
import io.hotmoka.network.models.errors.ErrorModel;
import io.hotmoka.network.models.requests.ConstructorCallTransactionRequestModel;
import io.hotmoka.network.models.requests.JarStoreInitialTransactionRequestModel;
import io.hotmoka.network.models.responses.SignatureAlgorithmResponseModel;
import io.hotmoka.network.models.updates.ClassTagModel;
import io.hotmoka.network.models.updates.StateModel;
import io.hotmoka.network.models.values.StorageReferenceModel;
import io.hotmoka.network.models.values.TransactionReferenceModel;
import io.takamaka.code.verification.issues.Error;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.PrivateKey;

import static io.hotmoka.beans.types.BasicTypes.INT;
import static java.math.BigInteger.ONE;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A test for creating a network server from a Hotmoka node using websockets
 */
class NetworkFromNodeWS extends TakamakaTest {
    private static final BigInteger ALL_FUNDS = BigInteger.valueOf(1_000_000_000);
    private static final BigInteger _20_000 = BigInteger.valueOf(20_000);
    private static final ConstructorSignature CONSTRUCTOR_INTERNATIONAL_TIME = new ConstructorSignature("io.takamaka.tests.basicdependency.InternationalTime", INT, INT, INT);

    private final NodeServiceConfig configNoBanner = new NodeServiceConfig.Builder().setPort(8081).setSpringBannerModeOn(false).build();

    /**
     * The account that holds all funds.
     */
    private StorageReference master;

    /**
     * The classpath of the classes being tested.
     */
    private TransactionReference classpath;

    /**
     * The private key of {@linkplain #master}.
     */
    private PrivateKey key;

    @BeforeEach
    void beforeEach() throws Exception {
        setNode("basicdependency.jar", ALL_FUNDS, BigInteger.ZERO);
        master = account(0);
        key = privateKey(0);
        classpath = addJarStoreTransaction(key, master, BigInteger.valueOf(10000), BigInteger.ONE, takamakaCode(), bytesOf("basic.jar"), jar());
    }

    @Test @DisplayName("starts a network server from a Hotmoka node")
    void startNetworkFromNode() {
        NodeServiceConfig config = new NodeServiceConfig.Builder().setPort(8081).setSpringBannerModeOn(true).build();
        try (NodeService nodeRestService = NodeService.of(config, nodeWithJarsView)) {
        }
    }


    @Test @DisplayName("starts a network server from a Hotmoka node and checks its signature algorithm")
    void startNetworkFromNodeAndTestSignatureAlgorithm() throws Exception {

        try (NodeService nodeRestService = NodeService.of(configNoBanner, nodeWithJarsView);
             WebsocketClient wsClient = new WebsocketClient("ws://localhost:8081/node")) {

            WebsocketClient.SubscriptionTask subscription = wsClient.subscribe("/topic/get/signatureAlgorithmForRequests", SignatureAlgorithmResponseModel.class);
            wsClient.send("/get/signatureAlgorithmForRequests");
            assertEquals("ed25519", ((SignatureAlgorithmResponseModel) subscription.get()).algorithm);
        }

    }

    @Test @DisplayName("starts a network server from a Hotmoka node and runs getTakamakaCode()")
    void testGetTakamakaCode() throws Exception {

        try (NodeService nodeRestService = NodeService.of(configNoBanner, nodeWithJarsView);
             WebsocketClient wsClient = new WebsocketClient("ws://localhost:8081/node")) {

            WebsocketClient.SubscriptionTask subscription = wsClient.subscribe("/topic/get/takamakaCode", TransactionReferenceModel.class);
            wsClient.send("/get/takamakaCode");
            assertEquals(nodeWithJarsView.getTakamakaCode().getHash(), ((TransactionReferenceModel) subscription.get()).hash);
        }

    }


    @Test @DisplayName("starts a network server from a Hotmoka node and runs addJarStoreInitialTransaction()")
    void addJarStoreInitialTransaction() throws Exception {

        try (NodeService nodeRestService = NodeService.of(configNoBanner, nodeWithJarsView);
             WebsocketClient wsClient = new WebsocketClient("ws://localhost:8081/node")) {
            JarStoreInitialTransactionRequest request = new JarStoreInitialTransactionRequest(Files.readAllBytes(Paths.get("jars/c13.jar")), nodeWithJarsView.getTakamakaCode());

            WebsocketClient.SubscriptionTask errorSubscription = wsClient.subscribe("/user/" + wsClient.getClientKey() + "/add/jarStoreInitialTransaction/error", ErrorModel.class);
            wsClient.send("/add/jarStoreInitialTransaction", new JarStoreInitialTransactionRequestModel(request));

            Object response = errorSubscription.get();
            assertTrue(response instanceof ErrorModel);

            ErrorModel errorModel = (ErrorModel) response;
            assertNotNull(errorModel);
            assertEquals("cannot run a JarStoreInitialTransactionRequest in an already initialized node", errorModel.message);
            assertEquals(TransactionRejectedException.class.getName(), errorModel.exceptionClassName);
        }
    }

    @Test @DisplayName("starts a network server from a Hotmoka node and runs addJarStoreInitialTransaction() without a jar")
    void addJarStoreInitialTransactionWithoutJar() throws Exception {

        try (NodeService nodeRestService = NodeService.of(configNoBanner, nodeWithJarsView);
             WebsocketClient wsClient = new WebsocketClient("ws://localhost:8081/node")) {

            WebsocketClient.SubscriptionTask subscriptionTask = wsClient.subscribeWithErrorResponse("/topic/add/jarStoreInitialTransaction", ErrorModel.class);
            wsClient.send("/add/jarStoreInitialTransaction", new JarStoreInitialTransactionRequestModel());

            Object response = subscriptionTask.get();
            assertTrue(response instanceof  ErrorModel);

            ErrorModel errorModel = (ErrorModel) response;
            assertNotNull(response);
            assertEquals("unexpected null jar", errorModel.message);
            assertEquals(InternalFailureException.class.getName(), errorModel.exceptionClassName);
        }
    }

    @Test @DisplayName("starts a network server from a Hotmoka node and calls addConstructorCallTransaction - new Sub(1973)")
    void addConstructorCallTransaction() throws Exception {

        try (NodeService nodeRestService = NodeService.of(configNoBanner, nodeWithJarsView);
             WebsocketClient wsClient = new WebsocketClient("ws://localhost:8081/node")) {

            ConstructorCallTransactionRequest request = new ConstructorCallTransactionRequest(
                    NonInitialTransactionRequest.Signer.with(signature(), key),
                    master,
                    ONE,
                    chainId,
                    _20_000,
                    ONE,
                    classpath,
                    new ConstructorSignature("io.takamaka.tests.basic.Sub", INT),
                    new IntValue(1973)
            );

            WebsocketClient.SubscriptionTask subscription = wsClient.subscribe("/topic/add/constructorCallTransaction", StorageReferenceModel.class);
            wsClient.send("/add/constructorCallTransaction", new ConstructorCallTransactionRequestModel(request));

            assertNotNull(((StorageReferenceModel) subscription.get()).transaction);
        }
    }

    @Test @DisplayName("starts a network server from a Hotmoka node, creates an object and calls getState() on it")
    void testGetState() throws Exception {

        try (NodeService nodeRestService = NodeService.of(configNoBanner, nodeWithJarsView);
             WebsocketClient wsClient = new WebsocketClient("ws://localhost:8081/node")) {

            ConstructorCallTransactionRequest request = new ConstructorCallTransactionRequest(
                    NonInitialTransactionRequest.Signer.with(signature(), key),
                    master,
                    ONE,
                    chainId,
                    _20_000,
                    ONE,
                    classpath,
                    CONSTRUCTOR_INTERNATIONAL_TIME,
                    new IntValue(13), new IntValue(25), new IntValue(40)
            );

            WebsocketClient.SubscriptionTask constructorCallSubscription = wsClient.subscribeWithErrorResponse("/topic/add/constructorCallTransaction", StorageReferenceModel.class);
            WebsocketClient.SubscriptionTask getStateSubscription = wsClient.subscribeWithErrorResponse("/topic/get/state", StateModel.class);

            // we execute the creation of the object
            wsClient.send("/add/constructorCallTransaction", new ConstructorCallTransactionRequestModel(request));

            StorageReferenceModel storageReferenceModel = (StorageReferenceModel) constructorCallSubscription.get();
            assertNotNull(storageReferenceModel.transaction);

            // we query the state of the object
            wsClient.send("/get/state", storageReferenceModel);

            StateModel stateModel = (StateModel) getStateSubscription.get();
            assertSame(2, stateModel.updates.size());
        }
    }

    @Test @DisplayName("starts a network server from a Hotmoka node, creates an object and calls getState() on it")
    void testGetClassTag() throws Exception {

        try (NodeService nodeRestService = NodeService.of(configNoBanner, nodeWithJarsView);
             WebsocketClient wsClient = new WebsocketClient("ws://localhost:8081/node")) {

            ConstructorCallTransactionRequest request = new ConstructorCallTransactionRequest(
                    NonInitialTransactionRequest.Signer.with(signature(), key),
                    master,
                    ONE,
                    chainId,
                    _20_000,
                    ONE,
                    classpath,
                    CONSTRUCTOR_INTERNATIONAL_TIME,
                    new IntValue(13), new IntValue(25), new IntValue(40)
            );

            WebsocketClient.SubscriptionTask constructorCallSubscription = wsClient.subscribe("/topic/add/constructorCallTransaction", StorageReferenceModel.class);
            WebsocketClient.SubscriptionTask getClassTageSubscription = wsClient.subscribe("/topic/get/classTag", ClassTagModel.class);

            // we execute the creation of the object
            wsClient.send("/add/constructorCallTransaction", new ConstructorCallTransactionRequestModel(request));

            StorageReferenceModel storageReferenceModel = (StorageReferenceModel) constructorCallSubscription.get();
            assertNotNull(storageReferenceModel.transaction);

            // we query the class tag of the object
            wsClient.send("/get/classTag", storageReferenceModel);

            ClassTagModel classTagModel = (ClassTagModel) getClassTageSubscription.get();

            // the state that the class tag holds the name of the class that has been created
            assertEquals(CONSTRUCTOR_INTERNATIONAL_TIME.definingClass.name, classTagModel.className);
        }

    }
}