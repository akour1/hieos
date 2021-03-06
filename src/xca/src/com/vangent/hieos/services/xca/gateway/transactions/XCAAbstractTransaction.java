/*
 * This code is subject to the HIEOS License, Version 1.0
 *
 * Copyright(c) 2008-2009 Vangent, Inc.  All rights reserved.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.vangent.hieos.services.xca.gateway.transactions;

import com.vangent.hieos.services.xca.gateway.controller.XCARequestController;

import com.vangent.hieos.subjectmodel.DeviceInfo;
import com.vangent.hieos.xutil.atna.ATNAAuditEvent;
import com.vangent.hieos.xutil.atna.ATNAAuditEvent.ActorType;

import com.vangent.hieos.xutil.services.framework.XBaseTransaction;
import com.vangent.hieos.xutil.metadata.structure.MetadataSupport;
import com.vangent.hieos.xutil.xlog.client.XLogMessage;
import com.vangent.hieos.xutil.response.Response;
import com.vangent.hieos.xutil.xconfig.XConfig;
import com.vangent.hieos.xutil.xconfig.XConfigActor;
import com.vangent.hieos.xutil.exception.SchemaValidationException;
import com.vangent.hieos.xutil.exception.XdsException;
import com.vangent.hieos.xutil.exception.XdsInternalException;

import org.apache.log4j.Logger;
import java.util.ArrayList;
import org.apache.axiom.om.OMElement;

/**
 *
 * @author Bernie Thuman
 */
public abstract class XCAAbstractTransaction extends XBaseTransaction {

    private final static Logger logger = Logger.getLogger(XCAAbstractTransaction.class);
    //private XConfigActor gatewayConfig = null;
    private XCARequestController requestController = null;
    private boolean errorDetected = false;
    private ATNAAuditEvent.ActorType gatewayActorType;

    /**
     * 
     */
    public enum XCAResponseStatusType {

        /**
         * 
         */
        Success,
        /**
         *
         */
        PartialSuccess,
        /**
         * 
         */
        Failure
    };

    // Subclass responsibilities:
    /**
     * 
     * @param request
     * @throws XdsInternalException
     */
    abstract void prepareValidRequests(OMElement request) throws XdsInternalException;

    /**
     *
     * @param request
     */
    abstract void validateRequest(OMElement request);

    /**
     *
     * @param allResponses
     * @return
     * @throws XdsInternalException
     */
    abstract XCAResponseStatusType consolidateResponses(ArrayList<OMElement> allResponses) throws XdsInternalException;

    /**
     *
     * @return
     */
    protected XConfigActor getGatewayConfig() {
        return this.getConfigActor();
    }

    /**
     *
     * @param log_message
     * @param response
     */
    protected void init(XLogMessage log_message, Response response) {
        this.log_message = log_message;
        this.requestController = new XCARequestController(response, this.log_message);
        super.init(response); // Initialize superclass whole.
    }

    /**
     * Return the homeCommunityId for the local community.
     *
     * @return The homeCommunityId for the local community.
     */
    protected String getLocalHomeCommunityId() {
        String localHomeCommunityId = null;
        try {
            XConfig xconfig = XConfig.getInstance();
            localHomeCommunityId = xconfig.getHomeCommunityConfig().getUniqueId();
        } catch (XdsInternalException e) {
            logger.fatal(logger_exception_details(e));
            log_message.addErrorParam("Internal Error", "Could not find local homeCommunityId in XConfig");
        }
        return localHomeCommunityId;
    }

    /**
     * 
     * @param request
     * @return
     * @throws SchemaValidationException
     * @throws XdsInternalException
     */
    public OMElement run(OMElement request) throws SchemaValidationException, XdsInternalException {
        try {
            validateRequest(request);     // Concrete class responsibility.
            if (response.has_errors()) {
                return response.getResponse();      // Get out early.
            }
            runInternal(request);  // Do the real work.
        } catch (XdsException e) {
            // FIXME (Repository/Registry).
            response.add_error(MetadataSupport.XDSRepositoryError, e.getMessage(),
                    this.getClass().getName(), log_message);
            logger.fatal(logger_exception_details(e));
        }

        OMElement finalResponse = null;
        try {
            finalResponse = response.getResponse();  // This should only be called once.
        } catch (XdsInternalException e) {
            logger.fatal(logger_exception_details(e));
            log_message.addErrorParam("Internal Error", "Error generating response from XCA");
        }
        this.log_response();
        if (this.errorDetected == true) {
            log_message.setPass(false);  // Force this for internal errors detected.
        }
        return finalResponse;
    }

    /**
     * 
     * @param request
     * @throws XdsInternalException
     */
    private void runInternal(OMElement request) throws XdsInternalException {
        prepareValidRequests(request);
        ArrayList<OMElement> allResponses = requestController.sendRequests();
        XCAResponseStatusType status = consolidateResponses(allResponses);
        if (XCAResponseStatusType.PartialSuccess.equals(status)) {
            // This implies that we were able to successfully make at least one request.
            response.forcePartialSuccessStatus();
        }
    }

    /**
     *
     * @return
     */
    protected DeviceInfo getSenderDeviceInfo() {
        return this.getDeviceInfo(this.getGatewayConfig());
    }

    /**
     *
     * @param actorConfig
     * @return
     */
    protected DeviceInfo getDeviceInfo(XConfigActor actorConfig) {
        DeviceInfo deviceInfo = new DeviceInfo(actorConfig);
        return deviceInfo;
    }

    /**
     *
     * @param errorString
     */
    protected void logError(String errorString) {
        this.errorDetected = true;  // Make note of a problem.
        try {
            log_message.addErrorParam("Errors", errorString);
            logger.error(errorString);
        } catch (Exception e) {
            // We really need to ignore this exception to avoid indirect recursion.
            logger.error("XCA ERROR: Could not log info", e);
        }
    }

    /**
     * 
     * @param logLabel
     * @param infoString
     */
    protected void logInfo(String logLabel, String infoString) {
        try {
            log_message.addOtherParam(logLabel, infoString);
            logger.info(logLabel + " : " + infoString);
        } catch (Exception e) {
            logger.error("XCA ERROR: Could not log info", e);
        }
    }

    /**
     *
     * @return
     */
    protected XCARequestController getRequestController() {
        return this.requestController;
    }

    /**
     *
     * @return
     */
    public ActorType getGatewayActorType() {
        return gatewayActorType;
    }

    /**
     *
     * @param gatewayActorType
     */
    public void setGatewayActorType(ActorType gatewayActorType) {
        this.gatewayActorType = gatewayActorType;
    }
}
