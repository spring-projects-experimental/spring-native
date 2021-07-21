package com.example.integration;

import org.springframework.integration.annotation.Gateway;
import org.springframework.integration.annotation.MessagingGateway;

@MessagingGateway(defaultRequestChannel = "controlBus.input")
public interface ControlBusGateway {

	@Gateway(payloadExpression = "'@' + args[0] + '.start()'")
	void startEndpoint(String id);

}
