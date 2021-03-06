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

package com.vangent.hieos.services.xds.bridge.message;

import com.vangent.hieos.xutil.xml.OMElementNodeWrapper;
import org.apache.axiom.om.OMElement;

/**
 * Class description
 *
 *
 * @version        v1.0, 2011-06-29
 * @author         Jim Horner
 */
public class SubmitDocumentResponseMessage extends OMElementNodeWrapper {

    public static final String MESSAGE_TYPE = "SubmitDocumentResponse";
    /**
     * Constructs ...
     *
     *
     * @param messageNode
     */
    public SubmitDocumentResponseMessage(OMElement messageNode) {
        super(messageNode, MESSAGE_TYPE);
    }
}
