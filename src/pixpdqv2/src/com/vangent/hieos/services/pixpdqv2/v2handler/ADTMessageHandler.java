/*
 * This code is subject to the HIEOS License, Version 1.0
 *
 * Copyright(c) 2012 Vangent, Inc.  All rights reserved.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.vangent.hieos.services.pixpdqv2.v2handler;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import com.vangent.hieos.hl7v2util.model.message.AckMessageBuilder;

/**
 *
 * @author Bernie Thuman
 */
public class ADTMessageHandler extends HL7V2MessageHandler {

    /**
     *
     * @param inMessage
     * @param responseText
     * @param errorText
     * @param errorCode
     * @return
     * @throws HL7Exception
     */
    public Message buildAck(Message inMessage, String responseText, String errorText, String errorCode) throws HL7Exception {
        AckMessageBuilder ackMessageBuilder = new AckMessageBuilder(inMessage);
        return ackMessageBuilder.buildAck(responseText, errorText, errorCode);
    }
}