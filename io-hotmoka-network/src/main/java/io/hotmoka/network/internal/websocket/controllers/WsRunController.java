package io.hotmoka.network.internal.websocket.controllers;

import io.hotmoka.network.internal.services.NetworkExceptionResponse;
import io.hotmoka.network.internal.services.RunService;
import io.hotmoka.network.models.errors.ErrorModel;
import io.hotmoka.network.models.requests.InstanceMethodCallTransactionRequestModel;
import io.hotmoka.network.models.requests.StaticMethodCallTransactionRequestModel;
import io.hotmoka.network.models.values.StorageValueModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@MessageMapping("/run")
public class WsRunController {

    @Autowired
    private SimpMessagingTemplate simpMessagingTemplate;

    @Autowired
    private RunService nodeRunService;

    @MessageMapping("/instanceMethodCallTransaction")
    @SendTo("/topic/run/instanceMethodCallTransaction")
    public StorageValueModel instanceMethodCallTransaction(InstanceMethodCallTransactionRequestModel request) {
        return nodeRunService.runInstanceMethodCallTransaction(request);
    }

    @MessageMapping("/staticMethodCallTransaction")
    @SendTo("/topic/run/staticMethodCallTransaction")
    public StorageValueModel staticMethodCallTransaction(StaticMethodCallTransactionRequestModel request) {
        return nodeRunService.runStaticMethodCallTransaction(request);
    }

    @MessageExceptionHandler
    public void handleException(Exception e, Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        String destinationTopic = (String) headerAccessor.getHeader("simpDestination");
        if (e instanceof NetworkExceptionResponse)
            simpMessagingTemplate.convertAndSendToUser(principal.getName(), destinationTopic + "/error", ((NetworkExceptionResponse) e).errorModel);
        else
            simpMessagingTemplate.convertAndSendToUser(principal.getName(), destinationTopic + "/error", new ErrorModel(e));
    }
}
