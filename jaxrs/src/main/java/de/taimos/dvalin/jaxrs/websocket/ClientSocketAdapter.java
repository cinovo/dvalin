package de.taimos.dvalin.jaxrs.websocket;

/*
 * #%L
 * Daemon with Spring and CXF
 * %%
 * Copyright (C) 2013 - 2015 Taimos GmbH
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.taimos.dvalin.jaxrs.MapperFactory;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketOpen;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Socket adapter for clients
 *
 * @author thoeger
 */
@WebSocket
public class ClientSocketAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientSocketAdapter.class);

    private final ObjectMapper mapper = MapperFactory.createDefault();
    private Session session;

    private final LinkedBlockingDeque<String> messageQueue = new LinkedBlockingDeque<>();
    private final CountDownLatch closeLatch = new CountDownLatch(1);

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        ClientSocketAdapter.LOGGER.info("WebSocket Close: {} - {}", statusCode, reason);
        this.closeLatch.countDown();
    }

    @OnWebSocketOpen
    public void onOpen(Session session) {
        this.session = session;
        ClientSocketAdapter.LOGGER.info("WebSocket Open: {}", session);
    }

    @OnWebSocketError
    public void onError(Throwable cause) {
        ClientSocketAdapter.LOGGER.warn("WebSocket Error", cause);
    }

    @OnWebSocketMessage
    public void onText(String message) {
        ClientSocketAdapter.LOGGER.info("Text Message [{}]", message);
        this.messageQueue.offer(message);
    }

    /**
     * send the given object to the server using JSON serialization
     *
     * @param o the object to send to the server
     */
    public final void sendObjectToSocket(Object o) {
        if (this.session != null) {
            String json;
            try {
                json = this.mapper.writeValueAsString(o);
            } catch (JsonProcessingException e) {
                ClientSocketAdapter.LOGGER.error("Failed to serialize object", e);
                return;
            }
            this.session.sendText(json, new Callback() {
                @Override
                public void fail(Throwable x) {
                    ClientSocketAdapter.LOGGER.error("Error sending message to socket", x);
                }

                @Override
                public void succeed() {
                    ClientSocketAdapter.LOGGER.info("Send data to socket");
                }
            });
        }
    }

    /**
     * reads the received string into the given class by parsing JSON
     *
     * @param <T>   the expected type
     * @param clazz the target class of type T
     * @return the parsed object or null if parsing was not possible or message is null
     */
    public final <T> T readMessage(Class<T> clazz) {
        String message = this.messageQueue.pollFirst();
        if ((message == null) || message.isEmpty()) {
            ClientSocketAdapter.LOGGER.info("Got empty session data");
            return null;
        }
        try {
            return this.mapper.readValue(message, clazz);
        } catch (IOException e1) {
            ClientSocketAdapter.LOGGER.info("Got invalid session data", e1);
            return null;
        }
    }
}
