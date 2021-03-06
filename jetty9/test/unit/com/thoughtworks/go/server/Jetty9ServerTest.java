/*************************GO-LICENSE-START*********************************
 * Copyright 2015 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *************************GO-LICENSE-END***********************************/

package com.thoughtworks.go.server;

import com.thoughtworks.go.util.ArrayUtil;
import com.thoughtworks.go.util.ReflectionUtil;
import com.thoughtworks.go.util.SystemEnvironment;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.webapp.JettyWebXmlConfiguration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebInfConfiguration;
import org.eclipse.jetty.webapp.WebXmlConfiguration;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;

import javax.net.ssl.SSLSocketFactory;
import java.util.Iterator;
import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

public class Jetty9ServerTest {

    private Jetty9Server jetty9Server;
    private Server server;
    private SystemEnvironment systemEnvironment;
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    private SSLSocketFactory sslSocketFactory;


    @Before
    public void setUp() throws Exception {
        server = mock(Server.class);
        systemEnvironment = mock(SystemEnvironment.class);
        when(systemEnvironment.getServerPort()).thenReturn(1234);
        when(systemEnvironment.keystore()).thenReturn(temporaryFolder.newFolder());
        when(systemEnvironment.truststore()).thenReturn(temporaryFolder.newFolder());
        when(systemEnvironment.getWebappContextPath()).thenReturn("context");
        when(systemEnvironment.getCruiseWar()).thenReturn("cruise.war");
        when(systemEnvironment.getParentLoaderPriority()).thenReturn(true);
        when(systemEnvironment.useCompressedJs()).thenReturn(true);
        when(systemEnvironment.get(SystemEnvironment.RESPONSE_BUFFER_SIZE)).thenReturn(1000);
        when(systemEnvironment.get(SystemEnvironment.IDLE_TIMEOUT)).thenReturn(2000);

        sslSocketFactory = mock(SSLSocketFactory.class);
        when(sslSocketFactory.getSupportedCipherSuites()).thenReturn(new String[]{});
        jetty9Server = new Jetty9Server(systemEnvironment, "pwd", sslSocketFactory, server);
    }

    @Test
    public void shouldAddMBeanContainerAsEventListener() throws Exception {
        ArgumentCaptor<MBeanContainer> captor = ArgumentCaptor.forClass(MBeanContainer.class);
        jetty9Server.configure();

        verify(server).addEventListener(captor.capture());
        MBeanContainer mBeanContainer = captor.getValue();
        assertThat(mBeanContainer.getMBeanServer(), is(not(nullValue())));
    }

    @Test
    public void shouldAddHttpSocketConnector() throws Exception {
        ArgumentCaptor<Connector> captor = ArgumentCaptor.forClass(Connector.class);
        jetty9Server.configure();

        verify(server, times(2)).addConnector(captor.capture());

        List<Connector> connectors = captor.getAllValues();
        Connector plainConnector = connectors.get(0);

        assertThat(plainConnector instanceof ServerConnector, is(true));
        ServerConnector connector = (ServerConnector) plainConnector;
        assertThat(connector.getServer(), is(server));
        assertThat(connector.getConnectionFactories().size(), is(1));
        ConnectionFactory connectionFactory = connector.getConnectionFactories().iterator().next();
        assertThat(connectionFactory instanceof HttpConnectionFactory, is(true));
    }

    @Test
    public void shouldAddSSLSocketConnector() throws Exception {
        ArgumentCaptor<Connector> captor = ArgumentCaptor.forClass(Connector.class);
        jetty9Server.configure();

        verify(server, times(2)).addConnector(captor.capture());
        List<Connector> connectors = captor.getAllValues();
        Connector sslConnector = connectors.get(1);

        assertThat(sslConnector instanceof ServerConnector, is(true));
        ServerConnector connector = (ServerConnector) sslConnector;
        assertThat(connector.getServer(), is(server));
        assertThat(connector.getConnectionFactories().size(), is(2));
        Iterator<ConnectionFactory> iterator = connector.getConnectionFactories().iterator();
        ConnectionFactory first = iterator.next();
        ConnectionFactory second = iterator.next();
        assertThat(first instanceof SslConnectionFactory, is(true));
        SslConnectionFactory sslConnectionFactory = (SslConnectionFactory) first;
        assertThat(sslConnectionFactory.getProtocol(), is("SSL-HTTP/1.1"));
        assertThat(second instanceof HttpConnectionFactory, is(true));
    }

    @Test
    public void shouldAddWelcomeRequestHandler() throws Exception {
        ArgumentCaptor<HandlerCollection> captor = ArgumentCaptor.forClass(HandlerCollection.class);
        jetty9Server.configure();

        verify(server, times(1)).setHandler(captor.capture());
        HandlerCollection handlerCollection = captor.getValue();
        assertThat(handlerCollection.getHandlers().length, is(3));
        Handler handler = handlerCollection.getHandlers()[0];
        assertThat(handler instanceof Jetty9Server.GoServerWelcomeFileHandler, is(true));

        Jetty9Server.GoServerWelcomeFileHandler welcomeFileHandler = (Jetty9Server.GoServerWelcomeFileHandler) handler;
        assertThat(welcomeFileHandler.getContextPath(), is("/"));
    }

    @Test
    public void shouldAddResourceHandlerForAssets() throws Exception {
        ArgumentCaptor<HandlerCollection> captor = ArgumentCaptor.forClass(HandlerCollection.class);
        jetty9Server.configure();

        verify(server, times(1)).setHandler(captor.capture());
        HandlerCollection handlerCollection = captor.getValue();
        assertThat(handlerCollection.getHandlers().length, is(3));

        Handler handler = handlerCollection.getHandlers()[1];
        assertThat(handler instanceof AssetsContextHandler, is(true));
        AssetsContextHandler assetsContextHandler = (AssetsContextHandler) handler;
        assertThat(assetsContextHandler.getContextPath(), is("context/assets"));
    }

    @Test
    public void shouldAddWebAppContextHandler() throws Exception {
        ArgumentCaptor<HandlerCollection> captor = ArgumentCaptor.forClass(HandlerCollection.class);
        jetty9Server.configure();

        verify(server, times(1)).setHandler(captor.capture());
        HandlerCollection handlerCollection = captor.getValue();
        assertThat(handlerCollection.getHandlers().length, is(3));

        Handler handler = handlerCollection.getHandlers()[2];
        assertThat(handler instanceof WebAppContext, is(true));
        WebAppContext webAppContext = (WebAppContext) handler;
        List<String> configClasses = ArrayUtil.asList(webAppContext.getConfigurationClasses());
        assertThat(configClasses.contains(WebInfConfiguration.class.getCanonicalName()), is(true));
        assertThat(configClasses.contains(WebXmlConfiguration.class.getCanonicalName()), is(true));
        assertThat(configClasses.contains(JettyWebXmlConfiguration.class.getCanonicalName()), is(true));
        assertThat(webAppContext.getContextPath(), is("context"));
        assertThat(webAppContext.getWar(), is("cruise.war"));
        assertThat(webAppContext.isParentLoaderPriority(), is(true));
        assertThat(webAppContext.getDefaultsDescriptor(), is("jar:file:cruise.war!/WEB-INF/webdefault.xml"));
    }

    @Test
    public void shouldSetStopAtShutdown() throws Exception {
        jetty9Server.configure();
        verify(server).setStopAtShutdown(true);
    }

    @Test
    public void shouldAddExtraJarsIntoClassPath() throws Exception {
        jetty9Server.configure();
        jetty9Server.addExtraJarsToClasspath("test-addons/some-addon-dir/addon-1.JAR,test-addons/some-addon-dir/addon-2.jar");
        assertThat(getWebAppContext(jetty9Server).getExtraClasspath(), is("test-addons/some-addon-dir/addon-1.JAR,test-addons/some-addon-dir/addon-2.jar"));
    }

    @Test
    public void shouldSetInitParams() throws Exception {
        jetty9Server.configure();
        jetty9Server.setInitParameter("name", "value");

        assertThat(getWebAppContext(jetty9Server).getInitParameter("name"), CoreMatchers.is("value"));
    }

    private WebAppContext getWebAppContext(Jetty9Server server) {
        return (WebAppContext) ReflectionUtil.getField(server, "webAppContext");
    }
}