package com.haufelexware.octane;

import com.haufelexware.gocd.dto.*;
import com.haufelexware.gocd.service.GoApiClient;
import com.haufelexware.gocd.service.GoGetPipelineConfig;
import com.haufelexware.gocd.service.GoGetPipelineGroups;
import com.haufelexware.gocd.plugin.octane.settings.OctaneGoCDPluginSettings;
import com.haufelexware.gocd.service.GoGetPipelineHistory;
import com.haufelexware.util.checker.Checker;
import com.haufelexware.util.checker.ListChecker;
import com.haufelexware.util.converter.Converter;
import com.haufelexware.util.converter.ListConverter;
import com.hp.octane.integrations.dto.DTOFactory;
import com.hp.octane.integrations.dto.configuration.CIProxyConfiguration;
import com.hp.octane.integrations.dto.configuration.OctaneConfiguration;
import com.hp.octane.integrations.dto.general.CIJobsList;
import com.hp.octane.integrations.dto.general.CIPluginInfo;
import com.hp.octane.integrations.dto.general.CIServerInfo;
import com.hp.octane.integrations.dto.general.CIServerTypes;
import com.hp.octane.integrations.dto.pipelines.PipelineNode;
import com.hp.octane.integrations.dto.pipelines.PipelinePhase;
import com.hp.octane.integrations.dto.snapshots.CIBuildResult;
import com.hp.octane.integrations.dto.snapshots.CIBuildStatus;
import com.hp.octane.integrations.dto.snapshots.SnapshotNode;
import com.hp.octane.integrations.dto.snapshots.SnapshotPhase;
import com.hp.octane.integrations.spi.CIPluginServicesBase;
import com.thoughtworks.go.plugin.api.logging.Logger;

import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class is the entry point into the Octane-PluginService.
 * As described in <a href="https://github.com/HPSoftware/octane-ci-java-sdk/blob/master/README.md">ReadMe</a>
 * it derives from {@link CIPluginServicesBase}.
 */
public class GoPluginServices extends CIPluginServicesBase {

	private static final Logger Log = Logger.getLoggerFor(GoPluginServices.class);

	private OctaneGoCDPluginSettings settings;
	private String goServerID;
	private String goServerURL;

	public OctaneGoCDPluginSettings getSettings() {
		return settings;
	}

	public void setSettings(OctaneGoCDPluginSettings settings) {
		this.settings = settings;
	}

	public String getGoServerID() {
		return goServerID;
	}

	public void setGoServerID(String goServerID) {
		this.goServerID = goServerID;
	}

	public String getGoServerURL() {
		return goServerURL;
	}

	public void setGoServerURL(String goServerURL) {
		this.goServerURL = goServerURL;
	}

	public GoApiClient getGoApiClient() {
		try {
			return new GoApiClient(new URL(goServerURL), settings.getGoUsername(), settings.getGoPassword());
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException("Could not parse the given serverURL '" + goServerURL + "'", e);
		}
	}

	@Override
	public CIPluginInfo getPluginInfo() {
		return DTOFactory.getInstance().newDTO(CIPluginInfo.class).setVersion("1.0");
	}

	@Override
	public CIServerInfo getServerInfo() {
		return DTOFactory.getInstance().newDTO(CIServerInfo.class)
			.setUrl(goServerURL)
			.setType(CIServerTypes.UNKNOWN) // TODO change into CIServerTypes.GoCD as soon as available.
			.setSendingTime(System.currentTimeMillis())
			.setInstanceId(goServerID);
	}

	@Override
	public OctaneConfiguration getOctaneConfiguration() {
		int contextPathPosition = settings.getServerURL().indexOf("/ui");
		if (contextPathPosition < 0) {
			throw new IllegalArgumentException("URL does not conform to the expected format");
		}
		final String baseURL = settings.getServerURL().substring(0, contextPathPosition);

		final URL url;
		try {
			url = new URL(settings.getServerURL());
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException("Could not parse serverURL '" + settings.getServerURL() + "'");
		}

		// find the id of the shared space.
		for (String param : url.getQuery().split("&")) {
			if (param.startsWith("p=")) {
				String[] spaces = param.substring(2).split("/");
				if (spaces.length < 1 || spaces[0].isEmpty()) {
					throw new IllegalArgumentException("sharedSpace ID must be present value of parameter p");
				}
				return DTOFactory.getInstance().newDTO(OctaneConfiguration.class)
					.setUrl(baseURL)
					.setSharedSpace(spaces[0])
					.setApiKey(settings.getClientID())
					.setSecret(settings.getClientSecret());
			}
		}
		throw new IllegalArgumentException("URL must contain parameter p with IDs for sharedSpace and workspace");
	}

	@Override
	public CIProxyConfiguration getProxyConfiguration(final String targetHost) {
		Log.info("proxy configuration requested for host '" + targetHost + "'");
		try {
			final String protocol = new URL(targetHost).getProtocol();
			if (System.getProperty(protocol + ".proxyHost") != null) { // is a proxy defined for this protocol?
				Log.info("using proxy " + System.getProperty(protocol + ".proxyHost"));
				return DTOFactory.getInstance().newDTO(CIProxyConfiguration.class)
					.setHost(System.getProperty(protocol + ".proxyHost"))
					.setPort(Integer.valueOf(System.getProperty(protocol + ".proxyPort")))
					.setUsername(System.getProperty(protocol + ".proxyUser"))
					.setPassword(System.getProperty(protocol + ".proxyPassword"));
			} else {
				Log.info("using no proxy");
			}
		} catch (MalformedURLException e) {
			Log.error("Could not parse given targetHost as URL. Proceeding with using no proxy configuration.");
		}
		return null; // use no proxy
	}

	@Override
	public CIJobsList getJobsList(boolean includeParameters) {
		List<PipelineNode> pipelineNodes = new ArrayList<>();
		for (GoPipelineGroup group : new GoGetPipelineGroups(getGoApiClient()).get()) {
			for (GoPipeline pipeline : group.getPipelines()) {
				pipelineNodes.add(DTOFactory.getInstance().newDTO(PipelineNode.class)
					.setJobCiId(pipeline.getName())
					.setName(pipeline.getName()));
			}
		}
		return DTOFactory.getInstance().newDTO(CIJobsList.class)
			.setJobs(pipelineNodes.toArray(new PipelineNode[pipelineNodes.size()]));
	}

	@Override
	public PipelineNode getPipeline(final String rootCIJobId) {
		if (rootCIJobId == null || rootCIJobId.isEmpty()) {
			throw new IllegalArgumentException("no pipeline identifier was given");
		}
		final GoPipelineConfig config = new GoGetPipelineConfig(getGoApiClient()).get(rootCIJobId);
		if (config == null) {
			return null;
		}
		return DTOFactory.getInstance().newDTO(PipelineNode.class)
			.setJobCiId(config.getName())
			.setName(config.getName())
			.setPhasesInternal(Collections.singletonList(DTOFactory.getInstance().newDTO(PipelinePhase.class)
				.setName("stages")
				.setBlocking(true)
				.setJobs(ListConverter.convert(config.getStages(), new Converter<GoStageConfig, PipelineNode>() {
					@Override
					public PipelineNode convert(GoStageConfig stage) {
						return DTOFactory.getInstance().newDTO(PipelineNode.class)
							.setJobCiId(stage.getName())
							.setName(stage.getName())
							.setPhasesInternal(Collections.singletonList(DTOFactory.getInstance().newDTO(PipelinePhase.class)
								.setName("jobs")
								.setBlocking(true)
								.setJobs(ListConverter.convert(stage.getJobs(), new Converter<GoJobConfig, PipelineNode>() {
									@Override
									public PipelineNode convert(GoJobConfig jobConfig) {
										return DTOFactory.getInstance().newDTO(PipelineNode.class)
											.setJobCiId(jobConfig.getName())
											.setName(jobConfig.getName());
									}
								}))));

					}
				}))));
	}

	@Override
	public SnapshotNode getSnapshotLatest(String ciJobId, boolean subTree) {
		final List<GoPipelineInstance> instances = new GoGetPipelineHistory(getGoApiClient()).get(ciJobId);
		if (instances == null || instances.isEmpty()) {
			return null;
		}
		final GoPipelineInstance instance = instances.get(0);
		final boolean allStagesSuccessful = ListChecker.check(instance.getStages(), new Checker<GoStageInstance>() {
			@Override
			public boolean check(GoStageInstance goStageInstance) {
				return "Passed".equals(goStageInstance.getResult());
			}
		});
		return DTOFactory.getInstance().newDTO(SnapshotNode.class)
			.setJobCiId(ciJobId)
			.setName(ciJobId)
			.setBuildCiId(instance.getId())
			.setNumber(String.valueOf(instance.getCounter()))
			.setResult(allStagesSuccessful ? CIBuildResult.SUCCESS : CIBuildResult.FAILURE)
			.setStatus(CIBuildStatus.FINISHED)
			.setStartTime(instance.getFirstScheduledDate())
			.setPhasesInternal(Collections.singletonList(DTOFactory.getInstance().newDTO(SnapshotPhase.class)
				.setName("stages")
				.setBlocking(true)
				.setBuilds(ListConverter.convert(instance.getStages(), new Converter<GoStageInstance, SnapshotNode>() {
					@Override
					public SnapshotNode convert(GoStageInstance stageInstance) {
						return DTOFactory.getInstance().newDTO(SnapshotNode.class)
							.setJobCiId(stageInstance.getName())
							.setName(stageInstance.getName())
							.setBuildCiId(String.valueOf(stageInstance.getId()))
							.setNumber(stageInstance.getCounter())
							.setResult("Passed".equals(stageInstance.getResult()) ? CIBuildResult.SUCCESS : CIBuildResult.FAILURE)
							.setStatus(CIBuildStatus.FINISHED)
							.setStartTime(stageInstance.getFirstScheduledDate())
							.setPhasesInternal(Collections.singletonList(DTOFactory.getInstance().newDTO(SnapshotPhase.class)
								.setName("jobs")
								.setBlocking(true)
								.setBuilds(ListConverter.convert(stageInstance.getJobs(), new Converter<GoJobInstance, SnapshotNode>() {
									@Override
									public SnapshotNode convert(GoJobInstance jobInstance) {
										return DTOFactory.getInstance().newDTO(SnapshotNode.class)
											.setJobCiId(jobInstance.getName())
											.setName(jobInstance.getName())
											.setBuildCiId(String.valueOf(jobInstance.getId()))
											.setResult("Passed".equals(jobInstance.getResult()) ? CIBuildResult.SUCCESS : CIBuildResult.FAILURE)
											.setStatus(CIBuildStatus.FINISHED)
											.setStartTime(jobInstance.getScheduledDate());
									}
								}))));
					}
				}))));
	}
}
