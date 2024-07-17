package de.taimos.dvalin.interconnect.core.config;

/*-
 * #%L
 * Dvalin interconnect core library
 * %%
 * Copyright (C) 2016 - 2017 Taimos GmbH
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

import jakarta.jms.ConnectionFactory;

import de.taimos.dvalin.interconnect.core.ActiveMQPooledConnectionFactory;
import de.taimos.dvalin.interconnect.core.DvalinConnectionFactory;
import de.taimos.dvalin.interconnect.core.daemon.DaemonRequestResponse;
import de.taimos.dvalin.interconnect.core.daemon.IDaemonRequestResponse;
import de.taimos.dvalin.interconnect.core.spring.DaemonMessageListener;
import de.taimos.dvalin.interconnect.core.spring.IDaemonMessageHandlerFactory;
import de.taimos.dvalin.interconnect.core.spring.IDaemonMessageSender;
import de.taimos.dvalin.interconnect.core.spring.SingleDaemonMessageHandler;
import de.taimos.dvalin.interconnect.model.service.ADaemonHandler;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.pool.PooledConnectionFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.transaction.annotation.EnableTransactionManagement;


@SuppressWarnings("ALL")
@Configuration
@Profile(de.taimos.daemon.spring.Configuration.PROFILES_PRODUCTION)
@EnableTransactionManagement
public class JMSConfig {

    @Value("${interconnect.jms.broker}")
    private String brokerUrl;

    @Value("${interconnect.jms.consumers:2-8}")
    private String consumers;

    @Value("${serviceName}")
    private String serviceName;

    @Value("${interconnect.jms.userName:#{null}}")
    private String userName;

    @Value("${interconnect.jms.password:}")
    private String password;

    @Bean(name = "DvalinConnectionFactory")
    public ConnectionFactory jmsConnectionFactory() {
        return new DvalinConnectionFactory(this.brokerUrl, this.userName, this.password);

    }


    @Bean(destroyMethod = "stop")
    public PooledConnectionFactory jmsFactory(ConnectionFactory jmsConnectionFactory) {
        return new ActiveMQPooledConnectionFactory().initDefault(jmsConnectionFactory);
    }


    @Bean
    public JmsTemplate jmsTemplate(PooledConnectionFactory jmsFactory) {
        return new JmsTemplate(jmsFactory);
    }


    @Bean
    public JmsTemplate topicJmsTemplate(PooledConnectionFactory jmsFactory) {
        JmsTemplate template = new JmsTemplate(jmsFactory);
        template.setPubSubDomain(true);
        return template;
    }


    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
    public IDaemonRequestResponse requestResponse() {
        return new DaemonRequestResponse();
    }

    @Bean
    public IDaemonMessageHandlerFactory createDaemonMessageHandlerFactory(BeanFactory beanFactory, IDaemonMessageSender messageSender) {
        return logger -> {
            final ADaemonHandler rh = (ADaemonHandler) beanFactory.getBean("requestHandler");
            return new SingleDaemonMessageHandler(logger, rh.getClass(), messageSender, beanFactory);
        };
    }

    @Bean
    public DefaultMessageListenerContainer jmsListenerContainer(@Qualifier("DvalinConnectionFactory") ConnectionFactory jmsFactory, DaemonMessageListener messageListener) {
        DefaultMessageListenerContainer dmlc = new DefaultMessageListenerContainer();
        dmlc.setConnectionFactory(jmsFactory);
        dmlc.setErrorHandler(messageListener);
        dmlc.setConcurrency(this.consumers);

        dmlc.setDestination(new ActiveMQQueue(this.serviceName + ".request"));
        dmlc.setMessageListener(messageListener);
        return dmlc;
    }

}
