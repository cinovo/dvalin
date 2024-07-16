/**
 *
 */
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

import jakarta.annotation.PostConstruct;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.ee10.websocket.server.JettyWebSocketServlet;
import org.eclipse.jetty.ee10.websocket.server.JettyWebSocketServletFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

public class WebSocketContextHandler extends ServletContextHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketContextHandler.class);

    @Value("${websocket.baseuri:/websocket}")
    private String baseURI;

    @Autowired
    private ListableBeanFactory beanFactory;


    @PostConstruct
    public void init() {
        this.setContextPath(this.baseURI);

        String[] socketBeans = this.beanFactory.getBeanNamesForAnnotation(WebSocket.class);
        for (String sb : socketBeans) {
            WebSocket ann = this.beanFactory.findAnnotationOnBean(sb, WebSocket.class);
            if (ann != null) {
                String pathSpec = ann.pathSpec();
                WebSocketContextHandler.LOGGER.info("Found bean {} for path {}", sb, pathSpec);
                this.addServlet(new ServletHolder(this.createServletForBeanName(sb)), pathSpec);
            }
        }
    }

    private JettyWebSocketServlet createServletForBeanName(final String beanName) {
        return new JettyWebSocketServlet() {

            private static final long serialVersionUID = 1L;


            @Override
            public void configure(JettyWebSocketServletFactory factory) {
                WebSocketContextHandler.LOGGER.info("Configuring WebSocket Servlet for {}", beanName);
                factory.setIdleTimeout(Duration.of(10000, ChronoUnit.MILLIS));
                factory.setCreator((req, resp) -> WebSocketContextHandler.this.beanFactory.getBean(beanName));
            }
        };
    }

}
