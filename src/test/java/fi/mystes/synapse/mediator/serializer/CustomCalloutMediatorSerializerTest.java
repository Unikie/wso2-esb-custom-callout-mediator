/**
 * Copyright 2016: Originally made by WSO2, Inc. (http://wso2.com), Modified by Mystes Oy
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
package fi.mystes.synapse.mediator.serializer;

import static org.junit.Assert.assertTrue;

import javax.xml.namespace.QName;

import org.apache.axiom.om.OMElement;
import org.apache.synapse.util.xpath.SynapseXPath;
import org.jaxen.JaxenException;
import org.junit.Before;
import org.junit.Test;

import fi.mystes.synapse.mediator.CustomCalloutMediator;

public class CustomCalloutMediatorSerializerTest {

    private CustomCalloutMediatorSerializer serializer;

    private String serviceURL = "http://www.mystes.fi/test/url";

    @Before
    public void setUp() {

        serializer = new CustomCalloutMediatorSerializer();
    }

    @Test
    public void shouldSerializeCustomCalloutMediatorWithNoChild() {
        OMElement mediatorElement = serializer.serializeSpecificMediator(new CustomCalloutMediator());
        assertTrue("Mediator element should not have children", mediatorElement.getFirstElement() == null);
    }

    @Test
    public void shouldSerializeCustomCalloutMediatorWithMandatoryFields() {
        CustomCalloutMediator mediator = new CustomCalloutMediator();
        mediator.setServiceURL(serviceURL);

        OMElement mediatorElement = serializer.serializeSpecificMediator(mediator);
        assertTrue("Mediator element should have serviceURL attribute",
                mediatorElement.getAttributeValue(new QName("serviceURL")).equals(serviceURL));
    }

    @Test
    public void shouldSerializeCustomCalloutMediatorWithSurceAndTargetFields() throws JaxenException {
        CustomCalloutMediator mediator = new CustomCalloutMediator();
        mediator.setServiceURL(serviceURL);
        mediator.setUseEnvelopeAsSource(false);
        mediator.setRequestXPath(new SynapseXPath("body/source"));
        mediator.setTargetXPath(new SynapseXPath("body/result"));

        OMElement mediatorElement = serializer.serializeSpecificMediator(mediator);
        assertTrue("Mediator element should have serviceURL attribute",
                mediatorElement.getAttributeValue(new QName("serviceURL")).equals(serviceURL));
        assertTrue("Mediator element should have source element with xpath attribute",
                ((OMElement) mediatorElement.getChildrenWithLocalName("source").next())
                        .getAttributeValue(new QName("xpath")).equals("body/source"));

        assertTrue("Mediator element should have target element with xpath attribute",
                ((OMElement) mediatorElement.getChildrenWithLocalName("target").next())
                        .getAttributeValue(new QName("xpath")).equals("body/result"));
    }

}
