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
import com.vangent.hieos.xutil.exception.NoSubmissionSetException;
import com.vangent.hieos.xutil.exception.XdsException;
import com.vangent.hieos.xutil.metadata.structure.Metadata;
import com.vangent.hieos.xutil.metadata.structure.MetadataSupport;
import com.vangent.hieos.xutil.metadata.structure.SQCodedTerm;
import com.vangent.hieos.xutil.metadata.structure.SqParams;
import com.vangent.hieos.xutil.response.Response;
import com.vangent.hieos.xutil.query.StoredQuery;
import com.vangent.hieos.xutil.xlog.client.XLogMessage;

import java.util.ArrayList;
import org.apache.axiom.om.OMElement;
import org.apache.log4j.Logger;

/**
 *
 * @author NIST (Adapted by Bernie Thuman).
 */
public class GetSubmissionSetAndContents extends StoredQuery {

    private final static Logger logger = Logger.getLogger(GetSubmissionSetAndContents.class);
    //private final HashMap<String, String> params;

    /**
     *
     * @param params
     * @param response
     * @param log_message
     */
    /*
    public GetSubmissionSetAndContents(HashMap<String, String> params, ErrorLogger response, XLogMessage log_message) {
    super(response, log_message);
    this.params = params;
    }*/
    /**
     *
     * @param params
     * @param return_objects
     * @param response
     * @param log_message
     * @param is_secure
     * @throws MetadataValidationException
     */
    public GetSubmissionSetAndContents(SqParams params, boolean return_objects, Response response, XLogMessage log_message)
            throws MetadataValidationException {
        super(params, return_objects, response, log_message);

        // param name, required?, multiple?, is string?, is code?, alternative
        validateQueryParam("$XDSSubmissionSetEntryUUID", true, false, true, false, "$XDSSubmissionSetUniqueId");
        validateQueryParam("$XDSSubmissionSetUniqueId", true, false, true, false, "$XDSSubmissionSetEntryUUID");
        validateQueryParam("$XDSDocumentEntryFormatCode", false, true, true, true, (String[]) null);
        validateQueryParam("$XDSDocumentEntryConfidentialityCode", false, true, true, true, (String[]) null);
        if (this.has_validation_errors) {
            throw new MetadataValidationException("Metadata Validation error present");
        }
    }

    /**
     *
     * @return
     * @throws XdsException
     */
    public Metadata run_internal() throws XdsException {
        Metadata metadata;
        String ss_uuid = params.getStringParm("$XDSSubmissionSetEntryUUID");
        if (ss_uuid != null) {
            // starting from uuid
            OMElement x = get_rp_by_uuid(ss_uuid, MetadataSupport.XDSSubmissionSet_uniqueid_uuid);
            try {
                metadata = new Metadata(x);
            } catch (NoSubmissionSetException e) {
                return null;
            }
        } else {
            // starting from uniqueid
            String ss_uid = params.getStringParm("$XDSSubmissionSetUniqueId");
            OMElement x = get_rp_by_uid(ss_uid, MetadataSupport.XDSSubmissionSet_uniqueid_uuid);
            try {
                metadata = new Metadata(x);
            } catch (NoSubmissionSetException e) {
                return null;
            }
            ss_uuid = metadata.getSubmissionSet().getAttributeValue(MetadataSupport.id_qname);
        }

        // ss_uuid has now been set
        SQCodedTerm conf_codes = params.getCodedParm("$XDSDocumentEntryConfidentialityCode");
        SQCodedTerm format_codes = params.getCodedParm("$XDSDocumentEntryFormatCode");

        OMElement doc_metadata = get_ss_docs(ss_uuid, format_codes, conf_codes);
        metadata.addMetadata(doc_metadata);

        OMElement fol_metadata = get_ss_folders(ss_uuid);
        metadata.addMetadata(fol_metadata);

        ArrayList<String> ssUuids = new ArrayList<String>();
        ssUuids.add(ss_uuid);
        OMElement assoc1_metadata = this.get_assocs(ssUuids);
        if (assoc1_metadata != null) {
            metadata.addMetadata(assoc1_metadata);
        }

        ArrayList<String> folder_ids = metadata.getFolderIds();
        OMElement assoc2_metadata = this.get_assocs(folder_ids);
        if (assoc2_metadata != null) {
            metadata.addMetadata(assoc2_metadata);
        }

//		ArrayList<String> ss_and_folder_ids = new ArrayList<String>(folder_ids);
//		ss_and_folder_ids.add(ss_uuid);

        metadata.rmDuplicates();

        // some document may have been filtered out, remove the unnecessary Associations
        ArrayList<String> content_ids = new ArrayList<String>();
        content_ids.addAll(metadata.getSubmissionSetIds());
        content_ids.addAll(metadata.getExtrinsicObjectIds());
        content_ids.addAll(metadata.getFolderIds());

        // add in Associations that link the above parts
        content_ids.addAll(metadata.getIds(metadata.getAssociationsInclusive(content_ids)));

        // Assocs can link to Assocs to so repeat
        content_ids.addAll(metadata.getIds(metadata.getAssociationsInclusive(content_ids)));

        metadata.filter(content_ids);
        return metadata;
    }

    /**
     *
     * @param ss_uuid
     * @return
     * @throws XdsException
     */
    private OMElement get_ss_folders(String ss_uuid) throws XdsException {
        init();
        append("SELECT * FROM RegistryPackage fol, Association a");
        newline();
        append("WHERE");
        newline();
        append("   a.associationType = '");
        append(MetadataSupport.xdsB_eb_assoc_type_has_member);
        append("' AND");
        newline();
        append("   a.sourceObject = '" + ss_uuid + "' AND");
        newline();
        append("   a.targetObject = fol.id ");
        newline();
        return query();
    }
}
