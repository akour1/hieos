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
package com.vangent.hieos.xutil.atna;

import com.vangent.hieos.xutil.xconfig.XConfig;
import com.vangent.hieos.xutil.exception.MetadataException;
import com.vangent.hieos.xutil.exception.MetadataValidationException;
import com.vangent.hieos.xutil.exception.XdsInternalException;
import com.vangent.hieos.xutil.metadata.structure.Metadata;
import com.vangent.hieos.xutil.metadata.structure.MetadataSupport;
import com.vangent.hieos.xutil.metadata.structure.ParamParser;
//import com.vangent.hieos.xutil.base64.Base64Coder;

// Third-party.
import com.vangent.hieos.xutil.metadata.structure.SqParams;
import com.vangent.hieos.xutil.xua.client.XServiceProvider;
import java.io.UnsupportedEncodingException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.net.URL;
import org.apache.axiom.om.OMElement;
import org.apache.axis2.context.MessageContext;
import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;

/**
 *
 * @author Vincent Lewis (original)
 * @author Sastry Dhara  (clean up,, refactor)
 * @author Ravi Nistala  (clean up, refactor)
 * @author Bernie Thuman (rewrite).
 */
public class XATNALogger {

    private final static Logger logger = Logger.getLogger(XATNALogger.class);
    // Public accessible parameters.
    public static final String TXN_ITI8 = "ITI-8";
    public static final String TXN_ITI18 = "ITI-18";
    public static final String TXN_ITI38 = "ITI-38";
    public static final String TXN_ITI39 = "ITI-39";
    public static final String TXN_ITI41 = "ITI-41";
    public static final String TXN_ITI42 = "ITI-42";
    public static final String TXN_ITI43 = "ITI-43";
    public static final String TXN_ITI44 = "ITI-44";
    public static final String TXN_ITI55 = "ITI-55";
    public static final String TXN_START = "START";
    public static final String TXN_STOP = "STOP";

    // BHT: Deals with OutcomeIndicator as defined by DICOM Supplement 95
    public enum OutcomeIndicator {

        SUCCESS(0), MINOR_FAILURE(4), SERIOUS_FAILURE(8), MAJOR_FAILURE(12);
        private final int value;

        OutcomeIndicator(int value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return Integer.toString(value);
        }
    }

    public enum ActorType {

        REGISTRY, REPOSITORY, DOCCONSUMER, INITIATING_GATEWAY, RESPONDING_GATEWAY, UNKNOWN, DOCRECIPIENT
    }
    private static final String REG_STOR_QRY = "Registry Stored Query";
    private static final String REG_DOC_SET = "Register Document Set-b";
    private static final String CRS_GTWY_QRY = "Cross Gateway Query";
    private static final String CRS_GTWY_PATIENT_DISCOVERY = "Cross Gateway Patient Discovery";
    private static final String IHE_TX = "IHE Transactions";
    //private static final String RTV_DOC = "Retrieve Document";
    private static final String RTV_DOC_SET = "Retrieve Document Set";
    private static final String CRS_GTWY_RTV = "Cross Gateway Retrieve";
    private static final String PROVD_N_REG_DOC_SET_B = "Provide and Register Document Set b";
    private static final String PATIENT_IDENTITY_FEED = "Patient Identity Feed";
    private static final String IHE_XDS_MDT = "IHE XDS Metadata";
    private static final String DCM = "DCM";
    private static final String APL_STRT = "Application Start";
    private static final String APL_ACTV = "Application Activity";
    private static final String APL_STP = "Application Stop";
    //private static final String UNDEF = "UNKNOWN";
    private static final String AUDIT_SRC_SUFFIX = "VANGENT_HIEOS";
    private ActorType actorType = ActorType.UNKNOWN;
    private String transactionId;
    private boolean performAudit = false;
    private AuditMessageBuilder amb = null;
    private OutcomeIndicator outcome = OutcomeIndicator.SUCCESS;
    //private String sourceId = "UNKNOWN";
    // Context variables.
    //private String hostName = "";
    private String hostAddress = "";
    private String pid = "";
    private String endpoint = "";
    private String fromAddress = "";
    private String replyTo = "";
    private String targetEndpoint = "";
    private String userName = "";

    /**
     * 
     * @param transactionId
     * @throws java.lang.Exception
     */
    public XATNALogger(String transactionId, ActorType actorType) throws Exception {
        this.transactionId = transactionId;
        this.actorType = actorType;
        this.performAudit = XConfig.getInstance().getHomeCommunityConfigPropertyAsBoolean("ATNAperformAudit");
    }

    /**
     * 
     * @param rootNode
     * @param targetEndpoint
     * @param successFlag
     * @throws java.lang.Exception
     */
    public void performAudit(OMElement rootNode, String targetEndpoint, OutcomeIndicator outcome) throws Exception {
        if (!this.performAudit) {
            return;  // Early exit.
        }
        // Prep for audit.
        this.outcome = outcome;
        this.setContextVariables(targetEndpoint);

        // Audit ProvideAndRegisterDocumentSetb
        if (TXN_ITI41.equals(this.transactionId)) {
            this.auditProvideAndRegisterDocumentSetbToRepository(rootNode);
        } else if (TXN_ITI42.equals(this.transactionId) && this.actorType == ActorType.REPOSITORY) {
            this.auditRegisterDocumentSetbFromRepository(rootNode);
        } else if (TXN_ITI42.equals(this.transactionId) && this.actorType == ActorType.REGISTRY) {
            this.auditRegisterDocumentSetbToRegistry(rootNode);
        } else if ((TXN_ITI43.equals(this.transactionId) || (TXN_ITI39.equals(this.transactionId))) && this.actorType == ActorType.REPOSITORY) {
            this.auditRetrieveDocumentSetToRepository(rootNode);
        } else if ((TXN_ITI43.equals(this.transactionId) || (TXN_ITI39.equals(this.transactionId))) && this.actorType == ActorType.DOCCONSUMER) {
            this.auditRetrieveDocumentSetFromConsumer(rootNode);
        } else if ((TXN_ITI18.equals(this.transactionId) || (TXN_ITI38.equals(this.transactionId))) && this.actorType == ActorType.REGISTRY) {
            this.auditRegistryStoredQueryToRegistry(rootNode);
        } else if ((TXN_ITI18.equals(this.transactionId) || (TXN_ITI38.equals(this.transactionId))) && this.actorType == ActorType.DOCCONSUMER) {
            this.auditRegistryStoredQueryFromConsumer(rootNode);
        } else if (transactionId.equals(TXN_START)) {
            this.auditStart();
        } else if (transactionId.equals(TXN_STOP)) {
            this.auditStop();
        }

        // Persist the message.
        if (amb != null) {
            amb.setAuditSource(this.getAuditSourceId(), null, null);
            amb.persistMessage();
        }
    }

    /**
     * 
     * @return
     */
    private String getAuditSourceId() {
        return this.hostAddress + "@" + this.actorType + "_" + AUDIT_SRC_SUFFIX;
    }

    /**
     *
     */
    private void auditStart() {
        CodedValueType eventId = this.getCodedValueType("110100", DCM, APL_ACTV);
        CodedValueType eventType = this.getCodedValueType("110120", DCM, APL_STRT);
        amb = new AuditMessageBuilder(null, null, eventId, eventType, "E", "0");
        CodedValueType roleIdCode = this.getCodedValueType("110150", "DCM", "Application");
        amb.setActiveParticipant(
                "root", /* userId */
                this.pid, /* altnerateuserId */
                null, /* userName */
                "false", /* userIsRequestor */
                roleIdCode, /* roleIdCode */
                "2", /* networkAccessPointTypeCode (1 = hostname, 2 = IP Address) */
                this.hostAddress);  /* networkAccessPointId  */
    }

    /**
     * 
     */
    private void auditStop() {
        CodedValueType eventId = this.getCodedValueType("110100", DCM, APL_ACTV);
        CodedValueType eventType = this.getCodedValueType("110121", DCM, APL_STP);
        amb = new AuditMessageBuilder(null, null, eventId, eventType, "E", "0");
        CodedValueType roleIdCode = this.getCodedValueType("110150", "DCM", "Application");
        amb.setActiveParticipant(
                "root", /* userId */
                this.pid, /* altnerateuserId */
                null, /* userName */
                "false", /* userIsRequestor */
                roleIdCode, /* roleIdCode */
                "2", /* networkAccessPointTypeCode (1 = hostname, 2 = IP Address) */
                this.hostAddress);  /* networkAccessPointId  */
    }

    /**
     * 
     * @param patientId
     * @param homeCommunityId
     * @param queryId
     * @param queryByParameter
     * @param targetEndpoint
     * @param outcome
     * @throws Exception
     */
    public void performAuditCrossGatewayPatientDiscovery(
            String patientId, String homeCommunityId,
            String queryId, String queryByParameter,
            String targetEndpoint, OutcomeIndicator outcome) throws Exception {
        if (!this.performAudit) {
            return;  // Early exit.
        }

        // Prep for audit.
        this.outcome = outcome;
        this.setContextVariables(targetEndpoint);

        if (this.actorType == ActorType.INITIATING_GATEWAY) {
            this.performAuditCGPD_InitiatingGatewaySend(patientId, homeCommunityId, queryId, queryByParameter);
        } else {
            this.performAuditCGPD_RespondingGatewayReceive(patientId, homeCommunityId, queryId, queryByParameter);
        }

        // Persist the message.
        if (amb != null) {
            amb.setAuditSource(this.getAuditSourceId(), null, null);
            amb.persistMessage();
        }
    }

    /**
     *
     * @param rootNode
     * @throws com.vangent.hieos.xutil.exception.XdsInternalException
     * @throws com.vangent.hieos.xutil.exception.MetadataValidationException
     */
    private void performAuditCGPD_RespondingGatewayReceive(
            String patientId, String homeCommunityId,
            String queryId, String queryByParameter) throws UnsupportedEncodingException {
        // Event ID and Event Type:
        CodedValueType eventId = this.getCodedValueType("110112", "DCM", "Query");
        CodedValueType eventType = this.getCodedValueType(transactionId, IHE_TX, CRS_GTWY_PATIENT_DISCOVERY);
        amb = new AuditMessageBuilder(null, null, eventId, eventType, "E", this.outcome.toString());

        // Source (Initiating Gateway):
        CodedValueType roleIdCode = this.getCodedValueType("110153", "DCM", "Source");
        amb.setActiveParticipant(
                this.replyTo, /* userId  */
                null, /* alternateUserId */
                this.userName, /* userName */
                "true", /* userIsRequestor */
                roleIdCode, /* roleIdCode */
                "2", /* networkAccessPointTypeCode (1 = hostname, 2 = IP Address) */
                this.fromAddress); /* networkAccessPointId */

        // Destination (Responding Gateway):
        roleIdCode = this.getCodedValueType("110152", "DCM", "Destination");
        amb.setActiveParticipant(
                this.endpoint, /* userId  */
                this.pid, /* alternateUserId */
                null, /* userName */
                "false", /* userIsRequestor */
                roleIdCode, /* roleIdCode */
                "2", /* networkAccessPointTypeCode (1 = hostname, 2 = IP Address) */
                this.hostAddress); /* networkAccessPointId */


        // Patient ID:
        CodedValueType participantObjectIdentifier = this.getCodedValueType("2", "RFC-3881", "Patient Number");
        amb.setParticipantObject(
                "1", /* participantObjectTypeCode */
                "1", /* participantObjectTypeCodeRole */
                null, /* participantObjectDataLifeCycle */
                participantObjectIdentifier, /* participantIDTypeCode */
                null, /* participantObjectSensitivity */
                patientId, /* participantObjectId */
                null, /* participantObjectName */
                null, /* participantObjectQuery */
                null, /* participantObjectDetailName */
                null); /* participantObjectDetailValue */

        byte[] queryBase64Bytes = Base64.encodeBase64(queryByParameter.getBytes());
        // Stored Query ID:
        participantObjectIdentifier = this.getCodedValueType(transactionId, IHE_TX, CRS_GTWY_PATIENT_DISCOVERY);
        amb.setParticipantObject(
                "2", /* participantObjectTypeCode */
                "24", /* participantObjectTypeCodeRole */
                null, /* participantObjectDataLifeCycle */
                participantObjectIdentifier, /* participantIDTypeCode */
                null, /* participantObjectSensitivity */
                queryId, /* participantObjectId */
                homeCommunityId, /* participantObjectName */
                queryBase64Bytes, /* participantObjectQuery */
                null, /* participantObjectDetailName */
                null); /* participantObjectDetailValue */
    }

    /**
     * 
     * @param patientId
     * @param homeCommunityId
     * @param queryId
     * @param queryByParameter
     * @throws UnsupportedEncodingException
     * @throws MalformedURLException
     */
    private void performAuditCGPD_InitiatingGatewaySend(
            String patientId, String homeCommunityId,
            String queryId, String queryByParameter) throws UnsupportedEncodingException, MalformedURLException {
        // Event ID and Event Type:
        CodedValueType eventId = this.getCodedValueType("110112", "DCM", "Query");
        String displayName = CRS_GTWY_PATIENT_DISCOVERY;
        
        CodedValueType eventType = this.getCodedValueType(transactionId, IHE_TX, displayName);
        amb = new AuditMessageBuilder(null, null, eventId, eventType, "E", this.outcome.toString());

        // Source (Document Consumer / Gateway):
        CodedValueType roleIdCode = this.getCodedValueType("110153", "DCM", "Source");
        amb.setActiveParticipant(
                this.endpoint, /* userId  */
                this.pid, /* alternateUserId */
                this.userName, /* userName */
                "true", /* userIsRequestor */
                roleIdCode, /* roleIdCode */
                "2", /* networkAccessPointTypeCode (1 = hostname, 2 = IP Address) */
                this.hostAddress); /* networkAccessPointId */

        // Destination (Registry / Gateway):
        roleIdCode = this.getCodedValueType("110152", "DCM", "Destination");
        URL url = new URL(this.targetEndpoint);
        amb.setActiveParticipant(
                this.targetEndpoint, /* userId  */
                null, /* alternateUserId */
                null, /* userName */
                "false", /* userIsRequestor */
                roleIdCode, /* roleIdCode */
                "2", /* networkAccessPointTypeCode (1 = hostname, 2 = IP Address) */
                url.getHost()); /* networkAccessPointId */
       
        byte[] queryBase64Bytes = Base64.encodeBase64(queryByParameter.getBytes());

        // Patient ID:
        CodedValueType participantObjectIdentifier = this.getCodedValueType("2", "RFC-3881", "Patient Number");
        amb.setParticipantObject(
                "1", /* participantObjectTypeCode */
                "1", /* participantObjectTypeCodeRole */
                null, /* participantObjectDataLifeCycle */
                participantObjectIdentifier, /* participantIDTypeCode */
                null, /* participantObjectSensitivity */
                patientId, /* participantObjectId */
                null, /* participantObjectName */
                null, /* participantObjectQuery */
                null, /* participantObjectDetailName */
                null); /* participantObjectDetailValue */

        // Stored Query ID:
        participantObjectIdentifier = this.getCodedValueType(transactionId, IHE_TX, displayName);
        amb.setParticipantObject(
                "2", /* participantObjectTypeCode */
                "24", /* participantObjectTypeCodeRole */
                null, /* participantObjectDataLifeCycle */
                participantObjectIdentifier, /* participantIDTypeCode */
                null, /* participantObjectSensitivity */
                queryId, /* participantObjectId */
                homeCommunityId, /* participantObjectName */
                queryBase64Bytes, /* participantObjectQuery */
                null, /* participantObjectDetailName */
                null); /* participantObjectDetailValue */
    }

    /**
     *
     * @param rootNode
     * @throws com.vangent.hieos.xutil.exception.MetadataValidationException
     * @throws com.vangent.hieos.xutil.exception.MetadataException
     */
    private void auditProvideAndRegisterDocumentSetbToRepository(OMElement rootNode) throws MetadataValidationException, MetadataException {
        // Event ID and Type:
        CodedValueType eventId = this.getCodedValueType("110107", "DCM", "Import");
        CodedValueType eventType = this.getCodedValueType(this.transactionId, IHE_TX, PROVD_N_REG_DOC_SET_B);
        amb = new AuditMessageBuilder(null, null, eventId, eventType, "C", this.outcome.toString());

        // Source (Document Source):
        CodedValueType roleIdCode = this.getCodedValueType("110153", "DCM", "Source");
        amb.setActiveParticipant(
                this.replyTo, /* userId */
                null, /* alternateUserId */
                this.userName, /* userName */
                "true", /* userIsRequestor */
                roleIdCode, /* roleIdCode */
                "2", /* networkAccessPointTypeCode (1 = hostname, 2 = IP Address) */
                this.fromAddress); /* networkAccessPointId */

        // Destination (Repository):
        roleIdCode = this.getCodedValueType("110152", "DCM", "Destination");
        amb.setActiveParticipant(
                this.endpoint, /* userId */
                this.pid, /* altnerateuserId */
                null, /* userName */
                "false", /* userIsRequestor */
                roleIdCode, /* roleIdCode */
                "2", /* networkAccessPointTypeCode (1 = hostname, 2 = IP Address) */
                this.hostAddress);  /* networkAccessPointId  */

        // Metadata variables:
        Metadata m = new Metadata(rootNode);
        String patientId = m.getSubmissionSetPatientId();
        String submissionSetId = m.getSubmissionSetUniqueId();

        // Patient ID:
        CodedValueType participantObjectIdentifier = this.getCodedValueType("2", "RFC-3881", "Patient Number");
        amb.setParticipantObject(
                "1", /* participantObjectTypeCode */
                "1", /* participantObjectTypeCodeRole */
                null, /* participantObjectDataLifeCycle */
                participantObjectIdentifier, /* participantIDTypeCode */
                null, /* participantObjectSensitivity */
                patientId, /* participantObjectId */
                null, /* participantObjectName */
                null, /* participantObjectQuery */
                null, /* participantObjectDetailName */
                null); /* participantObjecxtDetailValue */

        // Submission Set:
        participantObjectIdentifier = this.getCodedValueType(MetadataSupport.XDSSubmissionSet_classification_uuid, IHE_XDS_MDT, "submission set classificationNode");
        amb.setParticipantObject(
                "2", /* participantObjectTypeCode */
                "20", /* participantObjectTypeCodeRole */
                null, /* participantObjectDataLifeCycle */
                participantObjectIdentifier, /* participantIDTypeCode */
                null, /* participantObjectSensitivity */
                submissionSetId, /* participantObjectId */
                null, /* participantObjectName */
                null, /* participantObjectQuery */
                null, /* participantObjectDetailName */
                null); /* participantObjecxtDetailValue */
    }

    /**
     * 
     * @param rootNode
     * @param successFlag
     * @throws com.vangent.hieos.xutil.exception.MetadataValidationException
     * @throws com.vangent.hieos.xutil.exception.MetadataException
     */
    private void auditRegisterDocumentSetbFromRepository(OMElement rootNode) throws MetadataValidationException, MetadataException, MalformedURLException {
        // Event ID and Type:
        CodedValueType eventId = this.getCodedValueType("110106", "DCM", "Export");
        CodedValueType eventType = this.getCodedValueType(transactionId, IHE_TX, REG_DOC_SET);
        amb = new AuditMessageBuilder(null, null, eventId, eventType, "R", this.outcome.toString());

        // Source (Repository):
        CodedValueType roleIdCode = this.getCodedValueType("110153", "DCM", "Source");
        amb.setActiveParticipant(
                this.endpoint, /* userId */
                this.pid, /* alternateUserId */
                null, /* userName */
                "true", /* userIsRequestor */
                roleIdCode, /* roleIdCode */
                "2", /* networkAccessPointTypeCode (1 = hostname, 2 = IP Address) */
                this.hostAddress); /* networkAccessPointId */

        // Destination (Registry):
        roleIdCode = this.getCodedValueType("110152", "DCM", "Destination");
        URL url = new URL(this.targetEndpoint);
        amb.setActiveParticipant(
                this.targetEndpoint, /* userId */
                null, /* alternateUserId */
                null, /* userName */
                "false", /* userIsRequestor */
                roleIdCode, /* roleIdCode */
                "2", /* networkAccessPointTypeCode (1 = hostname, 2 = IP Address) */
                url.getHost()); /* networkAccessPointId */

        // Metadata variables:
        Metadata m = new Metadata(rootNode);
        String patientId = m.getSubmissionSetPatientId();
        String submissionSetId = m.getSubmissionSetUniqueId();

        // Patient ID:
        CodedValueType participantObjectIdentifier = this.getCodedValueType("2", "RFC-3881", "Patient Number");
        amb.setParticipantObject(
                "1", /* participantObjectTypeCode */
                "1", /* participantObjectTypeCodeRole */
                null, /* participantObjectDataLifeCycle */
                participantObjectIdentifier, /* participantIDTypeCode */
                null, /* participantObjectSensitivity */
                patientId, /* participantObjectId */
                null, /* participantObjectName */
                null, /* participantObjectQuery */
                null, /* participantObjectDetailName */
                null); /* participantObjecxtDetailValue */

        // Submission Set:
        participantObjectIdentifier = this.getCodedValueType(MetadataSupport.XDSSubmissionSet_classification_uuid, IHE_XDS_MDT, "submission set classificationNode");
        amb.setParticipantObject(
                "2", /* participantObjectTypeCode */
                "20", /* participantObjectTypeCodeRole */
                null, /* participantObjectDataLifeCycle */
                participantObjectIdentifier, /* participantIDTypeCode */
                null, /* participantObjectSensitivity */
                submissionSetId, /* participantObjectId */
                null, /* participantObjectName */
                null, /* participantObjectQuery */
                null, /* participantObjectDetailName */
                null); /* participantObjecxtDetailValue */
    }

    /**
     * 
     * @param rootNode
     * @throws com.vangent.hieos.xutil.exception.MetadataException
     * @throws com.vangent.hieos.xutil.exception.MetadataValidationException
     */
    private void auditRegisterDocumentSetbToRegistry(OMElement rootNode) throws MetadataException, MetadataValidationException {
        // Event ID and Type:
        CodedValueType eventId = this.getCodedValueType("110107", "DCM", "Import");
        CodedValueType eventType = this.getCodedValueType(transactionId, IHE_TX, REG_DOC_SET);
        amb = new AuditMessageBuilder(null, null, eventId, eventType, "C", this.outcome.toString());

        // Source (Repository / Document Source):
        CodedValueType roleIdCode = this.getCodedValueType("110153", "DCM", "Source");
        amb.setActiveParticipant(
                this.replyTo, /* userId */
                null, /* alternateUserId */
                this.userName, /* userName */
                "true", /* userIsRequestor */
                roleIdCode, /* roleIdCode */
                "2", /* networkAccessPointTypeCode (1 = hostname, 2 = IP Address) */
                this.fromAddress); /* networkAccessPointId */

        // Destination (Registry):
        roleIdCode = this.getCodedValueType("110152", "DCM", "Destination");
        amb.setActiveParticipant(
                this.endpoint, /* userId */
                this.pid, /* alternateUserId */
                null, /* userName */
                "false", /* userIsRequestor */
                roleIdCode, /* roleIdCode */
                "2", /* networkAccessPointTypeCode (1 = hostname, 2 = IP Address) */
                this.hostAddress); /* networkAccessPointId */

        // Metadata variables:
        Metadata m = new Metadata(rootNode);
        String patientId = m.getSubmissionSetPatientId();
        String submissionSetId = m.getSubmissionSetUniqueId();

        // Patient ID:
        CodedValueType participantObjectIdentifier = this.getCodedValueType("2", "RFC-3881", "Patient Number");
        amb.setParticipantObject(
                "1", /* participantObjectTypeCode */
                "1", /* participantObjectTypeCodeRole */
                null, /* participantObjectDataLifeCycle */
                participantObjectIdentifier, /* participantIDTypeCode */
                null, /* participantObjectSensitivity */
                patientId, /* participantObjectId */
                null, /* participantObjectName */
                null, /* participantObjectQuery */
                null, /* participantObjectDetailName */
                null); /* participantObjecxtDetailValue */

        // Submission Set:
        participantObjectIdentifier = this.getCodedValueType(MetadataSupport.XDSSubmissionSet_classification_uuid, IHE_XDS_MDT, "submission set classificationNode");
        amb.setParticipantObject(
                "2", /* participantObjectTypeCode */
                "20", /* participantObjectTypeCodeRole */
                null, /* participantObjectDataLifeCycle */
                participantObjectIdentifier, /* participantIDTypeCode */
                null, /* participantObjectSensitivity */
                submissionSetId, /* participantObjectId */
                null, /* participantObjectName */
                null, /* participantObjectQuery */
                null, /* participantObjectDetailName */
                null); /* participantObjecxtDetailValue */
    }

    /**
     * This transaction is from the perspective of the Repository. The repository is the source
     * for the document and exports the doc to the consumer (destination)
     * @param rootNode
     */
    private void auditRetrieveDocumentSetToRepository(OMElement rootNode) {
        // Event ID and Event Type:
        CodedValueType eventId = this.getCodedValueType("110106", "DCM", "Export");
        String displayName;
        if (TXN_ITI43.equals(transactionId)) {
            displayName = RTV_DOC_SET;
        } else {
            displayName = CRS_GTWY_RTV;
        }
        CodedValueType eventType = this.getCodedValueType(transactionId, IHE_TX, displayName);
        amb = new AuditMessageBuilder(null, null, eventId, eventType, "R", this.outcome.toString());

        // Source (Document Repository):
        CodedValueType roleIdCode = this.getCodedValueType("110153", "DCM", "Source");
        amb.setActiveParticipant(
                this.endpoint, /* userId */
                this.pid, /* alternateUserId */
                null, /* userName */
                "false", /* userIsRequestor */
                roleIdCode, /* roleIdCode */
                "2", /* networkAccessPointTypeCode (1 = hostname, 2 = IP Address) */
                this.hostAddress); /* networkAccessPointId */

        // Destination (Document Consumer):
        roleIdCode = this.getCodedValueType("110152", "DCM", "Destination");
        amb.setActiveParticipant(
                this.replyTo, /* userId */
                null, /* alternateUserId */
                this.userName, /* userName */
                "true", /* userIsRequestor */
                roleIdCode, /* roleIdCode */
                "2", /* networkAccessPointTypeCode (1 = hostname, 2 = IP Address) */
                this.fromAddress); /* networkAccessPointId */

        // Document URIs:
        CodedValueType participantObjectIdentifier = this.getCodedValueType("9", "RFC-3881", "Report Number");
        for (OMElement doc_request : MetadataSupport.childrenWithLocalName(rootNode, "DocumentRequest")) {
            String homeCommunityId = null;
            String repositoryId;
            String documentId;
            repositoryId = MetadataSupport.firstChildWithLocalName(doc_request, "RepositoryUniqueId").getText();
            byte[] podval = repositoryId.getBytes();
            documentId = MetadataSupport.firstChildWithLocalName(doc_request, "DocumentUniqueId").getText();
            OMElement homeNode = MetadataSupport.firstChildWithLocalName(doc_request, "HomeCommunityId");
            if (homeNode != null) {
                homeCommunityId = homeNode.getText();
            }
            // Document URI:
            amb.setParticipantObject(
                    "2", /* participantObjectTypeCode */
                    "3", /* participantObjectTypeCodeRole */
                    null, /* participantObjectDataLifeCycle */
                    participantObjectIdentifier, /* participantIDTypeCode */
                    null, /* participantObjectSensitivity */
                    documentId, /* participantObjectId */
                    homeCommunityId, /* participantObjectName */
                    null, /* participantObjectQuery */
                    "Repository Unique Id", /* participantObjectDetailName */
                    podval); /* participantObjectDetailValue */
        }
    }

    /**
     * This transaction is from the perspective of the Consumer. The consumer is the destination of the
     * document and will import the doc from the repository which is the source.
     * @param rootNode
     * @throws com.vangent.hieos.xutil.exception.MetadataValidationException
     * @throws com.vangent.hieos.xutil.exception.MetadataException
     */
    private void auditRetrieveDocumentSetFromConsumer(OMElement rootNode) throws MetadataValidationException, MetadataException, MalformedURLException {
        // Event ID and Event Type:
        CodedValueType eventId = this.getCodedValueType("110107", "DCM", "Import");
        String displayName;
        if (TXN_ITI43.equals(transactionId)) {
            displayName = RTV_DOC_SET;
        } else {
            displayName = CRS_GTWY_RTV;
        }
        CodedValueType eventType = this.getCodedValueType(transactionId, IHE_TX, displayName);
        amb = new AuditMessageBuilder(null, null, eventId, eventType, "C", this.outcome.toString());

        // Source (Document Repository)
        CodedValueType roleIdCode = this.getCodedValueType("110153", "DCM", "Source");
        URL url = new URL(this.targetEndpoint);
        amb.setActiveParticipant(
                this.targetEndpoint, /* userId  */
                null, /* alternateUserId */
                null, /* userName */
                "false", /* userIsRequestor */
                roleIdCode, /* roleIdCode */
                "2", /* networkAccessPointTypeCode (1 = hostname, 2 = IP Address) */
                url.getHost()); /* networkAccessPointId */

        // Destination (Document Consumer):
        roleIdCode = this.getCodedValueType("110152", "DCM", "Destination");
        amb.setActiveParticipant(
                this.endpoint, /* userId */
                this.pid, /* alternateUserId */
                this.userName, /* userName */
                "true", /* userIsRequestor */
                roleIdCode, /* roleIdCode */
                "2", /* networkAccessPointTypeCode (1 = hostname, 2 = IP Address) */
                this.hostAddress); /* networkAccessPointId */

        // Document URIs:
        CodedValueType participantObjectIdentifier = this.getCodedValueType("9", "RFC-3881", "Report Number");
        for (OMElement doc_request : MetadataSupport.childrenWithLocalName(rootNode, "DocumentRequest")) {
            String homeCommunityId = null;
            String repositoryId;
            String documentId;
            repositoryId = MetadataSupport.firstChildWithLocalName(doc_request, "RepositoryUniqueId").getText();
            byte[] podval = repositoryId.getBytes();
            documentId = MetadataSupport.firstChildWithLocalName(doc_request, "DocumentUniqueId").getText();
            OMElement homeNode = MetadataSupport.firstChildWithLocalName(doc_request, "HomeCommunityId");
            if (homeNode != null) {
                homeCommunityId = homeNode.getText();
            }
            // Document URI:
            amb.setParticipantObject(
                    "2", /* participantObjectTypeCode */
                    "3", /* participantObjectTypeCodeRole */
                    null, /* participantObjectDataLifeCycle */
                    participantObjectIdentifier, /* participantIDTypeCode */
                    null, /* participantObjectSensitivity */
                    documentId, /* participantObjectId */
                    homeCommunityId, /* participantObjectName */
                    null, /* participantObjectQuery */
                    "Repository Unique Id", /* participantObjectDetailName */
                    podval); /* participantObjectDetailValue */
        }
    }

    /**
     * 
     * @param rootNode
     * @throws com.vangent.hieos.xutil.exception.XdsInternalException
     * @throws com.vangent.hieos.xutil.exception.MetadataValidationException
     */
    private void auditRegistryStoredQueryToRegistry(OMElement rootNode) throws XdsInternalException, MetadataValidationException, UnsupportedEncodingException {
        // Event ID and Event Type:
        CodedValueType eventId = this.getCodedValueType("110112", "DCM", "Query");
        String displayName;
        if (TXN_ITI18.equals(transactionId)) {
            displayName = REG_STOR_QRY;
        } else {
            displayName = CRS_GTWY_QRY;
        }
        CodedValueType eventType = this.getCodedValueType(transactionId, IHE_TX, displayName);
        amb = new AuditMessageBuilder(null, null, eventId, eventType, "E", this.outcome.toString());

        // Source (Document Consumer):
        CodedValueType roleIdCode = this.getCodedValueType("110153", "DCM", "Source");
        amb.setActiveParticipant(
                this.replyTo, /* userId  */
                null, /* alternateUserId */
                this.userName, /* userName */
                "true", /* userIsRequestor */
                roleIdCode, /* roleIdCode */
                "2", /* networkAccessPointTypeCode (1 = hostname, 2 = IP Address) */
                this.fromAddress); /* networkAccessPointId */

        // Destination (Registry / Gateway):
        roleIdCode = this.getCodedValueType("110152", "DCM", "Destination");
        amb.setActiveParticipant(
                this.endpoint, /* userId  */
                this.pid, /* alternateUserId */
                null, /* userName */
                "false", /* userIsRequestor */
                roleIdCode, /* roleIdCode */
                "2", /* networkAccessPointTypeCode (1 = hostname, 2 = IP Address) */
                this.hostAddress); /* networkAccessPointId */

        // Pull values from metadata.
        String patientId;
        String storedQueryId;
        OMElement adhocQuery = MetadataSupport.firstChildWithLocalName(rootNode, "AdhocQuery");
        storedQueryId = adhocQuery.getAttributeValue(MetadataSupport.id_qname);
        patientId = this.getQueryPatientID(rootNode, storedQueryId);
        String query = rootNode.toString();
        /* BHT: Removed and replaced with line below.
        String queryBase64String = Base64Coder.encodeString(query);  // Convert to base64.
        byte[] queryBase64Bytes = queryBase64String.getBytes();
         */
        byte[] queryBase64Bytes = Base64.encodeBase64(query.getBytes());

        // Patient ID:
        CodedValueType participantObjectIdentifier = this.getCodedValueType("2", "RFC-3881", "Patient Number");
        amb.setParticipantObject(
                "1", /* participantObjectTypeCode */
                "1", /* participantObjectTypeCodeRole */
                null, /* participantObjectDataLifeCycle */
                participantObjectIdentifier, /* participantIDTypeCode */
                null, /* participantObjectSensitivity */
                patientId, /* participantObjectId */
                null, /* participantObjectName */
                null, /* participantObjectQuery */
                null, /* participantObjectDetailName */
                null); /* participantObjectDetailValue */

        // Stored Query ID:
        participantObjectIdentifier = this.getCodedValueType(transactionId, IHE_TX, displayName);
        String homeCommunityId = this.getHomeCommunityId(adhocQuery);
        amb.setParticipantObject(
                "2", /* participantObjectTypeCode */
                "24", /* participantObjectTypeCodeRole */
                null, /* participantObjectDataLifeCycle */
                participantObjectIdentifier, /* participantIDTypeCode */
                null, /* participantObjectSensitivity */
                storedQueryId, /* participantObjectId */
                homeCommunityId, /* participantObjectName */
                queryBase64Bytes, /* participantObjectQuery */
                null, /* participantObjectDetailName */
                null); /* participantObjectDetailValue */
    }

    /**
     * 
     * @param rootNode
     * @throws com.vangent.hieos.xutil.exception.MetadataValidationException
     * @throws com.vangent.hieos.xutil.exception.XdsInternalException
     */
    private void auditRegistryStoredQueryFromConsumer(OMElement rootNode) throws MetadataValidationException, XdsInternalException, MalformedURLException, UnsupportedEncodingException {
        // Event ID and Event Type:
        CodedValueType eventId = this.getCodedValueType("110112", "DCM", "Query");
        String displayName;
        if (TXN_ITI18.equals(transactionId)) {
            displayName = REG_STOR_QRY;
        } else {
            displayName = CRS_GTWY_QRY;
        }
        CodedValueType eventType = this.getCodedValueType(transactionId, IHE_TX, displayName);
        amb = new AuditMessageBuilder(null, null, eventId, eventType, "E", this.outcome.toString());

        // Source (Document Consumer / Gateway):
        CodedValueType roleIdCode = this.getCodedValueType("110153", "DCM", "Source");
        amb.setActiveParticipant(
                this.endpoint, /* userId  */
                this.pid, /* alternateUserId */
                this.userName, /* userName */
                "true", /* userIsRequestor */
                roleIdCode, /* roleIdCode */
                "2", /* networkAccessPointTypeCode (1 = hostname, 2 = IP Address) */
                this.hostAddress); /* networkAccessPointId */

        // Destination (Registry / Gateway):
        roleIdCode = this.getCodedValueType("110152", "DCM", "Destination");
        URL url = new URL(this.targetEndpoint);
        amb.setActiveParticipant(
                this.targetEndpoint, /* userId  */
                null, /* alternateUserId */
                null, /* userName */
                "false", /* userIsRequestor */
                roleIdCode, /* roleIdCode */
                "2", /* networkAccessPointTypeCode (1 = hostname, 2 = IP Address) */
                url.getHost()); /* networkAccessPointId */

        // Pull values from metadata.
        String patientId;
        String storedQueryId;
        OMElement adhocQuery = MetadataSupport.firstChildWithLocalName(rootNode, "AdhocQuery");
        storedQueryId = adhocQuery.getAttributeValue(MetadataSupport.id_qname);
        patientId = this.getQueryPatientID(rootNode, storedQueryId);

        String query = rootNode.toString();
        /* BHT: Removed and replaced with line below.
        String queryBase64String = Base64Coder.encodeString(query);  // Convert to base64.
        byte[] queryBase64Bytes = queryBase64String.getBytes();
         */
        byte[] queryBase64Bytes = Base64.encodeBase64(query.getBytes());


        // Patient ID:
        CodedValueType participantObjectIdentifier = this.getCodedValueType("2", "RFC-3881", "Patient Number");
        amb.setParticipantObject(
                "1", /* participantObjectTypeCode */
                "1", /* participantObjectTypeCodeRole */
                null, /* participantObjectDataLifeCycle */
                participantObjectIdentifier, /* participantIDTypeCode */
                null, /* participantObjectSensitivity */
                patientId, /* participantObjectId */
                null, /* participantObjectName */
                null, /* participantObjectQuery */
                null, /* participantObjectDetailName */
                null); /* participantObjectDetailValue */

        // Stored Query ID:
        participantObjectIdentifier = this.getCodedValueType(transactionId, IHE_TX, displayName);
        String homeCommunityId = this.getHomeCommunityId(adhocQuery);
        amb.setParticipantObject(
                "2", /* participantObjectTypeCode */
                "24", /* participantObjectTypeCodeRole */
                null, /* participantObjectDataLifeCycle */
                participantObjectIdentifier, /* participantIDTypeCode */
                null, /* participantObjectSensitivity */
                storedQueryId, /* participantObjectId */
                homeCommunityId, /* participantObjectName */
                queryBase64Bytes, /* participantObjectQuery */
                null, /* participantObjectDetailName */
                null); /* participantObjectDetailValue */
    }

    /**
     * This is public and does not follow the main audit pattern due to the complexity of the
     * HL7v3 message structure.
     * 
     * @param patientId
     * @param messageId
     * @param updateMode
     * @param successFlag
     * @param sourceIdentity
     * @param sourceIP
     */
    public void auditPatientIdentityFeedToRegistry(String patientId,
            String messageId, boolean updateMode, OutcomeIndicator outcome,
            String sourceIdentity, String sourceIP) {
        if (!this.performAudit) {
            return;  // Early exit.
        }
        // Prep for audit:
        this.outcome = outcome;
        this.setContextVariables(null);

        // Event ID and Type:
        CodedValueType eventId = this.getCodedValueType("110110", "DCM", "Patient Record");
        CodedValueType eventType = this.getCodedValueType(transactionId, IHE_TX, PATIENT_IDENTITY_FEED);
        String eventAction = updateMode ? "U" : "C";
        amb = new AuditMessageBuilder(null, null, eventId, eventType, eventAction, this.outcome.toString());

        // Source (Patient Identity Source):
        CodedValueType roleIdCode = this.getCodedValueType("110153", "DCM", "Source");
        amb.setActiveParticipant(
                sourceIdentity != null ? sourceIdentity : this.replyTo, /* userId */
                null, /* alternateUserId */
                this.userName, /* userName */
                "true", /* userIsRequestor */
                roleIdCode, /* roleIdCode */
                "2", /* networkAccessPointTypeCode (1 = hostname, 2 = IP Address) */
                sourceIP != null ? sourceIP : this.fromAddress); /* networkAccessPointId */

        // Destination (Registry):
        roleIdCode = this.getCodedValueType("110152", "DCM", "Destination");
        amb.setActiveParticipant(
                this.endpoint, /* userId */
                this.pid, /* alternateUserId */
                null, /* userName */
                "false", /* userIsRequestor */
                roleIdCode, /* roleIdCode */
                "2", /* networkAccessPointTypeCode (1 = hostname, 2 = IP Address) */
                this.hostAddress); /* networkAccessPointId */

        // Patient ID:
        byte[] podval = messageId.getBytes();
        CodedValueType participantObjectIdentifier = this.getCodedValueType("2", "RFC-3881", "Patient Number");
        amb.setParticipantObject(
                "1", /* participantObjectTypeCode */
                "1", /* participantObjectTypeCodeRole */
                null, /* participantObjectDataLifeCycle */
                participantObjectIdentifier, /* participantIDTypeCode */
                null, /* participantObjectSensitivity */
                patientId, /* participantObjectId */
                null, /* participantObjectName */
                null, /* participantObjectQuery */
                "Message Identifier", /* participantObjectDetailName */
                podval); /* participantObjectDetailValue */

        // Now, persist the audit message.
        amb.setAuditSource(this.getAuditSourceId(), null, null);
        amb.persistMessage();
    }

    /**
     * 
     * @param queryRequest
     * @return
     */
    private String getHomeCommunityId(OMElement queryRequest) {
        String homeCommunityId = queryRequest.getAttributeValue(MetadataSupport.home_qname);
        if (homeCommunityId == null || homeCommunityId.equals("")) {
            homeCommunityId = null;
        }
        if (homeCommunityId == null) {
            homeCommunityId = "HomeCommunityId not present in request";
        }
        return homeCommunityId;
    }

    /**
     *
     * @param code
     * @param codeSystem
     * @param displayName
     * @return
     */
    private CodedValueType getCodedValueType(String code, String codeSystem, String displayName) {
        CodedValueType codeValueType = new CodedValueType();
        codeValueType.setCode(code);
        codeValueType.setCodeSystem(codeSystem);
        codeValueType.setCodeSystemName(codeSystem);
        codeValueType.setDisplayName(displayName);
        return codeValueType;
    }

    /**
     *
     * @param request
     * @param queryRequest
     * @return
     * @throws com.vangent.hieos.xutil.exception.XdsInternalException
     */
    private String getQueryPatientID(OMElement request, String queryId) {
        if (queryId == null) {
            return "QueryId not known";  // Early exit (FIXME).
        }
        // Parse the query parameters.
        ParamParser parser = new ParamParser();
        SqParams params = null;
        try {
            params = parser.parse(request);
        } catch (MetadataValidationException ex) {
            logger.error("Could not parse stored query in ATNA", ex);
        } catch (XdsInternalException ex) {
            logger.error("Could not parse stored query in ATNA", ex);
        }

        if (params == null) {
            return "Query Parameters could not be parsed";  // Early exit.
        }
        String patientId = null;
        if (queryId.equals(MetadataSupport.SQ_FindDocuments)) {
            // $XDSDocumentEntryPatientId
            patientId = params.getStringParm("$XDSDocumentEntryPatientId");
        } else if (queryId.equals(MetadataSupport.SQ_FindFolders)) {
            // $XDSFolderPatientId
            patientId = params.getStringParm("$XDSFolderPatientId");
        } else if (queryId.equals(MetadataSupport.SQ_FindSubmissionSets)) {
            // $XDSSubmissionSetPatientId
            patientId = params.getStringParm("$XDSSubmissionSetPatientId");
        } else if (queryId.equals(MetadataSupport.SQ_GetAll)) {
            // FIXME: NOT IMPLEMENTED [NEED TO FIGURE OUT WHAT TO PULL OUT HERE.
            return "SQ_GetAll not implemented";
        }
        if (patientId == null) {
            return "PatientId not present on request";
        }
        return patientId;
    }

    /**
     * 
     * @return
     */
    private MessageContext getCurrentMessageContext() {
        return MessageContext.getCurrentMessageContext();
    }

    /**
     *
     */
    private void setContextVariables(String targetEndpoint) {
        // Set host address:
        try {
            InetAddress addr;
            addr = InetAddress.getLocalHost();
            this.hostAddress = addr.getHostAddress();
        } catch (UnknownHostException e) {
            this.hostAddress = null;
            logger.error("Exception in XATNALogger", e);
        }
        // Set target endpoint and process id:
        this.targetEndpoint = targetEndpoint;
        this.pid = ManagementFactory.getRuntimeMXBean().getName();
        MessageContext messageContext = this.getCurrentMessageContext();
        if (messageContext != null) {

            // Set current endpoint:
            try {
                this.endpoint = messageContext.getTo().toString();
            } catch (Exception e) {
                this.endpoint = null;
                logger.error("Exception in XATNALogger", e);
            }

            // Set from address:
            try {
                this.fromAddress = (String) messageContext.getProperty(MessageContext.REMOTE_ADDR);
            } catch (Exception e) {
                this.fromAddress = null;
                logger.error("Exception in XATNALogger", e);
            }

            // Set replyTo address:
            try {
                org.apache.axis2.addressing.EndpointReference replyToEndpointRef = messageContext.getReplyTo();
                this.replyTo = replyToEndpointRef != null ? replyToEndpointRef.toString() : null;
            } catch (Exception e) {
                this.replyTo = null;
                logger.error("Exception in XATNALogger", e);
            }

            // Set userName on request (if available):
            this.userName = this.getUserNameFromRequest();

        } else {
            this.endpoint = null;
            this.fromAddress = null;
            this.replyTo = null;
        }
        /*
        // The endpoint for the current web service running.
        AxisEndpoint axisEndPoint = (AxisEndpoint) messageContext.getProperty("endpoint");
        this.endpoint = axisEndPoint.getEndpointURL();

        // IP Address from the caller.
        this.fromAddress = (String) messageContext.getProperty("REMOTE_ADDR");
         */

        // DEBUG:
            /*
        System.out.println("--- AUDIT VARIABLES ---");
        System.out.println("hostname: " + this.hostname);
        System.out.println("pid:" + this.pid);
        System.out.println("endpoint: " + this.endpoint);
        System.out.println("fromAddress: " + this.fromAddress);
        System.out.println("getFrom().getAddress(): " + messageContext.getFrom().getAddress().toString());
        System.out.println("replyTo:" + messageContext.getReplyTo().toString());
        System.out.println("replyToAddress: " + messageContext.getReplyTo().getAddress().toString());
        System.out.println("-----------------------");
         */
        //logger.error("Exception in XATNALogger", e);
    }

    /**
     *
     * @return
     */
    private String getUserNameFromRequest() {
        XServiceProvider xServiceProvider = new XServiceProvider(null);
        return xServiceProvider.getUserNameFromRequest(this.getCurrentMessageContext());
    }
}
