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
package com.vangent.hieos.services.xds.registry.storedquery;

import com.vangent.hieos.xutil.exception.MetadataValidationException;
import com.vangent.hieos.xutil.exception.XDSRegistryOutOfResourcesException;
import com.vangent.hieos.xutil.exception.XdsException;
import com.vangent.hieos.xutil.exception.XdsInternalException;
import com.vangent.hieos.xutil.response.ErrorLogger;
import com.vangent.hieos.xutil.metadata.structure.Metadata;
import com.vangent.hieos.xutil.metadata.structure.MetadataParser;
import com.vangent.hieos.xutil.metadata.structure.SQCodedTerm;
import com.vangent.hieos.xutil.metadata.structure.SqParams;
import com.vangent.hieos.xutil.response.Response;
import com.vangent.hieos.xutil.query.StoredQuery;
import com.vangent.hieos.xutil.xlog.client.XLogMessage;

import java.util.List;
import org.apache.axiom.om.OMElement;

/**
 * 
 * @author NIST (Adapted by Bernie Thuman).
 */
public class FindDocumentsForMultiplePatients extends StoredQuery {

    /**
     *
     * @param response
     * @param log_message
     */
    public FindDocumentsForMultiplePatients(ErrorLogger response, XLogMessage log_message) {
        super(response, log_message);
    }

    /**
     *
     * @param params
     * @param return_objects
     * @param response
     * @param log_message
     * @param is_secure
     * @throws MetadataValidationException
     */
    public FindDocumentsForMultiplePatients(SqParams params, boolean return_objects, Response response, XLogMessage log_message)
            throws MetadataValidationException {
        super(params, return_objects, response, log_message);

        // param name, required?, multiple?, is string?, is code?, alternative
        validateQueryParam("$XDSDocumentEntryPatientId", false, true, true, false, (String[]) null);
        validateQueryParam("$XDSDocumentEntryClassCode", false, true, true, true, "$XDSDocumentEntryEventCodeList", "$XDSDocumentEntryHealthcareFacilityTypeCode");
        validateQueryParam("$XDSDocumentEntryPracticeSettingCode", false, true, true, true, (String[]) null);
        validateQueryParam("$XDSDocumentEntryCreationTimeFrom", false, false, true, false, (String[]) null);
        validateQueryParam("$XDSDocumentEntryCreationTimeTo", false, false, true, false, (String[]) null);
        validateQueryParam("$XDSDocumentEntryServiceStartTimeFrom", false, false, true, false, (String[]) null);
        validateQueryParam("$XDSDocumentEntryServiceStartTimeTo", false, false, true, false, (String[]) null);
        validateQueryParam("$XDSDocumentEntryServiceStopTimeFrom", false, false, true, false, (String[]) null);
        validateQueryParam("$XDSDocumentEntryServiceStopTimeTo", false, false, true, false, (String[]) null);
        validateQueryParam("$XDSDocumentEntryHealthcareFacilityTypeCode", false, true, true, true, "$XDSDocumentEntryEventCodeList", "$XDSDocumentEntryClassCode");
        validateQueryParam("$XDSDocumentEntryEventCodeList", false, true, true, true, "$XDSDocumentEntryClassCode", "$XDSDocumentEntryHealthcareFacilityTypeCode");
        validateQueryParam("$XDSDocumentEntryConfidentialityCode", false, true, true, true, (String[]) null);
        validateQueryParam("$XDSDocumentEntryFormatCode", false, true, true, true, (String[]) null);
        validateQueryParam("$XDSDocumentEntryStatus", true, true, true, false, (String[]) null);
        validateQueryParam("$XDSDocumentEntryAuthorPerson", false, true, true, false, (String[]) null);
        if (this.has_validation_errors) {
            throw new MetadataValidationException("Metadata Validation error present");
        }
    }

    /**
     *
     * @return
     * @throws XdsInternalException
     * @throws XdsException
     * @throws XDSRegistryOutOfResourcesException
     */
    public Metadata run_internal() throws XdsInternalException, XdsException, XDSRegistryOutOfResourcesException {
        if (this.return_leaf_class == true) {
            this.return_leaf_class = false;
            OMElement refs = impl();
            Metadata m = MetadataParser.parseNonSubmission(refs);
            // Guard against large queries -- FIXME(BHT): Configure this parameter.
            if (m.getObjectRefs().size() > 25) {
                throw new XDSRegistryOutOfResourcesException("GetDocumentsForMultiplePatients Stored Query for LeafClass is limited to 25 documents on this Registry. Your query targeted " + m.getObjectRefs().size() + " documents");
            }
            this.return_leaf_class = true;
        }
        OMElement results = impl();
        Metadata m = MetadataParser.parseNonSubmission(results);
        if (log_message != null) {
            log_message.addOtherParam("Results structure", m.structure());
        }
        return m;
    }

    /**
     *
     * @return
     * @throws XdsInternalException
     * @throws XdsException
     */
    OMElement impl() throws XdsInternalException, XdsException {
        List<String> patient_id = params.getListParm("$XDSDocumentEntryPatientId");
        SQCodedTerm class_codes = params.getCodedParm("$XDSDocumentEntryClassCode");
        SQCodedTerm type_codes = params.getCodedParm("$XDSDocumentEntryTypeCode");
        SQCodedTerm practice_setting_codes = params.getCodedParm("$XDSDocumentEntryPracticeSettingCode");
        String creation_time_from = params.getIntParm("$XDSDocumentEntryCreationTimeFrom");
        String creation_time_to = params.getIntParm("$XDSDocumentEntryCreationTimeTo");
        String service_start_time_from = params.getIntParm("$XDSDocumentEntryServiceStartTimeFrom");
        String service_start_time_to = params.getIntParm("$XDSDocumentEntryServiceStartTimeTo");
        String service_stop_time_from = params.getIntParm("$XDSDocumentEntryServiceStopTimeFrom");
        String service_stop_time_to = params.getIntParm("$XDSDocumentEntryServiceStopTimeTo");
        SQCodedTerm hcft_codes = params.getCodedParm("$XDSDocumentEntryHealthcareFacilityTypeCode");
        SQCodedTerm event_codes = params.getCodedParm("$XDSDocumentEntryEventCodeList");
        SQCodedTerm conf_codes = params.getCodedParm("$XDSDocumentEntryConfidentialityCode");
        SQCodedTerm format_codes = params.getCodedParm("$XDSDocumentEntryFormatCode");
        List<String> status = params.getListParm("$XDSDocumentEntryStatus");
        List<String> author_person = params.getListParm("$XDSDocumentEntryAuthorPerson");
        init();
        if (this.return_leaf_class) {
            append("SELECT * ");
            newline();
        } else {
            append("SELECT obj.id  ");
            newline();
        }
        append("FROM ExtrinsicObject obj");
        if (patient_id != null && patient_id.size() > 0) {
            append(", ExternalIdentifier patId");
            newline();
        }
        if (class_codes != null) {
            append(declareClassifications(class_codes));
        }
        if (type_codes != null) {
            append(declareClassifications(type_codes));
        }
        if (practice_setting_codes != null) {
            append(declareClassifications(practice_setting_codes));
        }
        if (hcft_codes != null) {
            append(declareClassifications(hcft_codes));  // $XDSDocumentEntryHealthcareFacilityTypeCode
        }
        if (event_codes != null) {
            append(declareClassifications(event_codes)); // $XDSDocumentEntryEventCodeList
        }
        if (creation_time_from != null) {
            append(", Slot crTimef");
        }
        newline();                       // $XDSDocumentEntryCreationTimeFrom
        if (creation_time_to != null) {
            append(", Slot crTimet");
        }
        newline();                       // $XDSDocumentEntryCreationTimeTo
        if (service_start_time_from != null) {
            append(", Slot serStartTimef");
        }
        newline();                 // $XDSDocumentEntryServiceStartTimeFrom
        if (service_start_time_to != null) {
            append(", Slot serStartTimet");
        }
        newline();                 // $XDSDocumentEntryServiceStartTimeTo
        if (service_stop_time_from != null) {
            append(", Slot serStopTimef");
        }
        newline();                  // $XDSDocumentEntryServiceStopTimeFrom
        if (service_stop_time_to != null) {
            append(", Slot serStopTimet");
        }
        newline();                  // $XDSDocumentEntryServiceStopTimeTo
        if (conf_codes != null) {
            append(declareClassifications(conf_codes));  // $XDSDocumentEntryConfidentialityCode
        }
        if (format_codes != null) {
            append(", Classification fmtCode");
        }
        newline();             // $XDSDocumentEntryFormatCode
        if (author_person != null) {
            append(", Classification author");
        }
        newline();
        if (author_person != null) {
            append(", Slot authorperson");
        }
        newline();
        where();
        newline();

        // patient id
        if (patient_id != null && patient_id.size() > 0) {
            append("(obj.id = patId.registryobject AND	");
            newline();
            append("  patId.identificationScheme='urn:uuid:58a6f841-87b3-4a3e-92fd-a8ffeff98427' AND ");
            newline();
            append("  patId.value IN ");
            append(patient_id);
            append(" ) ");
            newline();
        }
        addCode(class_codes);
        addCode(type_codes);
        addCode(practice_setting_codes);
        addTimes("creationTime", "crTimef", "crTimet", creation_time_from, creation_time_to, "obj");
        addTimes("serviceStartTime", "serStartTimef", "serStartTimet", service_start_time_from, service_start_time_to, "obj");
        addTimes("serviceStopTime", "serStopTimef", "serStopTimet", service_stop_time_from, service_stop_time_to, "obj");
        addCode(hcft_codes);
        addCode(event_codes);
        addCode(conf_codes);
        addCode(format_codes);
        if (author_person != null) {
            for (String ap : author_person) {
                and();
                newline();
                append("(obj.id = author.classifiedObject AND ");
                newline();
                append("  author.classificationScheme='urn:uuid:93606bcf-9494-43ec-9b4e-a7748d1a838d' AND ");
                newline();
                append("  authorperson.parent = author.id AND");
                newline();
                append("  authorperson.name = 'authorPerson' AND");
                newline();
                append("  authorperson.value LIKE '" + ap + "' )");
                newline();
            }
        }
        and();
        append(" obj.status IN ");
        append(status);
        return query(this.return_leaf_class);
    }
}