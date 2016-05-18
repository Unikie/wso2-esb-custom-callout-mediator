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
package fi.mystes.synapse.mediator.xml;

import static org.junit.Assert.assertTrue;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.impl.llom.OMElementImpl;
import org.junit.Before;
import org.junit.Test;

import fi.mystes.synapse.mediator.CustomCalloutMediator;

public class CustomCalloutMediatorFactoryTest {

    private CustomCalloutMediatorFactory factory;
    private OMElement mediatorElement;
    private String serviceURL = "http://www.mystes.fi/test/url";
    private final OMFactory omFactory = OMAbstractFactory.getOMFactory();

    @Before
    public void setUp() {
        factory = new CustomCalloutMediatorFactory();
        mediatorElement = new OMElementImpl(factory.getTagQName(), null, omFactory);
        mediatorElement.addAttribute("serviceURL", serviceURL, null);
    }

    @Test
    public void shouldInitiateCustomCalloutMediatorWithMandatoryField() {
        CustomCalloutMediator mediator = (CustomCalloutMediator) factory.createSpecificMediator(mediatorElement, null);
        assertTrue("Mediator should have serviceURL set", mediator.getServiceURL().equals(serviceURL));
        assertTrue("Mediator should use envelope as source", mediator.isUseEnvelopeAsSource());
    }

    @Test
    public void shouldInitiateCustomCalloutMediatorWithSourceAndTargetFields() {

        mediatorElement.addChild(omFactory.createOMElement(CustomCalloutMediatorFactory.Q_SOURCE)
                .addAttribute("xpath", "body/source", null).getOwner());

        mediatorElement.addChild(omFactory.createOMElement(CustomCalloutMediatorFactory.Q_TARGET)
                .addAttribute("xpath", "body/result", null).getOwner());
        CustomCalloutMediator mediator = (CustomCalloutMediator) factory.createSpecificMediator(mediatorElement, null);

        assertTrue("Mediator should have serviceURL set", mediator.getServiceURL().equals(serviceURL));
        assertTrue("Mediator should have source XPath", mediator.getRequestXPath().toString().equals("body/source"));
        assertTrue("Mediator should have target XPath", mediator.getTargetXPath().toString().equals("body/result"));
        assertTrue("Mediator should not use envelope as source", mediator.isUseEnvelopeAsSource() == false);
    }
}
