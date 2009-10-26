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
import com.vangent.hieos.xutil.exception.XdsException;
import com.vangent.hieos.xutil.exception.XdsInternalException;
import com.vangent.hieos.xutil.metadata.structure.Metadata;
import com.vangent.hieos.xutil.metadata.structure.MetadataParser;
import com.vangent.hieos.xutil.metadata.structure.SqParams;
import com.vangent.hieos.xutil.response.Response;
import com.vangent.hieos.xutil.query.StoredQuery;
import com.vangent.hieos.xutil.xlog.client.XLogMessage;

import java.util.ArrayList;
import java.util.List;
import org.apache.axiom.om.OMElement;

/**
 *
 * @author NIST (Adapted by Bernie Thuman).
 */
public class GetDocuments extends StoredQuery {

    /**
     *
     * @param params
     * @param return_objects
     * @param response
     * @param log_message
     * @param is_secure
     * @throws MetadataValidationException
     */
    public GetDocuments(SqParams params, boolean return_objects, Response response, XLogMessage log_message)
            throws MetadataValidationException {
        super(params, return_objects, response, log_message);

        // param name, required?, multiple?, is string?, is code?, alternative
        validateQueryParam("$XDSDocumentEntryUniqueId", true, true, true, false, "$XDSDocumentEntryEntryUUID");
        validateQueryParam("$XDSDocumentEntryEntryUUID", true, true, true, false, "$XDSDocumentEntryUniqueId");
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
        List<String> uids = params.getListParm("$XDSDocumentEntryUniqueId");
        List<String> uuids = params.getListParm("$XDSDocumentEntryEntryUUID");
        if (uids != null) {
            OMElement ele = getDocumentByUID(uids);
            metadata = MetadataParser.parseNonSubmission(ele);
        } else if (uuids != null) {
            OMElement ele = getDocumentByUUID(uuids);
            metadata = MetadataParser.parseNonSubmission(ele);
        } else {
            throw new XdsInternalException("GetDocuments Stored Query: uuid not found, uid not found");
        }
        return metadata;
    }
}