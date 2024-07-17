package de.taimos.dvalin.interconnect.core;

/*
 * #%L
 * Dvalin interconnect core library
 * %%
 * Copyright (C) 2016 Taimos GmbH
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

import de.taimos.dvalin.interconnect.core.crypto.JmsMessageCryptoUtil;
import de.taimos.dvalin.interconnect.core.exceptions.InfrastructureException;
import de.taimos.dvalin.interconnect.core.exceptions.MessageCryptoException;
import de.taimos.dvalin.interconnect.core.exceptions.SerializationException;
import de.taimos.dvalin.interconnect.core.exceptions.TimeoutException;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.apache.activemq.pool.PooledConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.jms.Connection;
import jakarta.jms.DeliveryMode;
import jakarta.jms.Destination;
import jakarta.jms.ExceptionListener;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.TemporaryQueue;
import jakarta.jms.TextMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Connector to connect to JMS providers.
 */
public final class MessageConnector {


    /**
     * name of the system property that contains the interconnect update topic name
     */
    public static final String SYSPROP_UPDATE_TOPIC = "interconnect.jms.updatetopic";
    /**
     * name of the system property that contains the interconnect virtual topic prefix
     */
    public static final String SYSPROP_VIRTUAL_TOPIC_PREFIX = "interconnect.jms.virtualtopic.prefix";
    /**
     * the default request timeout
     */
    public static final long REQUEST_TIMEOUT = 10000;
    /**
     * the message priority to use when sending a message over the message queue
     */
    public static final int MSGPRIORITY = 5;

    private static final String SECURITY_CHECK_FAILED = "Message security check failed";
    private static final String INVALID_RESPONSE_MESSAGE_RECEIVED = "Invalid response message received";
    private static final String RECEIVE_FAILED = "Error while receiving messages";
    private static final String SEND_FAILED = "Error while sending messages";
    private static final String FAILED_TO_CREATE_MESSAGE = "Failed to create message";
    private static final String CONNECTION_START_FAIL = "Failed to connect to the Interconnect.";
    private static final String CONNECTION_STOP_FAIL = "Failed to close the connection to the Interconnect.";
    private static final String CONNECTION_NOT_READY = "Connection not yet initialized.";
    private static final String CAN_NOT_CREATE_SESSION = "Can not create session";
    private static final String CAN_NOT_CREATE_CONNECTION = "Can not create connection";
    private static final String CAN_NOT_CREATE_DESTINATION = "Can not create destination";
    private static final String CAN_NOT_CREATE_REPLY_TO_DESTINATION = "Can not create eply to destination";
    private static final String CAN_NOT_CREATE_CONSUMER = "Can not create consumer";
    private static final String CAN_NOT_CREATE_PRODUCER = "Can not create producer";

    private static volatile PooledConnectionFactory pooledConnectionFactory;

    private static final AtomicBoolean started = new AtomicBoolean(false);

    private static final Logger logger = LoggerFactory.getLogger(MessageConnector.class);


    private MessageConnector() {
        // Utility class with private constructor
    }

    /**
     * @throws InfrastructureException of connection error
     */
    public static void start() throws InfrastructureException {
        MessageConnector.start(System.getProperty(DvalinConnectionFactory.SYSPROP_IBROKERURL));
    }

    private static ExceptionListener createMqErrorListener() {
        return new ExceptionListener() {

            @Override
            public void onException(final JMSException e) {
                MessageConnector.logger.warn("ActiveMQ connection factory error", e);
            }
        };
    }

    /**
     * @param brokerUrl the URL of the Interconnect message broker
     * @throws InfrastructureException of connection error
     */
    public static void start(final String brokerUrl) throws InfrastructureException {
        if (MessageConnector.started.compareAndSet(false, true)) {
            DvalinConnectionFactory dvalinConnectionFactory = new DvalinConnectionFactory(brokerUrl);
            dvalinConnectionFactory.setExceptionListener(MessageConnector.createMqErrorListener());
            MessageConnector.pooledConnectionFactory = new ActiveMQPooledConnectionFactory().initDefault(
                dvalinConnectionFactory);
        }
    }

    /**
     * @param brokerUrl the URL of the Interconnect message broker
     * @param userName  the username
     * @param password  the password
     * @throws InfrastructureException of connection error
     */
    public static void start(final String brokerUrl, final String userName, final String password) throws InfrastructureException {
        if (MessageConnector.started.compareAndSet(false, true)) {
            DvalinConnectionFactory dvalinConnectionFactory = new DvalinConnectionFactory(brokerUrl, userName,
                password);
            dvalinConnectionFactory.setExceptionListener(MessageConnector.createMqErrorListener());
            MessageConnector.pooledConnectionFactory = new ActiveMQPooledConnectionFactory().initDefault(
                dvalinConnectionFactory);
        }
    }

    /**
     * @throws InfrastructureException on disconnect error
     */
    public static void stop() throws InfrastructureException {
        if (MessageConnector.started.compareAndSet(true, false)) {
            try {
                MessageConnector.pooledConnectionFactory.stop();
                MessageConnector.pooledConnectionFactory = null;
            } catch (final Exception e) {
                throw new InfrastructureException(MessageConnector.CONNECTION_STOP_FAIL, e);
            }
        }
    }


    private interface GetDestinationAction {

        Destination get(Session session) throws JMSException;

    }

    private static final class GetResolveDestinationAction implements GetDestinationAction {

        private final boolean isQueue;
        private final String destinationName;


        public GetResolveDestinationAction(boolean isQueue, String destinationName) {
            super();
            this.isQueue = isQueue;
            this.destinationName = destinationName;
        }

        @Override
        public Destination get(final Session session) throws JMSException {
            if (this.isQueue) {
                return session.createQueue(this.destinationName);
            }
            return session.createTopic(this.destinationName);
        }

    }

    private static final class GetSimpleDestinationAction implements GetDestinationAction {

        private final Destination destination;


        public GetSimpleDestinationAction(Destination destination) {
            super();
            this.destination = destination;
        }

        @Override
        public Destination get(final Session session) throws JMSException {
            return this.destination;
        }

    }


    private static void sendToDestination(final GetDestinationAction getDestinationAction, final String body, final Map<String, Object> headers, final boolean secure, final String replyToQueueName, final String correlationId) throws InfrastructureException, MessageCryptoException {
        MessageConnector.checkInit();

        Connection connection = null;
        try {
            connection = MessageConnector.pooledConnectionFactory.createConnection();
            Session session = null;
            try {
                session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                TextMessage txt;
                try {
                    txt = session.createTextMessage(body);
                    if (replyToQueueName != null) {
                        try {
                            final Destination replyTo = session.createQueue(replyToQueueName);
                            txt.setJMSReplyTo(replyTo);
                        } catch (final JMSException e) {
                            throw new InfrastructureException(MessageConnector.CAN_NOT_CREATE_REPLY_TO_DESTINATION, e);
                        }
                    }
                    if (correlationId != null) {
                        txt.setJMSCorrelationID(correlationId);
                    }
                    if (headers != null) {
                        final Set<Entry<String, Object>> entrySet = headers.entrySet();
                        for (final Entry<String, Object> entry : entrySet) {
                            txt.setObjectProperty(entry.getKey(), entry.getValue());
                        }
                    }
                    if (secure) {
                        MessageConnector.secureMessage(txt);
                    }
                } catch (final JMSException e) {
                    throw new SerializationException(MessageConnector.FAILED_TO_CREATE_MESSAGE, e);
                }
                final Destination destination;
                try {
                    destination = getDestinationAction.get(session);
                } catch (final JMSException e) {
                    throw new InfrastructureException(MessageConnector.CAN_NOT_CREATE_DESTINATION, e);
                }
                MessageProducer producer = null;
                try {
                    producer = session.createProducer(destination);
                    try {
                        producer.send(txt);
                    } catch (JMSException e) {
                        throw new InfrastructureException(MessageConnector.SEND_FAILED, e);
                    }
                } catch (final JMSException e) {
                    throw new InfrastructureException(MessageConnector.CAN_NOT_CREATE_PRODUCER, e);
                } finally {
                    try {
                        if (producer != null) {
                            producer.close();
                        }
                    } catch (final JMSException e) {
                        MessageConnector.logger.warn("Can not close producer", e);
                    }
                }
            } catch (final JMSException e) {
                throw new InfrastructureException(MessageConnector.CAN_NOT_CREATE_SESSION, e);
            } finally {
                try {
                    if (session != null) {
                        session.close();
                    }
                } catch (final JMSException e) {
                    MessageConnector.logger.warn("Can not close session", e);
                }
            }
        } catch (final JMSException e) {
            throw new InfrastructureException(MessageConnector.CAN_NOT_CREATE_CONNECTION, e);
        } finally {
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (final JMSException e) {
                MessageConnector.logger.warn("Can not close connection", e);
            }
        }
    }

    /**
     * @param isQueue          Is the destination a Queue? (if false := topic)
     * @param destinationName  the name of the destination
     * @param body             the request body as String
     * @param headers          the request headers
     * @param secure           enable secure transport
     * @param replyToQueueName the name of the queue to reply to or null
     * @param correlationId    the correlated id
     * @throws InfrastructureException on errors
     * @throws MessageCryptoException  on secure transport errors
     */
    private static void sendToDestination(final boolean isQueue, final String destinationName, final String body, final Map<String, Object> headers, final boolean secure, final String replyToQueueName, final String correlationId) throws InfrastructureException, MessageCryptoException {
        MessageConnector.sendToDestination(new GetResolveDestinationAction(isQueue, destinationName), body, headers,
            secure, replyToQueueName, correlationId);
    }

    /**
     * @param destination      the destination
     * @param body             the request body as String
     * @param headers          the request headers
     * @param secure           enable secure transport
     * @param replyToQueueName the name of the queue to reply to or null
     * @param correlationId    the correlated id
     * @throws InfrastructureException on errors
     * @throws MessageCryptoException  on secure transport errors
     */
    public static void sendToDestination(final Destination destination, final String body, final Map<String, Object> headers, final boolean secure, final String replyToQueueName, final String correlationId) throws InfrastructureException, MessageCryptoException {
        MessageConnector.sendToDestination(new GetSimpleDestinationAction(destination), body, headers, secure,
            replyToQueueName, correlationId);
    }

    /**
     * @param queueName        the name of the queue
     * @param body             the request body as String
     * @param headers          the request headers
     * @param secure           enable secure transport
     * @param replyToQueueName the name of the queue to reply to or null
     * @param correlationId    the correlated id
     * @throws InfrastructureException on errors
     * @throws MessageCryptoException  on secure transport errors
     */
    public static void sendToQueue(final String queueName, final String body, final Map<String, Object> headers, final boolean secure, final String replyToQueueName, final String correlationId) throws InfrastructureException, MessageCryptoException {
        MessageConnector.sendToDestination(true, queueName, body, headers, secure, replyToQueueName, correlationId);
    }

    /**
     * @param queueName the name of the queue
     * @param body      the request body as String
     * @param headers   the request headers
     * @param secure    enable secure transport
     * @throws InfrastructureException on errors
     * @throws MessageCryptoException  on secure transport errors
     */
    public static void sendToQueue(final String queueName, final String body, final Map<String, Object> headers, final boolean secure) throws InfrastructureException, MessageCryptoException {
        MessageConnector.sendToQueue(queueName, body, headers, secure, null, null);
    }

    /**
     * @param queueName        the name of the queue
     * @param body             the request body as String
     * @param headers          the request headers
     * @param replyToQueueName the name of the queue to reply to or null
     * @param correlationId    the correlated id
     * @throws InfrastructureException on errors
     */
    public static void sendToQueue(final String queueName, final String body, final Map<String, Object> headers, final String replyToQueueName, final String correlationId) throws InfrastructureException {
        try {
            MessageConnector.sendToQueue(queueName, body, headers, false, replyToQueueName, correlationId);
        } catch (final MessageCryptoException e) {
            // secure is false --> No MessageCryptoException
        }
    }

    /**
     * @param queueName the name of the queue
     * @param body      the request body as String
     * @param headers   the request headers
     * @throws InfrastructureException on errors
     */
    public static void sendToQueue(final String queueName, final String body, final Map<String, Object> headers) throws InfrastructureException {
        try {
            MessageConnector.sendToQueue(queueName, body, headers, false);
        } catch (final MessageCryptoException e) {
            // secure is false --> No MessageCryptoException
        }
    }

    /**
     * @param topicName the name of the topic
     * @param body      the request body as String
     * @param headers   the request headers
     * @param secure    enable secure transport
     * @throws InfrastructureException on errors
     * @throws MessageCryptoException  on secure transport errors
     */
    public static void sendToTopic(final String topicName, final String body, final Map<String, Object> headers, final boolean secure) throws InfrastructureException, MessageCryptoException {
        MessageConnector.sendToDestination(false, topicName, body, headers, secure, null, null);
    }

    /**
     * @param topicName the name of the topic
     * @param body      the request body as String
     * @param headers   the request headers
     * @throws InfrastructureException on errors
     */
    public static void sendToTopic(final String topicName, final String body, final Map<String, Object> headers) throws InfrastructureException {
        try {
            MessageConnector.sendToTopic(topicName, body, headers, false);
        } catch (final MessageCryptoException e) {
            // secure is false --> No MessageCryptoException
        }
    }

    /**
     * @param queueName the name of the request queue
     * @param selector  the JMS selector (null or an empty string indicates that there is no selector)
     * @param timeout   the request timeout
     * @param secure    enable secure transport
     * @return the received {@link TextMessage}
     * @throws InfrastructureException on errors
     * @throws MessageCryptoException  on crypto errors
     * @deprecated use receiveFromQueue instead
     */
    @Deprecated
    public static TextMessage receive(final String queueName, final String selector, final long timeout, final boolean secure) throws InfrastructureException, MessageCryptoException {
        return MessageConnector.receiveFromQueue(queueName, selector, timeout, secure);
    }

    private static List<TextMessage> receiveBulkFromDestination(final GetDestinationAction getDestinationAction, final String selector, final int maxSize, final long timeout, final boolean secure) throws InfrastructureException, MessageCryptoException {
        MessageConnector.checkInit();
        final List<TextMessage> messages = new ArrayList<>(maxSize);

        Connection connection = null;
        try {
            connection = MessageConnector.pooledConnectionFactory.createConnection();
            Session session = null;
            try {
                session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                final Destination responseQueue;
                try {
                    responseQueue = getDestinationAction.get(session);
                } catch (final JMSException e) {
                    throw new InfrastructureException(MessageConnector.CAN_NOT_CREATE_DESTINATION, e);
                }
                MessageConsumer consumer = null;
                try {
                    consumer = session.createConsumer(responseQueue, selector);
                    try {
                        while (messages.size() < maxSize) {
                            connection.start();
                            final Message response;
                            try {
                                response = consumer.receive(timeout); // Wait for response.
                            } catch (final JMSException e) {
                                throw new InfrastructureException(MessageConnector.RECEIVE_FAILED, e);
                            }
                            if (response == null) {
                                if (messages.isEmpty()) {
                                    // first read timed out, so we throw a TimeoutException
                                    throw new TimeoutException(timeout);
                                }
                                // consecutive read timed out, so we just return the result
                                break;
                            }
                            if (response instanceof TextMessage) {
                                final TextMessage txtRes = (TextMessage) response;
                                if (secure) {
                                    MessageConnector.decryptMessage(txtRes);
                                }
                                messages.add(txtRes);
                            } else {
                                throw new InfrastructureException(MessageConnector.INVALID_RESPONSE_MESSAGE_RECEIVED);
                            }
                        }
                        return messages;
                    } catch (final JMSException e) {
                        throw new InfrastructureException(MessageConnector.RECEIVE_FAILED, e);
                    } finally {
                        connection.stop();
                    }
                } catch (final JMSException e) {
                    throw new InfrastructureException(MessageConnector.CAN_NOT_CREATE_CONSUMER, e);
                } finally {
                    try {
                        if (consumer != null) {
                            consumer.close();
                        }
                    } catch (final JMSException e) {
                        MessageConnector.logger.warn("Can not close consumer", e);
                    }
                }
            } catch (final JMSException e) {
                throw new InfrastructureException(MessageConnector.CAN_NOT_CREATE_SESSION, e);
            } finally {
                try {
                    if (session != null) {
                        session.close();
                    }
                } catch (final JMSException e) {
                    MessageConnector.logger.warn("Can not close session", e);
                }
            }
        } catch (final JMSException e) {
            throw new InfrastructureException(MessageConnector.CAN_NOT_CREATE_CONNECTION, e);
        } finally {
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (final JMSException e) {
                MessageConnector.logger.warn("Can not close connection", e);
            }
        }
    }

    private static TextMessage receiveFromDestination(final GetDestinationAction getDestinationAction, final String selector, final long timeout, final boolean secure) throws InfrastructureException, MessageCryptoException {
        final List<TextMessage> messages = MessageConnector.receiveBulkFromDestination(getDestinationAction, selector,
            1, timeout, secure);
        if (messages.size() != 1) {
            throw new InfrastructureException(MessageConnector.RECEIVE_FAILED);
        }
        return messages.get(0);
    }

    /**
     * @param queueName the name of the request queue
     * @param selector  the JMS selector (null or an empty string indicates that there is no selector)
     * @param timeout   the request timeout
     * @param secure    enable secure transport
     * @return the received {@link TextMessage}
     * @throws InfrastructureException on errors
     * @throws MessageCryptoException  on crypto errors
     */
    public static TextMessage receiveFromQueue(final String queueName, final String selector, final long timeout, final boolean secure) throws InfrastructureException, MessageCryptoException {
        return MessageConnector.receiveFromDestination(new GetResolveDestinationAction(true, queueName), selector,
            timeout, secure);
    }

    /**
     * @param queueName the name of the request queue
     * @param selector  the JMS selector (null or an empty string indicates that there is no selector)
     * @param maxSize   Max messages to receive
     * @param timeout   the request timeout
     * @param secure    enable secure transport
     * @return the received {@link TextMessage}s
     * @throws InfrastructureException on errors
     * @throws MessageCryptoException  on crypto errors
     */
    public static List<TextMessage> receiveBulkFromQueue(final String queueName, final String selector, final int maxSize, final long timeout, final boolean secure) throws InfrastructureException, MessageCryptoException {
        return MessageConnector.receiveBulkFromDestination(new GetResolveDestinationAction(true, queueName), selector,
            maxSize, timeout, secure);
    }

    /**
     * @param topicName the name of the topic
     * @param selector  the JMS selector (null or an empty string indicates that there is no selector)
     * @param timeout   the request timeout
     * @param secure    enable secure transport
     * @return the received {@link TextMessage}
     * @throws InfrastructureException on errors
     * @throws MessageCryptoException  on crypto errors
     */
    public static TextMessage receiveFromTopic(final String topicName, final String selector, final long timeout, final boolean secure) throws InfrastructureException, MessageCryptoException {
        return MessageConnector.receiveFromDestination(new GetResolveDestinationAction(false, topicName), selector,
            timeout, secure);
    }

    /**
     * @param topicName the name of the topic
     * @param selector  the JMS selector (null or an empty string indicates that there is no selector)
     * @param maxSize   Max messages to receive
     * @param timeout   the request timeout
     * @param secure    enable secure transport
     * @return the received {@link TextMessage}s
     * @throws InfrastructureException on errors
     * @throws MessageCryptoException  on crypto errors
     */
    public static List<TextMessage> receiveBulkFromTopic(final String topicName, final String selector, final int maxSize, final long timeout, final boolean secure) throws InfrastructureException, MessageCryptoException {
        return MessageConnector.receiveBulkFromDestination(new GetResolveDestinationAction(false, topicName), selector,
            maxSize, timeout, secure);
    }

    /**
     * @param queueName      the name of the request queue
     * @param body           the request body as String
     * @param headers        the request headers
     * @param secure         enable secure transport
     * @param receiveTimeout the request timeout
     * @param sendTimeout    the send timeout
     * @param priority       the message priority
     * @return the response {@link TextMessage}
     * @throws InfrastructureException on errors
     * @throws MessageCryptoException  on secure transport errors
     */
    public static TextMessage request(final String queueName, final String body, final Map<String, Object> headers, final boolean secure, final long receiveTimeout, final long sendTimeout, final int priority) throws InfrastructureException, MessageCryptoException {
        MessageConnector.checkInit();

        final String correlationId = UUID.randomUUID().toString();

        Connection connection = null;
        try {
            connection = MessageConnector.pooledConnectionFactory.createConnection();
            Session session = null;
            try {
                session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                final TemporaryQueue temporaryQueue;
                final Queue requestQueue;
                try {
                    temporaryQueue = session.createTemporaryQueue();
                    requestQueue = session.createQueue(queueName);
                } catch (final JMSException e) {
                    throw new InfrastructureException(MessageConnector.CAN_NOT_CREATE_DESTINATION, e);
                }
                MessageConsumer consumer = null;
                try {
                    consumer = session.createConsumer(temporaryQueue, "JMSCorrelationID = '" + correlationId + "'");
                    MessageProducer producer = null;
                    try {
                        producer = session.createProducer(requestQueue);
                        final TextMessage txt;
                        try {
                            txt = session.createTextMessage(body);
                            txt.setJMSCorrelationID(correlationId);
                            txt.setJMSReplyTo(temporaryQueue);
                            if (headers != null) {
                                final Set<Entry<String, Object>> entrySet = headers.entrySet();
                                for (final Entry<String, Object> entry : entrySet) {
                                    txt.setObjectProperty(entry.getKey(), entry.getValue());
                                }
                            }
                            if (secure) {
                                MessageConnector.secureMessage(txt);
                            }
                        } catch (final JMSException e) {
                            throw new SerializationException(MessageConnector.FAILED_TO_CREATE_MESSAGE, e);
                        }
                        producer.send(requestQueue, txt, DeliveryMode.NON_PERSISTENT, priority, sendTimeout);
                    } catch (final JMSException e) {
                        throw new InfrastructureException(MessageConnector.CAN_NOT_CREATE_PRODUCER, e);
                    } finally {
                        try {
                            if (producer != null) {
                                producer.close();
                            }
                        } catch (final JMSException e) {
                            MessageConnector.logger.warn("Can not close producer", e);
                        }
                    }
                    final Message receive;
                    try {
                        connection.start();
                        receive = consumer.receive(receiveTimeout);
                        connection.stop();
                    } catch (final JMSException e) {
                        throw new InfrastructureException(MessageConnector.RECEIVE_FAILED, e);
                    }
                    if (receive == null) {
                        throw new TimeoutException(receiveTimeout);
                    }
                    if (receive instanceof TextMessage) {
                        final TextMessage txtRes = (TextMessage) receive;
                        if (secure) {
                            MessageConnector.decryptMessage(txtRes);
                        }
                        return txtRes;
                    }
                    throw new InfrastructureException(MessageConnector.INVALID_RESPONSE_MESSAGE_RECEIVED);
                } catch (final JMSException e) {
                    throw new InfrastructureException(MessageConnector.CAN_NOT_CREATE_CONSUMER, e);
                } finally {
                    try {
                        if (consumer != null) {
                            consumer.close();
                        }
                    } catch (final JMSException e) {
                        MessageConnector.logger.warn("Can not close consumer", e);
                    }
                }
            } catch (final JMSException e) {
                throw new InfrastructureException(MessageConnector.CAN_NOT_CREATE_SESSION, e);
            } finally {
                try {
                    if (session != null) {
                        session.close();
                    }
                } catch (final JMSException e) {
                    MessageConnector.logger.warn("Can not close session", e);
                }
            }
        } catch (final JMSException e) {
            throw new InfrastructureException(MessageConnector.CAN_NOT_CREATE_CONNECTION, e);
        } finally {
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (final JMSException e) {
                MessageConnector.logger.warn("Can not close connection", e);
            }
        }
    }

    /**
     * @param queueName the name of the request queue
     * @param body      the request body as String
     * @param headers   the request headers
     * @return the response {@link TextMessage}
     * @throws InfrastructureException on errors
     */
    public static TextMessage request(final String queueName, final String body, final Map<String, Object> headers) throws InfrastructureException {
        try {
            return MessageConnector.request(queueName, body, headers, false, MessageConnector.REQUEST_TIMEOUT,
                MessageConnector.REQUEST_TIMEOUT, MessageConnector.MSGPRIORITY);
        } catch (final MessageCryptoException e) {
            // secure is false --> No MessageCryptoException
        }
        return null;
    }

    /**
     * @param txt the message to encrypt
     * @return if message is secure
     * @throws MessageCryptoException  on crypto errors
     * @throws InfrastructureException on infrastructure exception
     */
    public static boolean isMessageSecure(final TextMessage txt) throws MessageCryptoException, InfrastructureException {
        try {
            return txt.propertyExists(JmsMessageCryptoUtil.SIGNATURE_HEADER);
        } catch (JMSException e) {
            throw new InfrastructureException(MessageConnector.SECURITY_CHECK_FAILED, e);
        }
    }

    /**
     * @param txt the message to encrypt
     * @throws MessageCryptoException on crypto errors
     */
    public static void decryptMessage(final TextMessage txt) throws MessageCryptoException {
        try {
            if (!txt.propertyExists(JmsMessageCryptoUtil.SIGNATURE_HEADER)) {
                throw new MessageCryptoException(MessageConnector.SECURITY_CHECK_FAILED);
            }
            final String signature = txt.getStringProperty(JmsMessageCryptoUtil.SIGNATURE_HEADER);
            final boolean validate = JmsMessageCryptoUtil.validate(txt.getText(), signature);
            if (!validate) {
                throw new MessageCryptoException(MessageConnector.SECURITY_CHECK_FAILED);
            }

            if (txt instanceof ActiveMQTextMessage) {
                final ActiveMQTextMessage t = (ActiveMQTextMessage) txt;
                t.setReadOnlyBody(false);
            }
            final String decryptedText = JmsMessageCryptoUtil.decrypt(txt.getText());
            txt.setText(decryptedText);
        } catch (final JMSException e) {
            throw new MessageCryptoException(MessageConnector.SECURITY_CHECK_FAILED, e);
        }
    }

    /**
     * @param txt the message to encrypt
     * @throws JMSException           on JMS errors
     * @throws MessageCryptoException on crypto errors
     */
    public static void secureMessage(final TextMessage txt) throws JMSException, MessageCryptoException {
        final String cryptedText = JmsMessageCryptoUtil.crypt(txt.getText());
        txt.setText(cryptedText);
        txt.setStringProperty(JmsMessageCryptoUtil.SIGNATURE_HEADER, JmsMessageCryptoUtil.sign(cryptedText));
    }

    private static void checkInit() throws InfrastructureException {
        if (!MessageConnector.started.get()) {
            throw new InfrastructureException(MessageConnector.CONNECTION_NOT_READY);
        }
    }

}
