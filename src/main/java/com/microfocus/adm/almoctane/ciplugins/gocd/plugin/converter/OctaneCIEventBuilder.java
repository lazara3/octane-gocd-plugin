/*
 *
 *  (c) Copyright 2018 Micro Focus or one of its affiliates.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * /
 *
 */

package com.microfocus.adm.almoctane.ciplugins.gocd.plugin.converter;

import com.hp.octane.integrations.OctaneSDK;
import com.hp.octane.integrations.dto.DTOFactory;
import com.hp.octane.integrations.dto.causes.CIEventCause;
import com.hp.octane.integrations.dto.causes.CIEventCauseType;
import com.hp.octane.integrations.dto.events.CIEvent;
import com.hp.octane.integrations.dto.events.CIEventType;
import com.hp.octane.integrations.dto.events.PhaseType;
import com.hp.octane.integrations.dto.scm.SCMData;
import com.hp.octane.integrations.dto.snapshots.CIBuildResult;
import com.microfocus.adm.almoctane.ciplugins.gocd.dto.GoPipelineConfig;
import com.microfocus.adm.almoctane.ciplugins.gocd.dto.GoPipelineInstance;
import com.microfocus.adm.almoctane.ciplugins.gocd.dto.GoStageConfig;
import com.microfocus.adm.almoctane.ciplugins.gocd.dto.GoStageInstance;
import com.microfocus.adm.almoctane.ciplugins.gocd.service.*;
import com.thoughtworks.go.plugin.api.logging.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * This build helps converting GoCD status information into {@link CIEvent}s Octane can understand.
 *
 * GoCD uses a stage-centric approach, meaning it will send state event, whenever a stage is about
 * to be build or finishes building. Octane on the other hand is only interested in the overall
 * pipeline and does not care about the stages in detail.
 *
 * @see <a href="https://plugin-api.gocd.org/current/notifications/#stage-start-notifications">Stage Start Notifications</a>
 * @see <a href="https://plugin-api.gocd.org/current/notifications/#stage-completion-notifications">Stage Completion Notifications</a>
 */
public class OctaneCIEventBuilder {

	/** All possible values for a state of a stage in a pipeline. */
	public enum PipelineStageState {
		Building,
		Passed,
		Failed,
		Cancelled
	}

	protected static final Logger Log = Logger.getLoggerFor(OctaneCIEventBuilder.class);

	private final GoApiClient goApiClient;
	private final OctaneSDK octaneInstance;

	public OctaneCIEventBuilder(final GoApiClient goApiClient, final OctaneSDK octaneInstance) {
		this.goApiClient = goApiClient;
		this.octaneInstance = octaneInstance;
	}

	/**
	 * This method will analyze the given {@param statusInfo} and may or may not generate
	 * and send a {@link CIEvent} to Octane.
	 */
	public void sendCIEvent(StatusInfoWrapper statusInfo) {
		if (statusInfo == null) {
			return;
		}

		final String pipelineName = statusInfo.getPipelineName();
		final String stageName = statusInfo.getStageName();
		final GoPipelineConfig pipelineConfig = new GoGetPipelineConfig(goApiClient).get(pipelineName);
		final List<GoStageConfig> stages = pipelineConfig.getStages();

		switch (statusInfo.getStageStatus()){
			case Building:
				if(isFirstStage(stageName,stages)){
					sendPipelineStartEvent(statusInfo);
					//send pipeline start event
				}
			//	sendStageStartEvent(statusInfo);
				break;

			case Passed:
				if(isLastStage(stageName,stages)){
					sendPipelineEndEvent(statusInfo);
					//send pipeline end event
				} else {
					//send stage end event
					sendStageEndEvent(statusInfo);
				}
				break;
			case Failed:
			case Cancelled:
				sendPipelineEndEvent(statusInfo);
				break;
			default:
				sendPipelineEndEvent(statusInfo);
		}
	}

	private List<CIEventCause> getCauses(StatusInfoWrapper statusInfo) {

		CIEventCause cause = DTOFactory.getInstance().newDTO(CIEventCause.class)
			.setType(CIEventCauseType.UPSTREAM)
			.setProject(statusInfo.getPipelineName())
			.setBuildCiId(statusInfo.getPipelineCounter());
		List<CIEventCause> causeList =new ArrayList<>();
		causeList.add(cause);
		return causeList;

	}

	private CIBuildResult getResult(PipelineStageState stageState){
		switch (stageState) {
			case Passed: return CIBuildResult.SUCCESS;
			case Failed: return CIBuildResult.FAILURE;
			case Cancelled:return CIBuildResult.ABORTED;
			default: return CIBuildResult.FAILURE;
		}
	}

	private boolean isLastStage(String stageName, List<GoStageConfig> pipelineStages) {

		// whenever a stage passes check whether it is the last stage of this pipeline; only then send an end-event.
		if (pipelineStages != null && !pipelineStages.isEmpty() &&
			pipelineStages.get(pipelineStages.size() - 1).getName().equals(String.valueOf(stageName))) {
			return true;
		}
		return false;
	}

	private boolean isFirstStage(String stageName, List<GoStageConfig> pipelineStages){

		// only generate a start-event if the current stage is the very first one in the pipeline.
		if (pipelineStages != null && !pipelineStages.isEmpty() &&
			pipelineStages.get(0).getName().equals(String.valueOf(stageName))) {
			return true;

		}

		return false;
	}

	private void sendStageStartEvent(StatusInfoWrapper statusInfo){
		final String stageName = statusInfo.getStageName();
		final String pipelineCounter = statusInfo.getPipelineCounter();
		CIEvent event = DTOFactory.getInstance().newDTO(CIEvent.class)
			.setEventType(CIEventType.STARTED)
			.setProject(stageName)
			.setProjectDisplayName(stageName)
			.setBuildCiId(pipelineCounter)
			.setNumber(pipelineCounter)
			.setCauses(getCauses(statusInfo));

		Date createTime = statusInfo.getStageCreateTime();
		if (createTime != null) {
			event.setStartTime(createTime.getTime());
		}

		octaneInstance.getEventsService().publishEvent(event);
	}

	private void sendPipelineSCMEvent(StatusInfoWrapper statusInfo, GoPipelineInstance pipelineInstance){

		if (pipelineInstance != null) {

			SCMData scmData = new OctaneSCMDataBuilder().retrieveFrom(pipelineInstance);
			if(scmData != null && scmData.getCommits()!=null) {
				CIEvent scmEvent = DTOFactory.getInstance().newDTO(CIEvent.class)
					.setEventType(CIEventType.SCM)
					.setProject(statusInfo.getPipelineName())
					.setProjectDisplayName(statusInfo.getPipelineName())
					.setBuildCiId(statusInfo.getPipelineCounter())
					.setNumber(statusInfo.getPipelineCounter())
					.setCauses(Collections.<CIEventCause>emptyList())
					.setPhaseType(PhaseType.INTERNAL)
					.setScmData(scmData);

				OctaneSDK.getInstance().getEventsService().publishEvent(scmEvent);
			}
		}
	}

//	private void sendStageTestResults(StatusInfoWrapper statusInfo, GoPipelineInstance pipelineInstance){
//
//			Log.debug("Retrieving test results for '" + jobId + "' and buildNumber '" + buildNumber + "'");
//			final TestsResult result = DTOFactory.getInstance().newDTO(TestsResult.class)
//				.setBuildContext(DTOFactory.getInstance().newDTO(BuildContext.class).setServerId(goServerID))
//				.setTestRuns(new ArrayList<TestRun>());
//
//			/** Use the same client for all requests in this method. Notice that {@link GoGetAllArtifacts}
//			 * needs an authentication cookie which is received by the client when performing an API request. */
//			if (pipelineInstance != null && pipelineInstance.getStages() != null) {
//				result.getBuildContext()
//					.setJobId(statusInfo.getStageName())
//					.setJobName(statusInfo.getStageName())
//					.setBuildId(statusInfo.getPipelineCounter())
//					.setBuildName(pipelineInstance.getLabel());
//
//				List<GoArtifact> artifacts = new GoGetAllArtifacts(goApiClient).get(statusInfo.getPipelineName(),
//					statusInfo.getPipelineCounter(), statusInfo.getStageName(), Integer.valueOf(statusInfo.getPipelineCounter()), jobInstance.getName());
//				result.getTestRuns().addAll(new OctaneTestResultsBuilder(goApiClient).convert(artifacts));
//
//
////				for (GoStageInstance stageInstance : pipelineInstance.getStages()) {
////					if (stageInstance.getJobs() != null) {
////						for (GoJobInstance jobInstance : stageInstance.getJobs()) {
////							List<GoArtifact> artifacts = new GoGetAllArtifacts(goApiClient).get(statusInfo.getPipelineName(),
////								statusInfo.getPipelineCounter(), statusInfo.getStageName(), Integer.valueOf(stageInstance.getCounter()), jobInstance.getName());
////							result.getTestRuns().addAll(new OctaneTestResultsBuilder(goApiClient).convert(artifacts));
////						}
////					}
////				}
//			}
//
//			if(result.getTestRuns() != null && !result.getTestRuns().isEmpty()){
//				try {
//					OctaneSDK.getInstance().getTestsService().pushTestsResult(result);
//				} catch (IOException e) {
//					e.printStackTrace();
//				}
//			}
//	}

	private void sendStageEndEvent(StatusInfoWrapper statusInfo) {

		final String stageName = statusInfo.getStageName();
		final String pipelineCounter = statusInfo.getPipelineCounter();
		CIEvent event = DTOFactory.getInstance().newDTO(CIEvent.class)
			.setEventType(CIEventType.FINISHED)
			.setProject(stageName)
			.setProjectDisplayName(stageName)
			.setBuildCiId(pipelineCounter)
			.setNumber(pipelineCounter)
			.setCauses(getCauses(statusInfo))
			.setResult(getResult(statusInfo.getStageStatus()));

		//setTime(event, statusInfo);

		octaneInstance.getEventsService().publishEvent(event);
	}

	private void sendPipelineStartEvent(StatusInfoWrapper statusInfo) {
		final String pipelineName = statusInfo.getPipelineName();
		final String pipelineCounter =statusInfo.getPipelineCounter();
		CIEvent event = DTOFactory.getInstance().newDTO(CIEvent.class)
			.setEventType(CIEventType.STARTED)
			.setProject(pipelineName)
			.setProjectDisplayName(pipelineName)
			.setBuildCiId(pipelineCounter)
			.setNumber(pipelineCounter)
			.setCauses(Collections.<CIEventCause>emptyList());

		Date createTime = statusInfo.getStageCreateTime();
		if (createTime != null) {
			event.setStartTime(createTime.getTime());
		}

		// try to give an estimate about the expected building time.
		final List<Long> successfulDurations = getLastSuccessfulDurations(pipelineName, 3);
		Long estimatedDuration = null;
		Collections.reverse(successfulDurations); // since the newest instance should have the highest weight, start with the oldest instance.
		for (Long duration : successfulDurations) {
			if (estimatedDuration == null) {
				estimatedDuration = duration;
			} else {
				estimatedDuration = (long)(estimatedDuration * 0.5 + duration * 0.5);
			}
		}
		event.setEstimatedDuration(estimatedDuration);

		octaneInstance.getEventsService().publishEvent(event);
	}

	private void sendPipelineEndEvent(StatusInfoWrapper statusInfo) {
		final String pipelineName = statusInfo.getPipelineName();
		final String pipelineCounter = statusInfo.getPipelineCounter();
		CIEvent event = DTOFactory.getInstance().newDTO(CIEvent.class)
			.setEventType(CIEventType.FINISHED)
			.setProject(pipelineName)
			.setProjectDisplayName(pipelineName)
			.setBuildCiId(pipelineCounter)
			.setNumber(pipelineCounter)
			.setCauses(Collections.<CIEventCause>emptyList())
			.setResult(getResult(statusInfo.getStageStatus()));

		// determine the start-time of this pipeline.
		GoPipelineInstance pipelineInstance = new GoGetPipelineInstance(goApiClient).get(pipelineName, Integer.valueOf(pipelineCounter));
		if (pipelineInstance != null) {
			Long firstScheduledDate = pipelineInstance.getFirstScheduledDate();
			// correct the start time to the first documented date.
			event.setStartTime(firstScheduledDate);
			Date lastTransitionTime = statusInfo.getStageLastTransitionTime();
			if (lastTransitionTime != null && firstScheduledDate != null) {
				event.setDuration(lastTransitionTime.getTime() - firstScheduledDate); // in ms
			}
		}

		octaneInstance.getEventsService().publishEvent(event);
		// tell octane to request the test results.
		sendPipelineSCMEvent(statusInfo, pipelineInstance);
		octaneInstance.getTestsService().enqueuePushTestsResult(pipelineName, pipelineCounter);
	}

	/**
	 * This method collects the durations of the last successful pipeline runs.
	 * @param pipelineName name of the pipeline
	 * @param amount maximum number of durations
	 * @return found durations as a list. Never null. Might be less than the wanted amount.
	 */
	protected List<Long> getLastSuccessfulDurations(final String pipelineName, final int amount) {
		final List<Long> successfulDurations = new ArrayList<>();
		for (GoPipelineInstance instance : new GoGetPipelineHistory(goApiClient).get(pipelineName)) {
			if (successfulDurations.size() >= amount) {
				break; // enough durations collected.
			}
			if (!instance.isPassed()) {
				continue; // skip incomplete instances.
			}
			Long startTime = instance.getFirstScheduledDate();
			// PipelineInstance do not contain the jobTransitions, we have to query them.
			GoStageInstance stage = instance.getLastStage();
			if (stage != null) {
				GoStageInstance detailedStageInstance = new GoGetStageInstance(goApiClient).get(pipelineName, instance.getCounter(), stage.getName(), Integer.valueOf(stage.getCounter()));
				if (detailedStageInstance != null) {
					Long lastTransitionTime = detailedStageInstance.getLastJobTransitionDate();
					if (lastTransitionTime != null) {
						successfulDurations.add(lastTransitionTime - startTime);
					}
				}
			}

		}
		return successfulDurations;
	}
}