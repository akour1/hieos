/*
 * This code is subject to the HIEOS License, Version 1.0
 *
 * Copyright(c) 2011 Vangent, Inc.  All rights reserved.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.vangent.hieos.services.pixpdq.empi.impl.base;

import com.vangent.hieos.empi.codes.CodesConfig.CodedType;
import com.vangent.hieos.empi.config.EMPIConfig;
import com.vangent.hieos.empi.config.IdentitySourceConfig;
import com.vangent.hieos.empi.exception.EMPIException;
import com.vangent.hieos.empi.persistence.PersistenceManager;
import com.vangent.hieos.hl7v3util.model.subject.DeviceInfo;
import com.vangent.hieos.hl7v3util.model.subject.Subject;
import com.vangent.hieos.hl7v3util.model.subject.SubjectCitizenship;
import com.vangent.hieos.hl7v3util.model.subject.SubjectIdentifier;
import com.vangent.hieos.hl7v3util.model.subject.SubjectIdentifierDomain;
import com.vangent.hieos.hl7v3util.model.subject.SubjectLanguage;
import com.vangent.hieos.hl7v3util.model.subject.SubjectPersonalRelationship;
import com.vangent.hieos.services.pixpdq.empi.api.EMPINotification;
import com.vangent.hieos.xutil.xconfig.XConfigActor;
import java.util.List;
import org.apache.log4j.Logger;

/**
 *
 * @author Bernie Thuman
 */
public class BaseHandler {

    private static final Logger logger = Logger.getLogger(BaseHandler.class);
    private XConfigActor configActor = null;
    private PersistenceManager persistenceManager = null;
    private DeviceInfo senderDeviceInfo = null;

    /**
     * 
     * @param configActor
     * @param persistenceManager
     * @param senderDeviceInfo
     */
    protected BaseHandler(XConfigActor configActor,
            PersistenceManager persistenceManager, DeviceInfo senderDeviceInfo) {
        this.configActor = configActor;
        this.persistenceManager = persistenceManager;
        this.senderDeviceInfo = senderDeviceInfo;
    }

    /**
     *
     * @return
     */
    protected XConfigActor getConfigActor() {
        return this.configActor;
    }

    /**
     *
     * @return
     */
    public DeviceInfo getSenderDeviceInfo() {
        return senderDeviceInfo;
    }

    /**
     * 
     * @return
     */
    protected PersistenceManager getPersistenceManager() {
        return persistenceManager;
    }

    /**
     *
     * @param subject
     * @throws EMPIException
     */
    protected void validateSubjectIdentifierDomains(Subject subject) throws EMPIException {
        PersistenceManager pm = this.getPersistenceManager();
        // Validate identifier domains assocated with the subject's identifiers.
        List<SubjectIdentifier> subjectIdentifiers = subject.getSubjectIdentifiers();
        for (SubjectIdentifier subjectIdentifier : subjectIdentifiers) {
            boolean subjectIdentifierDomainExists = pm.doesSubjectIdentifierDomainExist(subjectIdentifier);
            if (!subjectIdentifierDomainExists) {
                SubjectIdentifierDomain subjectIdentifierDomain = subjectIdentifier.getIdentifierDomain();
                throw new EMPIException(
                        subjectIdentifierDomain.getUniversalId()
                        + " is not a known identifier domain",
                        EMPIException.ERROR_CODE_UNKNOWN_KEY_IDENTIFIER);
            }
        }
    }

    /**
     *
     * @param subject
     * @throws EMPIException
     */
    protected void validateSubjectCodes(Subject subject) throws EMPIException {
        EMPIConfig empiConfig = EMPIConfig.getInstance();
        if (!empiConfig.isValidateCodesEnabled()) {
            return;  // Early exit! --  Not doing validation.
        }

        // Validate individual code values.
        empiConfig.validateCode(subject.getGender(), CodedType.GENDER);
        empiConfig.validateCode(subject.getMaritalStatus(), CodedType.MARITAL_STATUS);
        empiConfig.validateCode(subject.getReligiousAffiliation(), CodedType.RELIGIOUS_AFFILIATION);
        empiConfig.validateCode(subject.getRace(), CodedType.RACE);
        empiConfig.validateCode(subject.getEthnicGroup(), CodedType.ETHNIC_GROUP);

        // Validate personal relationship codes.
        List<SubjectPersonalRelationship> subjectPersonalRelationships = subject.getSubjectPersonalRelationships();
        for (SubjectPersonalRelationship subjectPersonalRelationship : subjectPersonalRelationships) {
            empiConfig.validateCode(subjectPersonalRelationship.getRelationshipType(), CodedType.PERSONAL_RELATIONSHIP);
        }

        // Validate language codes.
        List<SubjectLanguage> subjectLanguages = subject.getSubjectLanguages();
        for (SubjectLanguage subjectLanguage : subjectLanguages) {
            empiConfig.validateCode(subjectLanguage.getLanguageCode(), CodedType.LANGUAGE);
        }

        // Validate nation codes.
        List<SubjectCitizenship> subjectCitizenships = subject.getSubjectCitizenships();
        for (SubjectCitizenship subjectCitizenship : subjectCitizenships) {
            empiConfig.validateCode(subjectCitizenship.getNationCode(), CodedType.NATION);
        }
    }

    /**
     *
     * @param subject
     * @throws EMPIException
     */
    protected void validateIdentitySource(Subject subject) throws EMPIException {
        EMPIConfig empiConfig = EMPIConfig.getInstance();
        if (!empiConfig.isValidateIdentitySourcesEnabled()) {
            return;  // Early exit! --  Not doing validation.
        }
        System.out.println("++++ Validating identity source ... id = " + this.senderDeviceInfo.getId());
        System.out.println("++++ Validating identity source ... name = " + this.senderDeviceInfo.getName());

        // Now, make sure that the current source (by Sender Device Info) is able to ADD/UPDATE/MERGE
        // subjects for the provided identifiers.
        IdentitySourceConfig identitySourceConfig = empiConfig.getIdentitySourceConfig(this.senderDeviceInfo);
        if (identitySourceConfig == null) {
            throw new EMPIException("Could not validate identity source '"
                    + this.senderDeviceInfo.getId()
                    + "' - not in HIEOS configuration");
        }

        // A little brute force, but small lists so should be fine.

        // Now check each subject's identifier and ensure each assigning authorities
        // is approved for the given identity source.
        List<SubjectIdentifier> subjectIdentifiers = subject.getSubjectIdentifiers();
        for (SubjectIdentifier subjectIdentifier : subjectIdentifiers) {
            SubjectIdentifierDomain subjectIdentifierDomain = subjectIdentifier.getIdentifierDomain();
            List<SubjectIdentifierDomain> validIdentifierDomains = identitySourceConfig.getIdentifierDomains();
            boolean validDomain = false;
            for (SubjectIdentifierDomain validIdentifierDomain : validIdentifierDomains) {
                if (validIdentifierDomain.equals(subjectIdentifierDomain)) {
                    validDomain = true;
                    break;
                }
            }
            if (!validDomain) {
                throw new EMPIException("Identity source '"
                        + this.senderDeviceInfo.getId()
                        + "' is not configured for universal id '"
                        + subjectIdentifierDomain.getUniversalId()
                        + "'");
            }
        }
    }

    /**
     * 
     * @param notification
     * @param enterpriseSubjectId
     * @throws EMPIException
     */
    protected void addSubjectToNotification(EMPINotification notification, String enterpriseSubjectId) throws EMPIException {
        PersistenceManager pm = this.getPersistenceManager();
        Subject subject = pm.loadEnterpriseSubjectIdentifiersAndNamesOnly(enterpriseSubjectId);
        notification.addSubject(subject);
    }
}
