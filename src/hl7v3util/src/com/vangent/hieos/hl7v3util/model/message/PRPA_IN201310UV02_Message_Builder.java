/*
 * This code is subject to the HIEOS License, Version 1.0
 *
 * Copyright(c) 2010 Vangent, Inc.  All rights reserved.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.vangent.hieos.hl7v3util.model.message;

import com.vangent.hieos.subjectmodel.DeviceInfo;
import com.vangent.hieos.subjectmodel.Subject;
import com.vangent.hieos.subjectmodel.SubjectSearchResponse;
import java.util.List;
import org.apache.axiom.om.OMElement;

/**
 *
 * @author Bernie Thuman
 */
public class PRPA_IN201310UV02_Message_Builder extends HL7V3MessageBuilderHelper {

    /**
     *
     */
    private PRPA_IN201310UV02_Message_Builder() {
    }

    /**
     *
     * @param senderDeviceInfo
     * @param receiverDeviceInfo
     */
    public PRPA_IN201310UV02_Message_Builder(DeviceInfo senderDeviceInfo, DeviceInfo receiverDeviceInfo) {
        super(senderDeviceInfo, receiverDeviceInfo);
    }

    /**
     *
     * @param PRPA_IN201309UV02_Message The request.
     * @param subjectSearchResponse [may be null]
     * @param errorDetail [may be null]
     * @return PRPA_IN201310UV02_Message
     */
    public PRPA_IN201310UV02_Message buildPRPA_IN201310UV02_Message(
            PRPA_IN201309UV02_Message request,
            SubjectSearchResponse subjectSearchResponse,
            HL7V3ErrorDetail errorDetail) {
        OMElement requestNode = request.getMessageNode();
        String messageName = "PRPA_IN201310UV02";

        // PRPA_IN201310UV02
        OMElement responseNode = this.getResponseNode(messageName, "P", "T", "NE");

        // PRPA_IN201310UV02/acknowledgement
        this.addAcknowledgementToRequest(requestNode, responseNode, errorDetail, "AA", "AE");

        // PRPA_IN201310UV02/controlActProcess
        OMElement controlActProcessNode = this.addControlActProcess(responseNode, "PRPA_TE201310UV02");

        // PRPA_IN201310UV02/controlActProcess/subject
        List<Subject> subjects = subjectSearchResponse != null ? subjectSearchResponse.getSubjects() : null;
        this.addSubjectsWithOnlyIds(controlActProcessNode, subjects);

        // PRPA_IN201310UV02/controlActProcess/queryAck
        this.addQueryAckToRequest(requestNode, controlActProcessNode, subjects, errorDetail);

        // PRPA_IN201310UV02/controlActProcess/queryByParameter
        this.addQueryByParameterFromRequest(requestNode, controlActProcessNode);

        return new PRPA_IN201310UV02_Message(responseNode);
    }
}
