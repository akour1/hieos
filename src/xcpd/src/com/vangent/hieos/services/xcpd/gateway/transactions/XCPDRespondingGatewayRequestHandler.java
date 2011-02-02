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
package com.vangent.hieos.services.xcpd.gateway.transactions;

import com.vangent.hieos.hl7v3util.model.message.HL7V3Message;
import com.vangent.hieos.hl7v3util.model.message.HL7V3MessageBuilderHelper;
import com.vangent.hieos.hl7v3util.model.message.PRPA_IN201305UV02_Message;
import com.vangent.hieos.hl7v3util.model.message.PRPA_IN201305UV02_Message_Builder;
import com.vangent.hieos.hl7v3util.model.message.PRPA_IN201306UV02_Message;
import com.vangent.hieos.hl7v3util.model.message.PRPA_IN201306UV02_Message_Builder;
import com.vangent.hieos.hl7v3util.model.subject.SubjectBuilder;
import com.vangent.hieos.hl7v3util.model.subject.SubjectSearchCriteriaBuilder;
import com.vangent.hieos.hl7v3util.model.subject.Custodian;
import com.vangent.hieos.hl7v3util.model.subject.DeviceInfo;
import com.vangent.hieos.hl7v3util.model.subject.Subject;
import com.vangent.hieos.hl7v3util.model.subject.SubjectSearchCriteria;
import com.vangent.hieos.services.xcpd.gateway.exception.XCPDException;
import com.vangent.hieos.xutil.xconfig.XConfig;
import com.vangent.hieos.xutil.xconfig.XConfigActor;
import com.vangent.hieos.xutil.xconfig.XConfigObject;
import com.vangent.hieos.xutil.xlog.client.XLogMessage;
import java.util.List;
import org.apache.axiom.om.OMElement;
import org.apache.axis2.AxisFault;
import org.apache.log4j.Logger;

/**
 *
 * @author Bernie Thuman
 */
public class XCPDRespondingGatewayRequestHandler extends XCPDGatewayRequestHandler {

    private final static Logger logger = Logger.getLogger(XCPDRespondingGatewayRequestHandler.class);
    private static XConfigActor _pdsConfig = null;

    /**
     * 
     * @param log_message
     * @param gatewayType
     */
    public XCPDRespondingGatewayRequestHandler(XLogMessage log_message, XCPDGatewayRequestHandler.GatewayType gatewayType) {
        super(log_message, gatewayType);
    }

    /**
     *
     * @param request
     * @param messageType
     * @return
     */
    public OMElement run(OMElement request, MessageType messageType) throws AxisFault {
        HL7V3Message result = null;
        switch (messageType) {
            case CrossGatewayPatientDiscovery:
                result = this.processCrossGatewayPatientDiscovery(new PRPA_IN201305UV02_Message(request));
                break;
            case PatientLocationQuery:
                //result = this.processPatientLocationQuery(request);
                break;

        }
        if (result != null) {
            log_message.addOtherParam("Response", result.getMessageNode());
        }
        log_message.setPass(true);  // PASS!
        return (result != null) ? result.getMessageNode() : null;
    }

    /**
     *
     * @param PRPA_IN201305UV02_Message
     * @return
     */
    private PRPA_IN201306UV02_Message processCrossGatewayPatientDiscovery(PRPA_IN201305UV02_Message request) throws AxisFault {
        // Validate request against XML schema.
        this.validateHL7V3Message(request);

        PRPA_IN201306UV02_Message queryResponse = null;
        try {
            // First convert the request.
            SubjectSearchCriteriaBuilder criteriaBuilder = new SubjectSearchCriteriaBuilder();
            SubjectSearchCriteria subjectSearchCriteria =
                    criteriaBuilder.buildSubjectSearchCriteriaFromPRPA_IN201305UV02_Message(request);

            this.performATNAAudit(request, subjectSearchCriteria, null /* endpoint */);

            // Validate against XCPD rules.
            this.validateRequest(subjectSearchCriteria);

            // Need to allow subjectIdentifier [e.g. reverse query]
            // Clear out any subject identifiers in the request since this could
            // cause problems with PDQ.
            //Subject subject = subjectSearchCriteria.getSubject();
            //if (subject.getSubjectIdentifiers().size() > 0)
            //{
            //    subject.setSubjectIdentifiers(new ArrayList<SubjectIdentifier>());
            //}

            // Now, make PDQ query to MPI.
            DeviceInfo senderDeviceInfo = this.getDeviceInfo();
            DeviceInfo receiverDeviceInfo = new DeviceInfo();
            receiverDeviceInfo.setId("TBD");  // FIXME (Receiver is PDS) ...
            PRPA_IN201305UV02_Message_Builder pdqQueryBuilder =
                    new PRPA_IN201305UV02_Message_Builder(senderDeviceInfo, receiverDeviceInfo);
            PRPA_IN201305UV02_Message pdqQuery =
                    pdqQueryBuilder.getPRPA_IN201305UV02_Message(
                    subjectSearchCriteria, false);
            PRPA_IN201306UV02_Message pdqResponse = this.findCandidatesQuery(pdqQuery);

            // FIXME: Should really check for errors from PDQ ....

            // Convert PDQ response.
            SubjectBuilder subjectBuilder = new SubjectBuilder();
            List<Subject> subjects = subjectBuilder.buildSubjectsFromPRPA_IN201306UV02_Message(pdqResponse);

            // Go through all subjects and add custodian
            for (Subject subject : subjects) {
                Custodian custodian = new Custodian();
                String homeCommunityId = this.getGatewayConfig().getUniqueId();
                homeCommunityId = homeCommunityId.replace("urn:oid:", "");
                custodian.setCustodianId(homeCommunityId);
                custodian.setSupportsHealthDataLocator(false);
                subject.setCustodian(custodian);
            }

            // Now prepare the XCPD response.
            queryResponse = this.getCrossGatewayPatientDiscoveryResponse(request, subjects, null);
        } catch (Exception ex) {
            log_message.setPass(false);
            log_message.addErrorParam("EXCEPTION", ex.getMessage());
            queryResponse = this.getCrossGatewayPatientDiscoveryResponse(
                    request, null /* subjects */, ex.getMessage());
            return queryResponse; // Early exit!
        }
        this.validateHL7V3Message(queryResponse);
        return queryResponse;

    }

    /**
     *
     * @param subjectSearchCriteria
     * @throws XCPDException
     */
    private void validateRequest(SubjectSearchCriteria subjectSearchCriteria) throws XCPDException {
        // Validate XCPD rules:
        Subject subject = subjectSearchCriteria.getSubject();

        // First see if any identifiers exist.
        if (subject.getSubjectIdentifiers().size() > 0) {
            return;  // All else are optional.
        }

        // Check required fields (Subject + BirthTime).
        if (subject.getSubjectNames().size() == 0) {
            throw new XCPDException("LivingSubjectName required");
        }

        if (subject.getBirthTime() == null) {
            throw new XCPDException("LivingSubjectBirthTime required");
        }
    }

    /**
     *
     * @param PRPA_IN201305UV02_Message
     * @param subjects
     * @param errorText
     * @return
     */
    private PRPA_IN201306UV02_Message getCrossGatewayPatientDiscoveryResponse(PRPA_IN201305UV02_Message request, List<Subject> subjects, String errorText) {
        DeviceInfo senderDeviceInfo = this.getDeviceInfo();
        DeviceInfo receiverDeviceInfo = HL7V3MessageBuilderHelper.getSenderDeviceInfo(request);
        PRPA_IN201306UV02_Message_Builder builder = new PRPA_IN201306UV02_Message_Builder(senderDeviceInfo, receiverDeviceInfo);
        return builder.buildPRPA_IN201306UV02_MessageFromSubjects(request, subjects, errorText);
    }

    /**
     *
     * @return
     * @throws AxisFault
     */
    @Override
    protected synchronized XConfigActor getPDSConfig() throws AxisFault {
        if (_pdsConfig != null) {
            return _pdsConfig;
        }
        XConfigObject gatewayConfig = this.getGatewayConfig();

        // Now get the "PDS" object (and cache it away).
        _pdsConfig = (XConfigActor) gatewayConfig.getXConfigObjectWithName("pds", XConfig.PDS_TYPE);
        return _pdsConfig;

    }
}