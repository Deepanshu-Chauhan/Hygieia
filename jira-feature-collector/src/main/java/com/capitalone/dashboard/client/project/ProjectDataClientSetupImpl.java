/*************************DA-BOARD-LICENSE-START*********************************
 * Copyright 2014 CapitalOne, LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *************************DA-BOARD-LICENSE-END*********************************/

package com.capitalone.dashboard.client.project;

import com.capitalone.dashboard.client.DataClientSetup;
import com.capitalone.dashboard.datafactory.jira.JiraDataFactoryImpl;
import com.capitalone.dashboard.model.Scope;
import com.capitalone.dashboard.repository.FeatureCollectorRepository;
import com.capitalone.dashboard.repository.ScopeRepository;
import com.capitalone.dashboard.util.Constants;
import com.capitalone.dashboard.util.DateUtil;
import com.capitalone.dashboard.util.FeatureSettings;
import org.json.simple.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Implemented class which is extended by children to perform actual
 * source-system queries as a service and to update the MongoDB in accordance.
 *
 * @author kfk884
 *
 */
@Component
public abstract class ProjectDataClientSetupImpl implements DataClientSetup {
	private static final Logger LOGGER = LoggerFactory.getLogger(ProjectDataClientSetupImpl.class);

	protected final FeatureSettings featureSettings;
	protected final FeatureCollectorRepository featureCollectorRepository;
	protected String todayDateISO;
	protected String query;
	protected Class<?> objClass;
	protected String returnDate;
	protected ScopeRepository projectRepo;

	/**
	 * Constructs the feature data collection based on system settings.
	 *
	 * @param featureSettings
	 *            Feature collector system settings
	 */
	public ProjectDataClientSetupImpl(FeatureSettings featureSettings,
			ScopeRepository projectRepository,
			FeatureCollectorRepository featureCollectorRepository) {
		super();
		LOGGER.debug("Constructing data collection for the feature widget...");

		this.featureSettings = featureSettings;
		this.projectRepo = projectRepository;
		this.featureCollectorRepository = featureCollectorRepository;
		returnDate = featureSettings.getMasterStartDate();
		setTodayDateISO(DateUtil.toISODateFormat(DateUtil.getTodayNoTime()));
	}

	/**
	 * This method is used to update the database with model defined in the
	 * collector model definitions.
	 *
	 * @see Story
	 */
	public void updateObjectInformation() {
		long start = System.nanoTime();
		String jiraCredentials = this.featureSettings.getJiraCredentials();
		String jiraBaseUrl = this.featureSettings.getJiraBaseUrl();
		String jiraQueryEndpoint = this.featureSettings.getJiraQueryEndpoint();
		try {
			JiraDataFactoryImpl jiraApi = new JiraDataFactoryImpl(
					jiraCredentials, jiraBaseUrl, jiraQueryEndpoint);
			jiraApi.buildBasicQuery(query);
			JSONArray outPutMainArray = jiraApi.getArrayQueryResponse();
			if (outPutMainArray == null) {
				throw new NullPointerException(
						"FAILED: Script Completed with Error");
			}
			JSONArray tmpDetailArray = (JSONArray) outPutMainArray.get(0);
			updateMongoInfo(tmpDetailArray);
		} catch (Exception e) {
			LOGGER.error("Unexpected error in Jira basic request of "
					+ e.getClass().getName() + "\n[" + e.getMessage() + "]");
		}

		double elapsedTime = (System.nanoTime() - start) / 1000000000.0;
		LOGGER.info("Process took :" + elapsedTime + " seconds to update");
	}

	/**
	 * Generates and retrieves the local server time stamp in Unix Epoch format.
	 *
	 * @param unixTimeStamp
	 *            The current millisecond value of since the Unix Epoch
	 * @return Unix Epoch-formatted time stamp for the current date/time
	 */
	public String getLocalTimeStampFromUnix(long unixTimeStamp) {
		long unixSeconds = unixTimeStamp;

		SimpleDateFormat sdf = new SimpleDateFormat("MMMM d, yyyy 'at' h:mm a");
		String date = sdf.format(unixSeconds);
		LOGGER.debug(unixSeconds + "==>" + date);

		return date;
	}

	/**
	 * Generates and retrieves the change date that occurs a minute prior to the
	 * specified change date in ISO format.
	 *
	 * @param changeDateISO
	 *            A given change date in ISO format
	 * @return The ISO-formatted date/time stamp for a minute prior to the given
	 *         change date
	 */
	public String getChangeDateMinutePrior(String changeDateISO) {
		int priorMinutes = this.featureSettings.getScheduledPriorMin();
		return DateUtil.toISODateRealTimeFormat(DateUtil.getDatePriorToMinutes(
				DateUtil.fromISODateTimeFormat(changeDateISO), priorMinutes));
	}

	/**
	 * Generates and retrieves the sprint start date in ISO format.
	 *
	 * @return The ISO-formatted date/time stamp for the sprint start date
	 */
	public String getSprintBeginDateFilter() {
		int priorDays = this.featureSettings.getSprintDays();
		return DateUtil.toISODateRealTimeFormat(DateUtil.getDatePriorToNDays(
				DateUtil.getDateNoTime(new Date()), priorDays));
	}

	/**
	 * Generates and retrieves the sprint end date in ISO format.
	 *
	 * @return The ISO-formatted date/time stamp for the sprint end date
	 */
	public String getSprintEndDateFilter() {
		int afterDays = this.featureSettings.getSprintDays();
		return DateUtil.toISODateRealTimeFormat(DateUtil.addDays(
				DateUtil.getDateNoTime(new Date()), afterDays));
	}

	/**
	 * Generates and retrieves the difference between the sprint start date and
	 * the sprint end date in ISO format.
	 *
	 * @return The ISO-formatted date/time stamp for the sprint start date
	 */
	public String getSprintDeltaDateFilter() {
		int priorDeltaDays = this.featureSettings.getSprintEndPrior();
		return DateUtil.toISODateRealTimeFormat(DateUtil.getDatePriorToNDays(
				DateUtil.getDateNoTime(new Date()), priorDeltaDays));
	}

	/**
	 * Accessor method for today's date in ISO format
	 */
	public String getTodayDateISO() {
		return todayDateISO;
	}

	/**
	 * Mutator method for setting today's date in ISO format
	 */
	public void setTodayDateISO(String todayDateISO) {
		this.todayDateISO = todayDateISO;
	}

	/**
	 * Retrieves the maximum change date for a given query.
	 *
	 * @return A list object of the maximum change date
	 */
	@SuppressWarnings("PMD.AvoidCatchingNPE")
	public String getMaxChangeDate() {
		String data = null;
		try {
			List<Scope> response = projectRepo.findTopByCollectorIdAndChangeDateGreaterThanOrderByChangeDateDesc(
					featureCollectorRepository.findByName(Constants.JIRA).getId(),
					featureSettings.getDeltaStartDate());
			if (!response.isEmpty()) {
				data = response.get(0).getChangeDate();
			}
		} catch (NullPointerException npe) {
			LOGGER.debug("No data was currently available in the local database that corresponded to a max change date\nReturning null");
		} catch (Exception e) {
			LOGGER.error("There was a problem retrieving or parsing data from the local repository while retrieving a max change date\nReturning null");
		}

		return data;
	}

	/**
	 * Abstract method required by children methods to update the MongoDB with a
	 * JSONArray received from the source system back-end.
	 *
	 * @param tmpMongoDetailArray
	 *            A JSON response in JSONArray format from the source system
	 * @return
	 */
	protected abstract void updateMongoInfo(JSONArray tmpMongoDetailArray);
}
