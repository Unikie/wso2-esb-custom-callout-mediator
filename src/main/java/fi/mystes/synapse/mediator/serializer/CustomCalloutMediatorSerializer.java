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

import org.apache.axiom.om.OMElement;
import org.apache.synapse.Mediator;
import org.apache.synapse.config.xml.AbstractMediatorSerializer;
import org.apache.synapse.config.xml.SynapseXPathSerializer;
import org.apache.synapse.config.xml.endpoints.EndpointSerializer;
import org.apache.synapse.endpoints.Endpoint;
import org.kohsuke.MetaInfServices;

import fi.mystes.synapse.mediator.CustomCalloutMediator;

/**
 * Mediator serializer class to transform mediator instance to OMElement
 * instance.
 * 
 * <pre>
 * &lt;customCallout serviceURL="string" | endpointKey="string" [action="string"] [initAxis2ClientOptions="boolean"]&gt;
 *      &lt;configuration [axis2xml="string"] [repository="string"]/&gt;?
 *      &lt;endpoint/&gt;?
 *      &lt;source xpath="expression" | key="string" | type="envelope" &gt;?
 *      &lt;target xpath="expression" | key="string"/&gt;?
 *      &lt;enableSec policy="string" | outboundPolicy="String" | inboundPolicy="String" /&gt;?
 * &lt;/customCallout&gt;
 * </pre>
 */
@MetaInfServices(org.apache.synapse.config.xml.MediatorSerializer.class)
public class CustomCalloutMediatorSerializer extends AbstractMediatorSerializer {

    /**
     * Get CustomCallout Mediator class name
     */
    @Override
    public String getMediatorClassName() {
        return CustomCalloutMediator.class.getName();
    }

    /**
     * Performs the mediator serialization by transfoming mediator instance into
     * OMElement instance.
     */
    @Override
    public OMElement serializeSpecificMediator(Mediator m) {

        if (!(m instanceof CustomCalloutMediator)) {
            handleException("Unsupported mediator passed in for serialization : " + m.getType());
        }

        CustomCalloutMediator mediator = (CustomCalloutMediator) m;
        OMElement callout = fac.createOMElement("customCallout", synNS);
        saveTracingState(callout, mediator);

        setEndpointKeyOrServiceUrlToCalloutOnDemand(mediator, callout);

        setEndpointToCalloutOnDemand(mediator, callout);

        setActionToCalloutOnDemand(mediator, callout);

        setUseServerConfigToCalloutOnDemand(mediator, callout);

        setInitAxis2ClientOptionsToCalloutOnDemand(mediator, callout);

        setClientRepositoryToCalloutOnDemand(mediator, callout);

        setSourceToCalloutOnDemand(mediator, callout);

        setTargetToCalloutOnDemand(mediator, callout);

        enableSecurityAtCalloutOnDemand(mediator, callout);

        return callout;
    }

    /**
     * Helper method to enable WS security on given OMElement callout.
     * 
     * @param mediator
     *            Contains information about WS security
     * @param callout
     *            OMElement to enable WS security to
     */
    private void enableSecurityAtCalloutOnDemand(CustomCalloutMediator mediator, OMElement callout) {
        if (mediator.isSecurityOn()) {
            OMElement security = fac.createOMElement("enableSec", synNS);
            if (mediator.getWsSecPolicyKey() != null) {
                security.addAttribute(fac.createOMAttribute("policy", nullNS, mediator.getWsSecPolicyKey()));
                callout.addChild(security);
            } else if (mediator.getOutboundWsSecPolicyKey() != null || mediator.getInboundWsSecPolicyKey() != null) {
                if (mediator.getOutboundWsSecPolicyKey() != null) {
                    security.addAttribute(
                            fac.createOMAttribute("outboundPolicy", nullNS, mediator.getOutboundWsSecPolicyKey()));
                }
                if (mediator.getInboundWsSecPolicyKey() != null) {
                    security.addAttribute(
                            fac.createOMAttribute("inboundPolicy", nullNS, mediator.getInboundWsSecPolicyKey()));
                }
                callout.addChild(security);
            }
        }
    }

    /**
     * Helper method to set target to given OMElement callout.
     * 
     * @param mediator
     *            Contains information about target
     * @param callout
     *            To set target to
     */
    private void setTargetToCalloutOnDemand(CustomCalloutMediator mediator, OMElement callout) {
        if (mediator.getTargetXPath() != null) {
            OMElement target = fac.createOMElement("target", synNS, callout);
            SynapseXPathSerializer.serializeXPath(mediator.getTargetXPath(), target, "xpath");
        } else if (mediator.getTargetKey() != null) {
            OMElement target = fac.createOMElement("target", synNS, callout);
            target.addAttribute(fac.createOMAttribute("key", nullNS, mediator.getTargetKey()));
        }
    }

    /**
     * Helper method to set source to given OMElement callout.
     * 
     * @param mediator
     *            Contains information about source
     * @param callout
     *            To set source to
     */
    private void setSourceToCalloutOnDemand(CustomCalloutMediator mediator, OMElement callout) {
        if (mediator.isUseEnvelopeAsSource()) {
            OMElement source = fac.createOMElement("source", synNS, callout);
            source.addAttribute(fac.createOMAttribute("type", nullNS, "envelope"));
        } else if (mediator.getRequestXPath() != null) {
            OMElement source = fac.createOMElement("source", synNS, callout);
            SynapseXPathSerializer.serializeXPath(mediator.getRequestXPath(), source, "xpath");
        } else if (mediator.getRequestKey() != null) {
            OMElement source = fac.createOMElement("source", synNS, callout);
            source.addAttribute(fac.createOMAttribute("key", nullNS, mediator.getRequestKey()));
        }
    }

    /**
     * Helper method to set client repository to given OMElement callout.
     * 
     * @param mediator
     *            Contains information about client repository
     * @param callout
     *            To set client repository to
     */
    private void setClientRepositoryToCalloutOnDemand(CustomCalloutMediator mediator, OMElement callout) {
        if (mediator.getClientRepository() != null || mediator.getAxis2xml() != null) {
            OMElement config = fac.createOMElement("configuration", synNS);
            if (mediator.getClientRepository() != null) {
                config.addAttribute(fac.createOMAttribute("repository", nullNS, mediator.getClientRepository()));
            }
            if (mediator.getAxis2xml() != null) {
                config.addAttribute(fac.createOMAttribute("axis2xml", nullNS, mediator.getAxis2xml()));
            }
            callout.addChild(config);
        }
    }

    /**
     * Helper method to set Axis2 client options to given OMElement callout.
     * 
     * @param mediator
     *            Contains information about Axis2 client options
     * @param callout
     *            To set Axis2 client options to
     */
    private void setInitAxis2ClientOptionsToCalloutOnDemand(CustomCalloutMediator mediator, OMElement callout) {
        if (!mediator.getInitClientOptions()) {
            callout.addAttribute(fac.createOMAttribute("initAxis2ClientOptions", nullNS,
                    Boolean.toString(mediator.getInitClientOptions())));
        }
    }

    /**
     * Helper method to set use server configuration to given OMElement callout.
     * 
     * @param mediator
     *            Contains information about use server configuration
     * @param callout
     *            To set use server configuration to
     */
    private void setUseServerConfigToCalloutOnDemand(CustomCalloutMediator mediator, OMElement callout) {
        if (mediator.getUseServerConfig() != null) {
            callout.addAttribute(fac.createOMAttribute("useServerConfig", nullNS, mediator.getUseServerConfig()));
        }
    }

    /**
     * Helper method to set action to given OMElement callout.
     * 
     * @param mediator
     *            Contains information about action
     * @param callout
     *            To set action to
     */
    private void setActionToCalloutOnDemand(CustomCalloutMediator mediator, OMElement callout) {
        if (mediator.getAction() != null) {
            callout.addAttribute(fac.createOMAttribute("action", nullNS, mediator.getAction()));
        }
    }

    /**
     * Helper method to set endpoint to given OMElement callout.
     * 
     * @param mediator
     *            Contains information about endpoint
     * @param callout
     *            To set endpoint to
     */
    private void setEndpointToCalloutOnDemand(CustomCalloutMediator mediator, OMElement callout) {
        Endpoint endpoint = mediator.getEndpoint();
        if (endpoint != null) {
            callout.addChild(EndpointSerializer.getElementFromEndpoint(endpoint));
        }
    }

    /**
     * Helper method to set endpoint key or service URL to given OMElement
     * callout. If service URL is available it will be set to callout. If
     * service URL is not available but endpoint key is available, then endpoint
     * key will be set to callout.
     * 
     * @param mediator
     *            Contains information about endpoint key and service URL
     * @param callout
     *            To set endpoint key or service URL to
     */
    private void setEndpointKeyOrServiceUrlToCalloutOnDemand(CustomCalloutMediator mediator, OMElement callout) {
        if (mediator.getServiceURL() != null) {
            callout.addAttribute(fac.createOMAttribute("serviceURL", nullNS, mediator.getServiceURL()));
        } else if (mediator.getEndpointKey() != null) {
            callout.addAttribute(fac.createOMAttribute("endpointKey", nullNS, mediator.getEndpointKey()));
        }
    }
}
