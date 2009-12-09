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
package com.vangent.hieos.services.xds.registry.serviceimpl;

import com.vangent.hieos.xutil.exception.XdsValidationException;
import com.vangent.hieos.xutil.metadata.structure.MetadataSupport;
import com.vangent.hieos.services.xds.registry.transactions.AdhocQueryRequest;
import com.vangent.hieos.xutil.services.framework.XAbstractService;
import com.vangent.hieos.services.xds.registry.transactions.SubmitObjectsRequest;
import com.vangent.hieos.services.xds.registry.transactions.RegistryPatientIdentityFeed;

import org.apache.log4j.Logger;
import org.apache.axis2.AxisFault;
import org.apache.axis2.addressing.EndpointReference;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMNamespace;

// Axis2 LifeCycle support:
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.description.AxisService;

import com.vangent.hieos.xutil.atna.XATNALogger;
import com.vangent.hieos.xutil.exception.XdsInternalException;
import com.vangent.hieos.xutil.soap.SoapActionFactory;
import com.vangent.hieos.xutil.xconfig.XConfig;
import com.vangent.hieos.xutil.xconfig.XConfigRegistry;

public class XDSbRegistry extends XAbstractService {
    private final static Logger logger = Logger.getLogger(XDSbRegistry.class);

    /**
     *
     * @param sor
     * @return
     * @throws org.apache.axis2.AxisFault
     */
    public OMElement SubmitObjectsRequest(OMElement sor) throws AxisFault {
        try {
            OMElement startup_error = beginTransaction(getRTransactionName(sor), sor, XAbstractService.ActorType.REGISTRY);
            if (startup_error != null) {
                return startup_error;
            }
            validateWS();
            validateNoMTOM();
            validateSubmitTransaction(sor);
            SubmitObjectsRequest s = new SubmitObjectsRequest(log_message, getMessageContext());
            OMElement result = s.submitObjectsRequest(sor);
            endTransaction(s.getStatus());
            return result;
        } catch (Exception e) {
            return endTransaction(sor, e, XAbstractService.ActorType.REGISTRY, "");
        }
    }

    /**
     *
     * @param ahqr
     * @return
     * @throws org.apache.axis2.AxisFault
     */
    public OMElement AdhocQueryRequest(OMElement ahqr) throws AxisFault {
        OMElement startup_error = beginTransaction(getRTransactionName(ahqr), ahqr, XAbstractService.ActorType.REGISTRY);
        if (startup_error != null) {
            return startup_error;
        }
        AdhocQueryRequest a = new AdhocQueryRequest(this.getRegistryXConfigName(), log_message, getMessageContext());
        try {
            validateWS();
            validateNoMTOM();
            validateQueryTransaction(ahqr);
        } catch (Exception e) {
            return endTransaction(ahqr, e, XAbstractService.ActorType.REGISTRY, "");
        }
        a.setServiceName(this.getServiceName());
        a.setIsMPQRequest(this.isMPQRequest());
        OMElement result = a.adhocQueryRequest(ahqr);
        endTransaction(a.getStatus());
        return result;
    }

    /**
     * Returns true if this is an MPQ SOAP request.  Otherwise, returns false.
     * @return Boolean result indicating MPQ SOAP request status.
     */
    private boolean isMPQRequest() {
        boolean isMPQ = false;
        String soapAction = this.getSOAPAction();
        if (soapAction.equals(SoapActionFactory.XDSB_REGISTRY_MPQ_ACTION)) {
            isMPQ = true;
        }
        return isMPQ;
    }

    // Added (BHT): Patient Identity Feed:
    /**
     * Patient Registry Record Added
     *
     * @param PRPA_IN201301UV02_Message
     * @return
     * @throws org.apache.axis2.AxisFault
     */
    public OMElement DocumentRegistry_PRPA_IN201301UV02(OMElement PRPA_IN201301UV02_Message) throws AxisFault {
        OMElement startup_error = beginTransaction("PIDFEED.Add", PRPA_IN201301UV02_Message, XAbstractService.ActorType.REGISTRY);
        if (startup_error != null) {
            return startup_error;
        }
        logger.info("*** PID Feed: Patient Registry Record Added ***");
        logger.info("*** XConfig Registry Name = " + this.getRegistryXConfigName());
        RegistryPatientIdentityFeed rpif = new RegistryPatientIdentityFeed(this.getRegistryXConfigName(), log_message);
        OMElement result = rpif.run(PRPA_IN201301UV02_Message, RegistryPatientIdentityFeed.MessageType.PatientRegistryRecordAdded);
        this.forceAnonymousReply();  // BHT (FIXME)
        endTransaction(true /* success */);
        return result;
    }

    /**
     * Patient Registry Record Updated
     *
     * @param PRPA_IN201302UV02_Message
     * @return
     * @throws org.apache.axis2.AxisFault
     */
    public OMElement DocumentRegistry_PRPA_IN201302UV02(OMElement PRPA_IN201302UV02_Message) throws AxisFault {
        OMElement startup_error = beginTransaction("PIDFEED.Update", PRPA_IN201302UV02_Message, XAbstractService.ActorType.REGISTRY);
        if (startup_error != null) {
            return startup_error;
        }
        logger.info("*** PID Feed: Patient Registry Record Updated ***");
        logger.info("*** XConfig Registry Name = " + this.getRegistryXConfigName());
        RegistryPatientIdentityFeed rpif = new RegistryPatientIdentityFeed(this.getRegistryXConfigName(), log_message);
        OMElement result = rpif.run(PRPA_IN201302UV02_Message, RegistryPatientIdentityFeed.MessageType.PatientRegistryRecordUpdated);
        this.forceAnonymousReply();  // BHT (FIXME)
        endTransaction(true /* success */);
        return result;
    }

    /**
     * Patient Registry Duplicates Resolved
     *
     * @param PRPA_IN201304UV02_Message
     * @return
     * @throws org.apache.axis2.AxisFault
     */
    public OMElement DocumentRegistry_PRPA_IN201304UV02(OMElement PRPA_IN201304UV02_Message) throws AxisFault {
        OMElement startup_error = beginTransaction("PIDFEED.Merge", PRPA_IN201304UV02_Message, XAbstractService.ActorType.REGISTRY);
        if (startup_error != null) {
            return startup_error;
        }
        logger.info("*** PID Feed: Patient Registry Duplicates Resolved (MERGE) ***");
        logger.info("*** XConfig Registry Name = " + this.getRegistryXConfigName());
        RegistryPatientIdentityFeed rpif = new RegistryPatientIdentityFeed(this.getRegistryXConfigName(), log_message);
        OMElement result = rpif.run(PRPA_IN201304UV02_Message, RegistryPatientIdentityFeed.MessageType.PatientRegistryDuplicatesResolved);
        this.forceAnonymousReply();  // BHT (FIXME)
        endTransaction(true /* success */);
        return result;
    }

    /**
     * 
     * @param sor
     * @throws XdsValidationException
     */
    private void validateSubmitTransaction(OMElement sor)
            throws XdsValidationException {
        OMNamespace ns = sor.getNamespace();
        String ns_uri = ns.getNamespaceURI();
        if (ns_uri == null || !ns_uri.equals(MetadataSupport.ebLcm3.getNamespaceURI())) {
            throw new XdsValidationException("Invalid namespace on " + sor.getLocalName() + " (" + ns_uri + ")");
        }
        String type = getRTransactionName(sor);
        if (!type.startsWith("SubmitObjectsRequest")) {
            throw new XdsValidationException("Only SubmitObjectsRequest is acceptable on this endpoint, found " + sor.getLocalName());
        }
    }

    /**
     *
     * @param sor
     * @throws com.vangent.hieos.xutil.exception.XdsValidationException
     */
    private void validateQueryTransaction(OMElement sor)
            throws XdsValidationException {
        OMNamespace ns = sor.getNamespace();
        String ns_uri = ns.getNamespaceURI();
        if (ns_uri == null || !ns_uri.equals(MetadataSupport.ebQns3.getNamespaceURI())) {
            throw new XdsValidationException("Invalid namespace on " + sor.getLocalName() + " (" + ns_uri + ")");
        }
        String type = getRTransactionName(sor);
        if (!this.isSQ(sor)) {
            throw new XdsValidationException("Only StoredQuery is acceptable on this endpoint");
        }
        //new StoredQueryRequestSoapValidator(getXdsVersion(), getMessageContext()).runWithException();
    }

    /**
     *
     * @param ahqr
     * @return
     */
    protected String getRTransactionName(OMElement ahqr) {
        OMElement ahq = MetadataSupport.firstChildWithLocalName(ahqr, "AdhocQuery");
        if (ahq != null) {
            return "SQ.b";
        } else if (ahqr.getLocalName().equals("SubmitObjectsRequest")) {
            return "SubmitObjectsRequest.b";
        } else {
            return "Unknown";
        }
    }

    /**
     *
     * @param ahqr
     * @return
     */
    private boolean isSQ(OMElement ahqr) {
        return MetadataSupport.firstChildWithLocalName(ahqr, "AdhocQuery") != null;
    }

    /**
     *
     * @param ahqr
     * @return
     */
    /*
    private boolean isSQL(OMElement ahqr) {
    return MetadataSupport.firstChildWithLocalName(ahqr, "SQLQuery") != null;
    }*/
    /**
     *
     */
    private void forceAnonymousReply() {
        // FIXME: Need to look at why we need to do this at all.
        EndpointReference epr = new EndpointReference("http://www.w3.org/2005/08/addressing/anonymous");
        try {
            this.getResponseMessageContext().setTo(epr);
        } catch (AxisFault ex) {
            logger.error("Unable to force anonymous reply", ex);
        }
    }

    /**
     * Return the name of the registry in XConfig.
     *
     * @return  The logical name stored in XConfig that represents the registry.
     */
    private String getRegistryXConfigName() {
        return (String) this.getMessageContext().getParameter("XConfigName").getValue();
    }

    // BHT (ADDED Axis2 LifeCycle methods):
    /**
     * This will be called during the deployment time of the service.
     * Irrespective of the service scope this method will be called
     */
    @Override
    public void startUp(ConfigurationContext configctx, AxisService service) {
        logger.info("RegistryB::startUp()");
        this.ATNAlogStart(XATNALogger.ActorType.REGISTRY);
    }

    /**
     * This will be called during the system shut down time. Irrespective
     * of the service scope this method will be called
     */
    @Override
    public void shutDown(ConfigurationContext configctx, AxisService service) {
        logger.info("RegistryB::shutDown()");
        this.ATNAlogStop(XATNALogger.ActorType.REGISTRY);
    }
}
