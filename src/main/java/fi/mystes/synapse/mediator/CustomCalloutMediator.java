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
package fi.mystes.synapse.mediator;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMNode;
import org.apache.axiom.soap.SOAPBody;
import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.ConfigurationContextFactory;
import org.apache.synapse.ManagedLifecycle;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.SynapseException;
import org.apache.synapse.SynapseLog;
import org.apache.synapse.commons.json.JsonUtil;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.endpoints.AbstractEndpoint;
import org.apache.synapse.endpoints.AddressEndpoint;
import org.apache.synapse.endpoints.DefaultEndpoint;
import org.apache.synapse.endpoints.Endpoint;
import org.apache.synapse.endpoints.EndpointDefinition;
import org.apache.synapse.mediators.AbstractMediator;
import org.apache.synapse.message.senders.blocking.BlockingMsgSender;
import org.apache.synapse.util.MessageHelper;
import org.apache.synapse.util.xpath.SynapseXPath;
import org.jaxen.JaxenException;

/**
 * Custom Callout mediator where SOAP fault handling is fixed.
 * 
 * <customCallout serviceURL="string" | endpointKey="string" [action="string"]
 * [initAxis2ClientOptions="boolean"]> <configuration [axis2xml="string"]
 * [repository="string"]/>? <endpoint/>? <source xpath="expression" |
 * key="string" | type="envelope">? <!-- key can be a MC property or entry key
 * --> <target xpath="expression" | key="string"/>? <enableSec policy="string" |
 * outboundPolicy="String" | inboundPolicy="String"/>? </customCallout>
 */
public class CustomCalloutMediator extends AbstractMediator implements ManagedLifecycle {

    private ConfigurationContext configCtx = null;
    private String serviceURL = null;
    private String action = null;
    private String requestKey = null;
    private SynapseXPath requestXPath = null;
    private SynapseXPath targetXPath = null;
    private String targetKey = null;
    private String clientRepository = null;
    private String axis2xml = null;
    private String useServerConfig = null;
    private boolean initClientOptions = true;
    private Endpoint endpoint;
    private String endpointKey = null;
    private boolean useEnvelopeAsSource = false;
    private boolean securityOn = false; // Should messages be sent using
    // WS-Security?
    private String wsSecPolicyKey = null;
    private String inboundWsSecPolicyKey = null;
    private String outboundWsSecPolicyKey = null;
    public final static String DEFAULT_CLIENT_REPO = "./repository/deployment/client";
    public final static String DEFAULT_AXIS2_XML = "./repository/conf/axis2/axis2_blocking_client.xml";
    private boolean isWrappingEndpointCreated = false;

    BlockingMsgSender blockingMsgSender = null;

    /**
     * Invokes the mediator passing the current message for mediation. Each
     * mediator performs its mediation action, and returns true if mediation
     * should continue, or false if further mediation should be aborted.
     *
     * @param context
     *            Current message context for mediation
     * @return true if further mediation should continue, otherwise false
     */
    @Override
    public boolean mediate(MessageContext synCtx) {
        SynapseLog synLog = getLog(synCtx);

        debugMediatorStartOnDemand(synCtx, synLog);

        try {

            initClientOptionsOnBlockingMsgSender();

            if (endpointKey != null) {
                endpoint = synCtx.getEndpoint(endpointKey);
            }

            debugEndpoint(synLog);

            enableMtomAtEndpointOnDemand(synCtx);

            MessageContext synapseOutMsgCtx = MessageHelper.cloneMessageContext(synCtx);
            handlePayloadAsJsonOnDemand(synCtx, synapseOutMsgCtx);

            if (action != null) {
                synapseOutMsgCtx.setWSAAction(action);
            }

            debugServiceInvocationOnDemand(synLog, synapseOutMsgCtx);

            MessageContext resultMsgCtx = invokeService(synCtx, synapseOutMsgCtx);

            traceResponseOnDemand(synLog, resultMsgCtx);

            if (resultMsgCtx != null) {
                processResponseMessageContext(synCtx, resultMsgCtx);
            } else {
                synLog.traceOrDebug("Service returned a null response");
            }

        } catch (AxisFault e) {
            handleException(
                    "Error invoking service : " + serviceURL + (action != null ? " with action : " + action : ""), e,
                    synCtx);
        } catch (JaxenException e) {
            handleException("Error while evaluating the XPath expression: " + targetXPath, e, synCtx);
        } catch (Exception e) {
            e.printStackTrace();
        }

        synLog.traceOrDebug("End : CustomCallout mediator");
        return true;
    }

    /**
     * Helper method to process given response message context.
     * 
     * @param synCtx
     *            Request/current message context
     * @param resultMsgCtx
     *            Response message context
     * @throws JaxenException
     *             If processing XML data from response message context fails
     * @throws AxisFault
     *             If setting envelope to current message context fails
     */
    private void processResponseMessageContext(MessageContext synCtx, MessageContext resultMsgCtx)
            throws JaxenException, AxisFault {
        org.apache.axis2.context.MessageContext mc = ((Axis2MessageContext) resultMsgCtx).getAxis2MessageContext();
        if (JsonUtil.hasAJsonPayload(mc)) {
            JsonUtil.cloneJsonPayload(mc, ((Axis2MessageContext) synCtx).getAxis2MessageContext());
        } else {
            if (targetXPath != null) {
                Object o = targetXPath.evaluate(synCtx);
                OMElement result = resultMsgCtx.getEnvelope().getBody().getFirstElement();
                if (o != null && o instanceof OMElement) {
                    OMNode tgtNode = (OMElement) o;
                    tgtNode.insertSiblingAfter(result);
                    tgtNode.detach();
                } else if (o != null && o instanceof List && !((List<?>) o).isEmpty()) {
                    // Always fetches *only* the first
                    OMNode tgtNode = (OMElement) ((List<?>) o).get(0);
                    tgtNode.insertSiblingAfter(result);
                    tgtNode.detach();
                } else {
                    handleException("Evaluation of target XPath expression : " + targetXPath.toString()
                            + " did not yeild an OMNode", synCtx);
                }
            } else if (targetKey != null) {
                OMElement result = resultMsgCtx.getEnvelope().getBody().getFirstElement();
                synCtx.setProperty(targetKey, result);
            } else {
                synCtx.setEnvelope(resultMsgCtx.getEnvelope());
            }
        }
    }

    /**
     * Helper method to trace given response message context if trace is
     * enabled.
     * 
     * @param synLog
     *            To check whether trace is enable and trace given message
     *            context
     * @param resultMsgCtx
     *            Message context to be traced
     */
    private void traceResponseOnDemand(SynapseLog synLog, MessageContext resultMsgCtx) {
        if (synLog.isTraceTraceEnabled() && resultMsgCtx != null) {
            synLog.traceTrace("Response payload received : " + resultMsgCtx.getEnvelope());
        }
    }

    /**
     * Helper method to perform service invocation with blocking message sender.
     * 
     * @param synCtx
     *            Contains properties which define whether invocation is out
     *            only
     * @param synapseOutMsgCtx
     *            Contains the payload to be sent
     * @return New message context as response
     */
    private MessageContext invokeService(MessageContext synCtx, MessageContext synapseOutMsgCtx) {
        MessageContext resultMsgCtx = null;
        try {
            if ("true".equals(synCtx.getProperty(SynapseConstants.OUT_ONLY))) {
                blockingMsgSender.send(endpoint, synapseOutMsgCtx);
            } else {
                resultMsgCtx = blockingMsgSender.send(endpoint, synapseOutMsgCtx);

                if ("true".equals(resultMsgCtx.getProperty(SynapseConstants.BLOCKING_SENDER_ERROR))) {
                    handleFault(synCtx, (Exception) synCtx.getProperty(SynapseConstants.ERROR_EXCEPTION));
                }
                if (resultMsgCtx.getEnvelope().hasFault()) {
                    resultMsgCtx.setFaultResponse(true);
                    Exception e = new AxisFault(resultMsgCtx.getEnvelope().getBody().getFault());
                    handleFault(synCtx, e);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            handleFault(synCtx, ex);
        }
        return resultMsgCtx;
    }

    /**
     * Helper method to debug service invocation if trace/debug enabled.
     * 
     * @param synLog
     *            To check whether trace/debug is enable and debug the service
     *            invocation
     * @param synapseOutMsgCtx
     *            Contains SOAP envelop to trace
     */
    private void debugServiceInvocationOnDemand(SynapseLog synLog, MessageContext synapseOutMsgCtx) {
        if (synLog.isTraceOrDebugEnabled()) {
            synLog.traceOrDebug("About to invoke the service");
            if (synLog.isTraceTraceEnabled()) {
                synLog.traceTrace("Request message payload : " + synapseOutMsgCtx.getEnvelope());
            }
        }
    }

    /**
     * Helper method to check and process payload as JSON if message context
     * contains JSON payload.
     * 
     * @param synCtx
     *            To retrieve payload from
     * @param synapseOutMsgCtx
     *            To check whether JSON payload is available from
     * @throws AxisFault
     *             If payload retrieval fails
     */
    private void handlePayloadAsJsonOnDemand(MessageContext synCtx, MessageContext synapseOutMsgCtx) throws AxisFault {
        if (!useEnvelopeAsSource
                // if the payload is JSON, we do not consider the request
                // (ie. source) path. Instead, we use the complete payload.
                && !JsonUtil.hasAJsonPayload(((Axis2MessageContext) synapseOutMsgCtx).getAxis2MessageContext())) {
            SOAPBody soapBody = synapseOutMsgCtx.getEnvelope().getBody();
            for (Iterator<?> itr = soapBody.getChildElements(); itr.hasNext();) {
                OMElement child = (OMElement) itr.next();
                child.detach();
            }
            soapBody.addChild(getRequestPayload(synCtx));
        }
    }

    /**
     * Helper method to enable MTOM at endpoint if 'isWrappingEndpointCreate'
     * boolean flag is set to 'true'.
     * 
     * @param synCtx
     *            Message context contains properties to be checked
     */
    private void enableMtomAtEndpointOnDemand(MessageContext synCtx) {
        if (isWrappingEndpointCreated) {
            org.apache.axis2.context.MessageContext axis2MsgCtx = ((Axis2MessageContext) synCtx)
                    .getAxis2MessageContext();
            if (Constants.VALUE_TRUE.equals(axis2MsgCtx.getProperty(Constants.Configuration.ENABLE_MTOM))) {
                ((AbstractEndpoint) endpoint).getDefinition().setUseMTOM(true);
            }
        }
    }

    /**
     * Helper method to debug endpoint or service URL if trace/debug enabled.
     * 
     * @param synLog
     *            To check whether trace/debug is enable and debug the endpoint
     *            or service URL
     */
    private void debugEndpoint(SynapseLog synLog) {
        if (synLog.isTraceOrDebugEnabled()) {
            if (!isWrappingEndpointCreated) {
                synLog.traceOrDebug("Using the defined endpoint : " + endpoint.getName());
            } else {
                if (serviceURL != null) {
                    synLog.traceOrDebug("Using the serviceURL : " + serviceURL);
                } else {
                    synLog.traceOrDebug("Using the To header as the EPR ");
                }
                if (securityOn) {
                    synLog.traceOrDebug("Security enabled within the CustomCallout Mediator config");
                    if (wsSecPolicyKey != null) {
                        synLog.traceOrDebug("Using security policy key : " + wsSecPolicyKey);
                    } else {
                        if (inboundWsSecPolicyKey != null) {
                            synLog.traceOrDebug("Using inbound security policy key : " + inboundWsSecPolicyKey);
                        }
                        if (outboundWsSecPolicyKey != null) {
                            synLog.traceOrDebug("Using outbound security policy key : " + outboundWsSecPolicyKey);
                        }
                    }
                }
            }
        }
    }

    /**
     * Helper method to initiate client options on blocking message sender if
     * 'initClientOptions' flag is set to false.
     */
    private void initClientOptionsOnBlockingMsgSender() {
        if (!initClientOptions) {
            blockingMsgSender.setInitClientOptions(false);
        }
    }

    /**
     * Helper method to debug start of mediator if debug/trace is enabled.
     * 
     * @param synCtx
     *            Contains SOAP envelop to trace
     * @param synLog
     *            To check whether trace/debug is enable and debug the start
     */
    private void debugMediatorStartOnDemand(MessageContext synCtx, SynapseLog synLog) {
        if (synLog.isTraceOrDebugEnabled()) {
            synLog.traceOrDebug("Start : CustomCallout mediator");

            if (synLog.isTraceTraceEnabled()) {
                synLog.traceTrace("Message : " + synCtx.getEnvelope());
            }
        }
    }

    /**
     * Helper method to handle SOAP fault.
     * 
     * @param synCtx
     *            Current message context
     * @param ex
     *            Occurred exception
     */
    private void handleFault(MessageContext synCtx, Exception ex) {
        synCtx.setProperty(SynapseConstants.SENDING_FAULT, Boolean.TRUE);

        if (ex instanceof AxisFault) {
            AxisFault axisFault = (AxisFault) ex;

            if (axisFault.getFaultCodeElement() != null) {
                synCtx.setProperty(SynapseConstants.ERROR_CODE, axisFault.getFaultCodeElement().getText());
            } else {
                synCtx.setProperty(SynapseConstants.ERROR_CODE, SynapseConstants.CALLOUT_OPERATION_FAILED);
            }

            if (axisFault.getMessage() != null) {
                synCtx.setProperty(SynapseConstants.ERROR_MESSAGE, axisFault.getMessage());
            } else {
                synCtx.setProperty(SynapseConstants.ERROR_MESSAGE,
                        "Error while performing " + "the CustomCallout operation");
            }

            if (axisFault.getFaultDetailElement() != null) {
                if (axisFault.getFaultDetailElement().getFirstElement() != null) {
                    synCtx.setProperty(SynapseConstants.ERROR_DETAIL,
                            axisFault.getFaultDetailElement().getFirstElement());
                } else {
                    synCtx.setProperty(SynapseConstants.ERROR_DETAIL, axisFault.getFaultDetailElement().getText());
                }
            }
        }

        synCtx.setProperty(SynapseConstants.ERROR_EXCEPTION, ex);
        throw new SynapseException("Error while performing the CustomCallout operation", ex);
    }

    /**
     * Helper method to retrieve current request payload.
     * 
     * @param synCtx
     *            Message context contains payload
     * @return Retrieved payload as OMElement or null
     * @throws AxisFault
     *             If payload retrieval fails
     */
    private OMElement getRequestPayload(MessageContext synCtx) throws AxisFault {

        if (requestKey != null) {
            Object request = synCtx.getProperty(requestKey);
            if (request == null) {
                request = synCtx.getEntry(requestKey);
            }
            if (request != null && request instanceof OMElement) {
                return (OMElement) request;
            } else {
                handleException("The property : " + requestKey + " is not an OMElement", synCtx);
            }
        } else if (requestXPath != null) {
            try {
                Object o = requestXPath.evaluate(MessageHelper.cloneMessageContext(synCtx));

                if (o instanceof OMElement) {
                    return (OMElement) o;
                } else if (o instanceof List && !((List<?>) o).isEmpty()) {
                    return (OMElement) ((List<?>) o).get(0); // Always fetches
                    // *only* the
                    // first
                } else {
                    handleException("The evaluation of the XPath expression : " + requestXPath.toString()
                            + " did not result in an OMElement", synCtx);
                }
            } catch (JaxenException e) {
                handleException("Error evaluating XPath expression : " + requestXPath.toString(), e, synCtx);
            }
        }
        return null;
    }

    /**
     * Overridden method to initiate CustomCalloutMediator.
     */
    @Override
    public void init(SynapseEnvironment synEnv) {
        try {
            configCtx = ConfigurationContextFactory.createConfigurationContextFromFileSystem(
                    clientRepository != null ? clientRepository : DEFAULT_CLIENT_REPO,
                    axis2xml != null ? axis2xml : DEFAULT_AXIS2_XML);
            if (serviceURL != null) {
                serviceURL = changeEndPointReference(serviceURL);
            }

            initBlockingMsgSender(new BlockingMsgSender());

            EndpointDefinition endpointDefinition = null;

            if (serviceURL != null) {
                // If Service URL is specified, it is given the highest priority
                endpoint = new AddressEndpoint();
                endpointDefinition = new EndpointDefinition();
                endpointDefinition.setAddress(serviceURL);
                ((AddressEndpoint) endpoint).setDefinition(endpointDefinition);
                isWrappingEndpointCreated = true;
            } else if (endpoint == null && endpointKey == null) {
                // Use a default endpoint in this case - i.e. the To header
                endpoint = new DefaultEndpoint();
                endpointDefinition = new EndpointDefinition();
                ((DefaultEndpoint) endpoint).setDefinition(endpointDefinition);
                isWrappingEndpointCreated = true;
            }
            // If the endpoint is specified, we'll look it up at mediation time.

            if (endpointDefinition != null && isSecurityOn()) {
                endpointDefinition.setSecurityOn(true);
                if (wsSecPolicyKey != null) {
                    endpointDefinition.setWsSecPolicyKey(wsSecPolicyKey);
                } else {
                    if (inboundWsSecPolicyKey != null) {
                        endpointDefinition.setInboundWsSecPolicyKey(inboundWsSecPolicyKey);
                    }
                    if (outboundWsSecPolicyKey != null) {
                        endpointDefinition.setOutboundWsSecPolicyKey(outboundWsSecPolicyKey);
                    }
                }
            }
        } catch (AxisFault e) {
            String msg = "Error initializing CustomCallout mediator : " + e.getMessage();
            log.error(msg, e);
            throw new SynapseException(msg, e);
        }
    }

    /**
     * Helper method to initiate blocking message sender. Used also in tests.
     * 
     * @param bmsgs
     *            Blocking message sender to be set and initiate
     */
    public void initBlockingMsgSender(BlockingMsgSender bmsgs) {
        blockingMsgSender = bmsgs;
        blockingMsgSender.setConfigurationContext(configCtx);
        blockingMsgSender.init();
    }

    /**
     * Overridden method to destroy CustomCalloutMethod.
     */
    @Override
    public void destroy() {
        try {
            configCtx.terminate();
        } catch (AxisFault ignore) {
        }
    }

    /**
     * Getter for service URL.
     * 
     * @return String containing service URL
     */
    public String getServiceURL() {
        return serviceURL;
    }

    /**
     * Setter for service URL.
     * 
     * @param serviceURL
     *            Service URL to be set
     */
    public void setServiceURL(String serviceURL) {
        this.serviceURL = serviceURL;
    }

    /**
     * Getter for action.
     * 
     * @return String containing action
     */
    public String getAction() {
        return action;
    }

    /**
     * Setter for action.
     * 
     * @param action
     *            Action to be set
     */
    public void setAction(String action) {
        this.action = action;
    }

    /**
     * Getter for use server config.
     * 
     * @return Server config
     */
    public String getUseServerConfig() {
        return useServerConfig;
    }

    /**
     * Setter for use server config.
     * 
     * @param useServerConfig
     *            Use server config to be set
     */
    public void setUseServerConfig(String useServerConfig) {
        this.useServerConfig = useServerConfig;
    }

    /**
     * Getter for request key.
     * 
     * @return Request key
     */
    public String getRequestKey() {
        return requestKey;
    }

    /**
     * Setter for request key.
     * 
     * @param requestKey
     *            Key to be set
     */
    public void setRequestKey(String requestKey) {
        this.requestKey = requestKey;
    }

    /**
     * Setter for request XPath.
     * 
     * @param requestXPath
     *            XPath to be set
     */
    public void setRequestXPath(SynapseXPath requestXPath) {
        this.requestXPath = requestXPath;
    }

    /**
     * Setter for target XPath.
     * 
     * @param targetXPath
     *            XPath to be set
     */
    public void setTargetXPath(SynapseXPath targetXPath) {
        this.targetXPath = targetXPath;
    }

    /**
     * Getter for target key.
     * 
     * @return Target key
     */
    public String getTargetKey() {
        return targetKey;
    }

    /**
     * Setter for target key.
     * 
     * @param targetKey
     *            Key to be set
     */
    public void setTargetKey(String targetKey) {
        this.targetKey = targetKey;
    }

    /**
     * Getter for request XPath.
     * 
     * @return XPath for request
     */
    public SynapseXPath getRequestXPath() {
        return requestXPath;
    }

    /**
     * Getter for target XPath.
     * 
     * @return Target XPath
     */
    public SynapseXPath getTargetXPath() {
        return targetXPath;
    }

    /**
     * Getter for client repository.
     * 
     * @return Client repository
     */
    public String getClientRepository() {
        return clientRepository;
    }

    /**
     * Setter for client repository.
     * 
     * @param clientRepository
     */
    public void setClientRepository(String clientRepository) {
        this.clientRepository = clientRepository;
    }

    /**
     * Getter for Axis2 XML configuration file.
     * 
     * @return Path of Axix2 XML configuration file
     */
    public String getAxis2xml() {
        return axis2xml;
    }

    /**
     * Setter for Axis2 XML configuration file path.
     * 
     * @param axis2xml
     *            Path to be set
     */
    public void setAxis2xml(String axis2xml) {
        this.axis2xml = axis2xml;
    }

    /**
     * Setter for endpoint key.
     * 
     * @param key
     *            Endpoint key to be set
     */
    public void setEndpointKey(String key) {
        this.endpointKey = key;
    }

    /**
     * Getter for endpoint key.
     * 
     * @return Endopoint key
     */
    public String getEndpointKey() {
        return endpointKey;
    }

    /**
     * Getter for initiate client options boolean flag.
     * 
     * @return True/false whether to initiate client options
     */
    public boolean getInitClientOptions() {
        return initClientOptions;
    }

    /**
     * Setter for initiate client options boolean flag.
     * 
     * @param initClientOptions
     *            True/false whether to initiate client options
     */
    public void setInitClientOptions(boolean initClientOptions) {
        this.initClientOptions = initClientOptions;
    }

    /**
     * Getter for use envelope as source boolean flag.
     * 
     * @return True/false whether to use envelope as source
     */
    public boolean isUseEnvelopeAsSource() {
        return useEnvelopeAsSource;
    }

    /**
     * Setter for use envelope as source boolean flag.
     * 
     * @param useEnvelopeAsSource
     *            True/false whether to use envelope as source
     */
    public void setUseEnvelopeAsSource(boolean useEnvelopeAsSource) {
        this.useEnvelopeAsSource = useEnvelopeAsSource;
    }

    /**
     * Is WS-Security turned on on this endpoint?
     * 
     * @return true if on
     */
    public boolean isSecurityOn() {
        return securityOn;
    }

    /**
     * Request that WS-Sec be turned on/off on this endpoint
     * 
     * @param securityOn
     *            a boolean flag indicating security is on or not
     */
    public void setSecurityOn(boolean securityOn) {
        this.securityOn = securityOn;
    }

    /**
     * Return the Rampart Security configuration policys' 'key' to be used (See
     * Rampart)
     * 
     * @return the Rampart Security configuration policys' 'key' to be used (See
     *         Rampart)
     */
    public String getWsSecPolicyKey() {
        return wsSecPolicyKey;
    }

    /**
     * Set the Rampart Security configuration policys' 'key' to be used (See
     * Rampart)
     * 
     * @param wsSecPolicyKey
     *            the Rampart Security configuration policys' 'key' to be used
     */
    public void setWsSecPolicyKey(String wsSecPolicyKey) {
        this.wsSecPolicyKey = wsSecPolicyKey;
    }

    /**
     * Get the outbound security policy key. This is used when we specify
     * different policies for inbound and outbound.
     * 
     * @return outbound security policy key
     */
    public String getOutboundWsSecPolicyKey() {
        return outboundWsSecPolicyKey;
    }

    /**
     * Set the outbound security policy key.This is used when we specify
     * different policies for inbound and outbound.
     * 
     * @param outboundWsSecPolicyKey
     *            outbound security policy key.
     */
    public void setOutboundWsSecPolicyKey(String outboundWsSecPolicyKey) {
        this.outboundWsSecPolicyKey = outboundWsSecPolicyKey;
    }

    /**
     * Get the inbound security policy key. This is used when we specify
     * different policies for inbound and outbound.
     * 
     * @return inbound security policy key
     */
    public String getInboundWsSecPolicyKey() {
        return inboundWsSecPolicyKey;
    }

    /**
     * Set the inbound security policy key. This is used when we specify
     * different policies for inbound and outbound.
     * 
     * @param inboundWsSecPolicyKey
     *            inbound security policy key.
     */
    public void setInboundWsSecPolicyKey(String inboundWsSecPolicyKey) {
        this.inboundWsSecPolicyKey = inboundWsSecPolicyKey;
    }

    /**
     * This method checks for dynamic url in CustomCallout mediator and replace
     * it with given system properties. properties has to given as
     * -D{parameter}={value}
     * 
     * @param epr
     *            end point url
     * @return fixed end point url
     */
    private String changeEndPointReference(String epr) {

        if (epr.toLowerCase().contains("system.prop")) {
            Pattern p = Pattern.compile("\\{(.*?)\\}");
            Matcher m = p.matcher(epr);
            Map<String, String> result = new HashMap<String, String>();
            while (m.find()) {
                result.put(m.group(1), "");
                String propName = System.getProperty(m.group(1).replace("system.prop.", ""));
                if (propName != null) {
                    epr = epr.replace("{" + m.group(1) + "}", propName);
                } else {
                    log.warn("System property is not initialized");
                }
            }
            log.info("Dynamic properties of url are replaced");
        }

        return epr;
    }

    /**
     * Get the defined endpoint
     * 
     * @return endpoint
     */
    public Endpoint getEndpoint() {
        if (!isWrappingEndpointCreated) {
            return endpoint;
        }
        return null;
    }

    /**
     * Set the defined endpoint
     * 
     * @param endpoint
     *            defined endpoint
     */
    public void setEndpoint(Endpoint endpoint) {
        this.endpoint = endpoint;
    }

}
