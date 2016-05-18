/**
 * Copyright 2016 Mystes Oy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fi.mystes.synapse.mediator;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMNamespace;
import org.apache.axiom.om.impl.OMNamespaceImpl;
import org.apache.axiom.om.impl.llom.factory.OMLinkedListMetaFactory;
import org.apache.axiom.soap.SOAPBody;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axiom.soap.SOAPFactory;
import org.apache.axiom.soap.impl.llom.SOAPEnvelopeImpl;
import org.apache.axiom.soap.impl.llom.soap11.SOAP11BodyImpl;
import org.apache.axiom.soap.impl.llom.soap11.SOAP11Factory;
import org.apache.axis2.AxisFault;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.ConfigurationContextFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.endpoints.Endpoint;
import org.apache.synapse.message.senders.blocking.BlockingMsgSender;
import org.apache.synapse.util.MessageHelper;
import org.apache.synapse.util.xpath.SynapseXPath;
import org.jaxen.JaxenException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ MessageHelper.class, ConfigurationContextFactory.class })
public class CustomCalloutMediatorTest {

    private CustomCalloutMediator callout;
    private String endpointName = "CustomEndpointName";

    private MessageContext reqMC, resMC;

    private org.apache.axis2.context.MessageContext reqAMC, resAMC;

    @Mock
    private Endpoint endpoint;

    @Mock
    private SynapseEnvironment environtment;

    @Mock
    private BlockingMsgSender blockingMsgSender;

    @Mock
    private ConfigurationContext cfgContext;

    @Mock
    private MessageContext reqMcMock;

    private SOAPBody reqBody, resBody;

    private SOAPEnvelope reqEnvelope;

    private SOAPEnvelope resEnvelope;

    private SOAPFactory soapFactory;

    private OMLinkedListMetaFactory omFactory;

    private OMNamespace ns;

    private String requestKey = "RequestEndpointKey";

    private OMElement requestElement;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        PowerMockito.mockStatic(MessageHelper.class);
        PowerMockito.mockStatic(ConfigurationContextFactory.class);

        soapFactory = new SOAP11Factory(omFactory);
        requestElement = soapFactory.createOMElement("Request", null);

        ns = new OMNamespaceImpl("http://schemas.xmlsoap.org/soap/envelope/", "env");

        reqAMC = new org.apache.axis2.context.MessageContext();

        reqEnvelope = new SOAPEnvelopeImpl(ns, soapFactory);
        reqBody = new SOAP11BodyImpl(reqEnvelope, soapFactory);
        reqBody.addChild(requestElement);
        reqMC = new Axis2MessageContext(reqAMC, null, environtment);
        reqMC.setEnvelope(reqEnvelope);
        reqMC.getContextEntries().put(requestKey, requestElement);
        reqMC.setProperty(requestKey, requestElement);

        resAMC = new org.apache.axis2.context.MessageContext();
        resEnvelope = new SOAPEnvelopeImpl(ns, soapFactory);
        resBody = new SOAP11BodyImpl(resEnvelope, soapFactory);
        resBody.addChild(soapFactory.createOMElement("Response", null));
        resMC = new Axis2MessageContext(resAMC, null, environtment);
        resMC.setEnvelope(resEnvelope);

        when(environtment.createMessageContext()).thenReturn(reqMC);
        when(endpoint.getName()).thenReturn(endpointName);
        when(MessageHelper.cloneMessageContext(reqMC)).thenReturn(reqMC);
        when(MessageHelper.cloneMessageContext(reqMcMock)).thenReturn(reqMcMock);
        when(blockingMsgSender.send(endpoint, reqMC)).thenReturn(resMC);
        when(blockingMsgSender.send(endpoint, reqMcMock)).thenReturn(resMC);
        when(ConfigurationContextFactory.createConfigurationContextFromFileSystem(anyString(), anyString()))
                .thenReturn(cfgContext);
        when(reqMcMock.getEndpoint(requestKey)).thenReturn(endpoint);

        callout = new CustomCalloutMediator();
        callout.setEndpoint(endpoint);
        callout.setUseEnvelopeAsSource(true);
        callout.initBlockingMsgSender(blockingMsgSender);
    }

    @Test
    public void shouldRequestWithEmptyEnvelopeAsSource() {
        callout.mediate(reqMC);
        assertTrue("Message context envelope should be as response envelope", reqMC.getEnvelope().equals(resEnvelope));
        assertTrue("Message context body should be as response body", reqMC.getEnvelope().getBody().equals(resBody));
        assertTrue("Message context shoul contain Response element",
                reqMC.getEnvelope().getBody().getFirstElement().getLocalName().equals("Response"));
    }

    @Test
    public void shouldRequestWithXPathSource() throws JaxenException {
        callout.setUseEnvelopeAsSource(true);
        callout.setRequestXPath(new SynapseXPath(
                soapFactory.createOMElement("Body",
                        new OMNamespaceImpl("http://schemas.xmlsoap.org/soap/envelope/", "env")),
                "env:Body/child::*[fn:position()=1]"));
        callout.mediate(reqMC);
        assertTrue("Message context envelope should be as response envelope", reqMC.getEnvelope().equals(resEnvelope));
        assertTrue("Message context body should be as response body", reqMC.getEnvelope().getBody().equals(resBody));
        assertTrue("Message context shoul contain Response element",
                reqMC.getEnvelope().getBody().getFirstElement().getLocalName().equals("Response"));
    }

    @Test
    public void shouldRequestWithRequestKeyAsPropertySource() throws JaxenException {
        callout.setRequestKey(requestKey);
        callout.mediate(reqMC);
        assertTrue("Message context envelope should be as response envelope", reqMC.getEnvelope().equals(resEnvelope));
        assertTrue("Message context body should be as response body", reqMC.getEnvelope().getBody().equals(resBody));
        assertTrue("Message context shoul contain Response element",
                reqMC.getEnvelope().getBody().getFirstElement().getLocalName().equals("Response"));
    }

    @Test
    public void shouldRequestWithRequestKeyAsEntrySource() throws JaxenException {
        callout.setRequestKey(requestKey);
        reqMC.getPropertyKeySet().remove(requestKey);
        callout.mediate(reqMC);
        assertTrue("Message context envelope should be as response envelope", reqMC.getEnvelope().equals(resEnvelope));
        assertTrue("Message context body should be as response body", reqMC.getEnvelope().getBody().equals(resBody));
        assertTrue("Message context shoul contain Response element",
                reqMC.getEnvelope().getBody().getFirstElement().getLocalName().equals("Response"));
    }

    @Test
    public void shouldRequestWithEndpointKey() throws JaxenException, AxisFault {
        callout.setEndpointKey(requestKey);
        callout.mediate(reqMcMock);
        verify(reqMcMock).setEnvelope(resEnvelope);
    }
}
