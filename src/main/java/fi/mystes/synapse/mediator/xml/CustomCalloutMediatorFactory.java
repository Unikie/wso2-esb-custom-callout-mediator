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
package fi.mystes.synapse.mediator.xml;

import java.io.File;
import java.util.Properties;

import javax.xml.namespace.QName;

import org.apache.axiom.om.OMAttribute;
import org.apache.axiom.om.OMElement;
import org.apache.synapse.Mediator;
import org.apache.synapse.config.xml.AbstractMediatorFactory;
import org.apache.synapse.config.xml.SynapseXPathFactory;
import org.apache.synapse.config.xml.XMLConfigConstants;
import org.apache.synapse.config.xml.endpoints.EndpointFactory;
import org.apache.synapse.endpoints.Endpoint;
import org.jaxen.JaxenException;
import org.kohsuke.MetaInfServices;

import fi.mystes.synapse.mediator.CustomCalloutMediator;

/**
 * Factory for {@link CustomCalloutMediator} instances.
 * 
 * <pre>
 * &lt;customCallout serviceURL="string" | endpointKey="string" [action="string"]&gt;
 *      &lt;configuration [axis2xml="string"] [repository="string"]/&gt;?
 *      &lt;endpoint/&gt;?
 *      &lt;source xpath="expression" | key="string" | type="envelope"&gt;?
 *      &lt;target xpath="expression" | key="string"/&gt;?
 *      &lt;enableSec policy="string" | outboundPolicy="String" | inboundPolicy="String" /&gt;?
 * &lt;/customCallout&gt;
 * </pre>
 */
@MetaInfServices(org.apache.synapse.config.xml.MediatorFactory.class)
public class CustomCalloutMediatorFactory extends AbstractMediatorFactory {

    public static final QName TAG_NAME = new QName(XMLConfigConstants.SYNAPSE_NAMESPACE, "customCallout");
    public static final QName ATT_URL = new QName("serviceURL");
    public static final QName ATT_ENDPOINT = new QName("endpointKey");
    public static final QName ATT_ACTION = new QName("action");
    public static final QName ATT_AXIS2XML = new QName("axis2xml");
    public static final QName ATT_USESERVERCONFIG = new QName("useServerConfig");
    public static final QName ATT_REPOSITORY = new QName("repository");
    public static final QName ATT_INIT_AXI2_CLIENT_OPTIONS = new QName("initAxis2ClientOptions");
    public static final QName Q_CONFIG = new QName(XMLConfigConstants.SYNAPSE_NAMESPACE, "configuration");
    public static final QName Q_SOURCE = new QName(XMLConfigConstants.SYNAPSE_NAMESPACE, "source");
    public static final QName Q_TARGET = new QName(XMLConfigConstants.SYNAPSE_NAMESPACE, "target");
    public static final QName ATT_SOURCE_TYPE = new QName(XMLConfigConstants.NULL_NAMESPACE, "type");
    public static final QName Q_SEC = new QName(XMLConfigConstants.SYNAPSE_NAMESPACE, "enableSec");
    public static final QName ATT_POLICY = new QName(XMLConfigConstants.NULL_NAMESPACE, "policy");
    public static final QName ATT_OUTBOUND_SEC_POLICY = new QName(XMLConfigConstants.NULL_NAMESPACE, "outboundPolicy");
    public static final QName ATT_INBOUND_SEC_POLICY = new QName(XMLConfigConstants.NULL_NAMESPACE, "inboundPolicy");
    public static final QName Q_ENDPOINT = new QName(XMLConfigConstants.SYNAPSE_NAMESPACE, "endpoint");

    /**
     * The QName of custom callout mediator element in the XML config
     * 
     * @return QName of wrapper mediator
     */
    @Override
    public QName getTagQName() {
        return TAG_NAME;
    }

    /**
     * Specific mediator factory implementation to build the
     * org.apache.synapse.Mediator by the given XML configuration
     * 
     * @param OMElement
     *            element configuration element describing the properties of the
     *            mediator
     * @param properties
     *            bag of properties to pass in any information to the factory
     * 
     * @return built wrapper mediator
     */
    @Override
    public Mediator createSpecificMediator(OMElement elem, Properties properties) {

        CustomCalloutMediator callout = new CustomCalloutMediator();

        setEndpointKeyOrServiceUrlAttributeToCalloutOnDemand(elem, callout);

        setEndpointToCalloutOnDemand(elem, properties, callout);

        setActionToCalloutOnDemand(elem, callout);

        setUseServiceConfigToCalloutOnDemand(elem, callout);

        setInitAxis2ClientOptionsToCalloutOnDemand(elem, callout);

        setAxis2ConfigAndClientRepositoryToCalloutOnDemand(elem, callout);

        setSourceToCallout(elem, callout);

        setTargetToCalloutOnDemand(elem, callout);

        enableWsSecurityAtCalloutOnDemand(elem, callout);

        return callout;
    }

    /**
     * Helper method to enable WS security on given callout.
     * 
     * @param elem
     *            Contains necessary element for security enabling
     * @param callout
     *            Mediator to enable WS security on
     */
    private void enableWsSecurityAtCalloutOnDemand(OMElement elem, CustomCalloutMediator callout) {
        OMElement wsSec = elem.getFirstChildWithName(Q_SEC);
        if (wsSec != null) {
            callout.setSecurityOn(true);
            OMAttribute policyKey = wsSec.getAttribute(ATT_POLICY);
            OMAttribute outboundPolicyKey = wsSec.getAttribute(ATT_OUTBOUND_SEC_POLICY);
            OMAttribute inboundPolicyKey = wsSec.getAttribute(ATT_INBOUND_SEC_POLICY);
            if (policyKey != null) {
                callout.setWsSecPolicyKey(policyKey.getAttributeValue());
            } else if (outboundPolicyKey != null || inboundPolicyKey != null) {
                if (outboundPolicyKey != null) {
                    callout.setOutboundWsSecPolicyKey(outboundPolicyKey.getAttributeValue());
                }
                if (inboundPolicyKey != null) {
                    callout.setInboundWsSecPolicyKey(inboundPolicyKey.getAttributeValue());
                }
            } else {
                callout.setSecurityOn(false);
                handleException("A policy key is required to enable security");
            }
        }
    }

    /**
     * Helper method to set target to given callout.
     * 
     * @param elem
     *            Contains necessary element for target
     * @param callout
     *            Mediator to set target to
     */
    private void setTargetToCalloutOnDemand(OMElement elem, CustomCalloutMediator callout) {
        OMElement targetElt = elem.getFirstChildWithName(Q_TARGET);
        if (targetElt != null) {
            if (targetElt.getAttribute(ATT_XPATH) != null) {
                try {
                    callout.setTargetXPath(SynapseXPathFactory.getSynapseXPath(targetElt, ATT_XPATH));
                } catch (JaxenException e) {
                    handleException("Invalid target XPath : " + targetElt.getAttributeValue(ATT_XPATH));
                }
            } else if (targetElt.getAttribute(ATT_KEY) != null) {
                callout.setTargetKey(targetElt.getAttributeValue(ATT_KEY));
            } else {
                handleException("A 'xpath' or 'key' attribute " + "is required for the Callout 'target'");
            }
        }
    }

    /**
     * Helper method to set source to given callout. If source not defined
     * within payload, evelope will be used as source for given callout.
     * 
     * @param elem
     *            Contains necessary element for source
     * @param callout
     *            Mediator to set source to
     */
    private void setSourceToCallout(OMElement elem, CustomCalloutMediator callout) {
        OMElement sourceElt = elem.getFirstChildWithName(Q_SOURCE);
        if (sourceElt != null) {
            OMAttribute sourceType = sourceElt.getAttribute(ATT_SOURCE_TYPE);

            if (sourceType != null && sourceType.getAttributeValue().equals("envelope")) {
                callout.setUseEnvelopeAsSource(true);
            } else if (sourceElt.getAttribute(ATT_XPATH) != null) {
                try {
                    callout.setRequestXPath(SynapseXPathFactory.getSynapseXPath(sourceElt, ATT_XPATH));
                } catch (JaxenException e) {
                    handleException("Invalid source XPath : " + sourceElt.getAttributeValue(ATT_XPATH));
                }
            } else if (sourceElt.getAttribute(ATT_KEY) != null) {
                callout.setRequestKey(sourceElt.getAttributeValue(ATT_KEY));
            } else {
                handleException("A 'xpath' or 'key' attribute " + "is required for the Callout 'source'");
            }
        } else {
            callout.setUseEnvelopeAsSource(true);
        }
    }

    /**
     * Helper method to whether set Axis2 XML configuration or client repository
     * to given callout.
     * 
     * @param elem
     *            Contains necessary element for Axis2 XML configuration
     * @param callout
     */
    private void setAxis2ConfigAndClientRepositoryToCalloutOnDemand(OMElement elem, CustomCalloutMediator callout) {
        OMElement configElt = elem.getFirstChildWithName(Q_CONFIG);
        if (configElt != null) {
            setAxis2XmlConfigToCalloutOnDemand(callout, configElt);
            setClientRepositoryToCalloutOnDemand(callout, configElt);
        }
    }

    /**
     * Helper method to set client repository to given callout.
     * 
     * @param callout
     *            Mediator to set client repository to
     * @param configElt
     *            Contains necessary attribute for client repository
     */
    private void setClientRepositoryToCalloutOnDemand(CustomCalloutMediator callout, OMElement configElt) {
        OMAttribute repoAttr = configElt.getAttribute(ATT_REPOSITORY);
        if (repoAttr != null && repoAttr.getAttributeValue() != null) {
            File repo = new File(repoAttr.getAttributeValue());
            if (repo.exists() && repo.isDirectory()) {
                callout.setClientRepository(repoAttr.getAttributeValue());
            } else {
                handleException("Invalid repository path: " + repoAttr.getAttributeValue());
            }
        }
    }

    /**
     * Helper method to set Axis2 XML configuration to given callout.
     * 
     * @param callout
     *            Mediator to set Axis2 XML configuration to
     * @param configElt
     *            Contains necessary attribute for Axis2 XML configuration
     */
    private void setAxis2XmlConfigToCalloutOnDemand(CustomCalloutMediator callout, OMElement configElt) {
        OMAttribute axis2xmlAttr = configElt.getAttribute(ATT_AXIS2XML);

        if (axis2xmlAttr != null && axis2xmlAttr.getAttributeValue() != null) {
            File axis2xml = new File(axis2xmlAttr.getAttributeValue());
            if (axis2xml.exists() && axis2xml.isFile()) {
                callout.setAxis2xml(axis2xmlAttr.getAttributeValue());
            } else {
                handleException("Invalid axis2.xml path: " + axis2xmlAttr.getAttributeValue());
            }
        }
    }

    /**
     * Helper method to make given callout mediator whether to enable/disable
     * initiate client options.
     * 
     * @param elem
     *            Contains necessary attribute for 'initAxis2ClientOptions'
     * @param callout
     *            Mediator to enable/disable 'initAxi2ClientOptions' to/from
     */
    private void setInitAxis2ClientOptionsToCalloutOnDemand(OMElement elem, CustomCalloutMediator callout) {
        OMAttribute initAxis2ClientOptions = elem.getAttribute(ATT_INIT_AXI2_CLIENT_OPTIONS);
        if (initAxis2ClientOptions != null) {
            if ("true".equals(initAxis2ClientOptions.getAttributeValue().toLowerCase())) {
                callout.setInitClientOptions(true);
            } else if ("false".equals(initAxis2ClientOptions.getAttributeValue().toLowerCase())) {
                callout.setInitClientOptions(false);
            } else {
                handleException("The 'initAxis2ClientOptions' attribute only accepts a boolean value.");
            }
        }
    }

    /**
     * Helper method to set use server config to given callout.
     * 
     * @param elem
     *            Contains necessary attribute for 'useServerConfig'
     * @param callout
     *            Mediator to set use server config to
     */
    private void setUseServiceConfigToCalloutOnDemand(OMElement elem, CustomCalloutMediator callout) {
        OMAttribute attUseServerConfig = elem.getAttribute(ATT_USESERVERCONFIG);
        if (attUseServerConfig != null) {
            callout.setUseServerConfig(attUseServerConfig.getAttributeValue());
        }
    }

    /**
     * Helper method to set action to given callout.
     * 
     * @param elem
     *            Contains necessary attribute for action
     * @param callout
     *            Mediator to set action to
     */
    private void setActionToCalloutOnDemand(OMElement elem, CustomCalloutMediator callout) {
        OMAttribute attAction = elem.getAttribute(ATT_ACTION);
        if (attAction != null) {
            callout.setAction(attAction.getAttributeValue());
        }
    }

    /**
     * Helper method to set endpoint to given callout mediator.
     * 
     * @param elem
     *            Contains necessary element
     * @param properties
     *            Properties needed by Endpoint factory
     * @param callout
     *            Mediator to set endpoint to
     */
    private void setEndpointToCalloutOnDemand(OMElement elem, Properties properties, CustomCalloutMediator callout) {
        OMElement epElement = elem.getFirstChildWithName(Q_ENDPOINT);
        if (epElement != null) {
            Endpoint endpoint = EndpointFactory.getEndpointFromElement(epElement, true, properties);
            if (endpoint != null) {
                callout.setEndpoint(endpoint);
            }
        }
    }

    /**
     * Helper method to set endpoint key or service URL to given callout
     * mediator.
     * 
     * @param elem
     *            Contains necessary attributes
     * @param callout
     *            Mediator to set endpoint key or service URL to
     */
    private void setEndpointKeyOrServiceUrlAttributeToCalloutOnDemand(OMElement elem, CustomCalloutMediator callout) {
        OMAttribute attEndpoint = elem.getAttribute(ATT_ENDPOINT);
        OMAttribute attServiceURL = elem.getAttribute(ATT_URL);
        if (attServiceURL != null) {
            callout.setServiceURL(attServiceURL.getAttributeValue());
        } else if (attEndpoint != null) {
            callout.setEndpointKey(attEndpoint.getAttributeValue());
        }
    }

}
