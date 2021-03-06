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
package com.vangent.hieos.DocViewer.client.services.proxy;

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.smartgwt.client.util.SC;
import com.vangent.hieos.DocViewer.client.helper.Observer;
import com.vangent.hieos.DocViewer.client.helper.TimeOutHelper;
import com.vangent.hieos.DocViewer.client.model.config.ConfigDTO;
import com.vangent.hieos.DocViewer.client.services.rpc.ConfigRemoteService;

public class ConfigRetrieveService extends ProxyService {

	/**
	 * 
	 * @param observer
	 * @param timeOutHelper
	 */
	public ConfigRetrieveService(Observer observer, TimeOutHelper timeOutHelper) {
		super(observer, timeOutHelper);
	}

	/**
	 * 
	 */
	public void doWork() {

		this.getTimeOutHelper().startTimer();
		// RPC:
		ConfigRemoteService.Util.getInstance().getConfig(
				new AsyncCallback<ConfigDTO>() {

					@Override
					public void onSuccess(ConfigDTO config) {
						cancelTimer();
						if (getAbortFlag()) {
							// Timeout already occurred. discard result
							return;
						}
						update(config);
					}

					@Override
					public void onFailure(Throwable caught) {
						cancelTimer();
						SC.warn("EXCEPTION: " + caught.getMessage());
						SC.logWarn(caught.getMessage());
					}

				});

	}

}
