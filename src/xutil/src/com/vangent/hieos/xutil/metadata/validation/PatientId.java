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
package com.vangent.hieos.xutil.metadata.validation;

import com.vangent.hieos.xutil.exception.MetadataException;
import com.vangent.hieos.xutil.exception.MetadataValidationException;
import com.vangent.hieos.xutil.exception.XdsInternalException;
import com.vangent.hieos.xutil.metadata.structure.Metadata;
import com.vangent.hieos.xutil.metadata.structure.MetadataSupport;
import com.vangent.hieos.xutil.response.RegistryErrorList;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author NIST (adapted for HIEOS).
 */
public class PatientId {

    private ArrayList<String> patient_ids;
    private RegistryErrorList rel;
    private Metadata m;

   /**
    *
    * @param m
    * @param rel
    */
    public PatientId(Metadata m, RegistryErrorList rel) {
        this.rel = rel;
        this.m = m;
        patient_ids = new ArrayList<String>();
    }

    /**
     *
     * @throws XdsInternalException
     * @throws MetadataValidationException
     * @throws MetadataException
     */
    public void run() throws XdsInternalException, MetadataValidationException, MetadataException {

        gather_patient_ids(m, m.getSubmissionSetIds(), MetadataSupport.XDSSubmissionSet_patientid_uuid);
        gather_patient_ids(m, m.getExtrinsicObjectIds(), MetadataSupport.XDSDocumentEntry_patientid_uuid);
        gather_patient_ids(m, m.getFolderIds(), MetadataSupport.XDSFolder_patientid_uuid);

        if (patient_ids.size() > 1) {
            rel.add_error(MetadataSupport.XDSPatientIdDoesNotMatch, "Multiple Patient IDs found in submission: " + patient_ids, this.getClass().getName(), null);
        }
    }

    /**
     *
     * @param m
     * @param parts
     * @param uuid
     * @throws MetadataException
     */
    private void gather_patient_ids(Metadata m, List<String> parts, String uuid) throws MetadataException {
        String patient_id;
        for (String id : parts) {
            patient_id = m.getExternalIdentifierValue(id, uuid);
            if (patient_id == null) {
                continue;
            }
            if (!patient_ids.contains(patient_id)) {
                patient_ids.add(patient_id);
            }
        }
    }

    /**
     *
     * @param msg
     */
    private void err(String msg) {
        rel.add_error(MetadataSupport.XDSRegistryMetadataError, msg, this.getClass().getName(), null);
    }
}
