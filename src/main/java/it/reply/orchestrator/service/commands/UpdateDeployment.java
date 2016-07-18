package it.reply.orchestrator.service.commands;

import com.google.common.collect.ImmutableMap;

import it.reply.orchestrator.dal.entity.Deployment;
import it.reply.orchestrator.dal.repository.DeploymentRepository;
import it.reply.orchestrator.dto.CloudProviderEndpoint;
import it.reply.orchestrator.dto.RankCloudProvidersMessage;
import it.reply.orchestrator.dto.deployment.DeploymentMessage;
import it.reply.orchestrator.dto.onedata.OneData;
import it.reply.orchestrator.dto.ranker.RankedCloudProvider;
import it.reply.orchestrator.service.CloudProviderEndpointServiceImpl;
import it.reply.orchestrator.service.WorkflowConstants;
import it.reply.orchestrator.service.deployment.providers.DeploymentStatusHelper;
import it.reply.workflowmanager.spring.orchestrator.bpm.ejbcommands.BaseCommand;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kie.api.executor.CommandContext;
import org.kie.api.executor.ExecutionResults;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Choose Cloud Provider and update Deployment/Message with the selected one data.
 * 
 * @author l.biava
 *
 */
@Component
@PropertySource("${chronos.auth.file.path:classpath:chronos/chronos.properties}")
public class UpdateDeployment extends BaseCommand {

  private static final Logger LOG = LogManager.getLogger(UpdateDeployment.class);

  @Value("${onedata.token}")
  private String token;
  @Value("${onedata.space}")
  private String space;
  @Value("${onedata.path:''}")
  private String path;
  @Value("${onedata.provider}")
  private String provider;

  @Autowired
  private DeploymentRepository deploymentRepository;

  @Autowired
  private DeploymentStatusHelper deploymentStatusHelper;

  @Autowired
  private CloudProviderEndpointServiceImpl cloudProviderEndpointServiceImpl;

  @Override
  public ExecutionResults customExecute(CommandContext ctx) throws Exception {

    RankCloudProvidersMessage rankCloudProvidersMessage =
        (RankCloudProvidersMessage) getWorkItem(ctx)
            .getParameter(WorkflowConstants.WF_PARAM_RANK_CLOUD_PROVIDERS_MESSAGE);
    DeploymentMessage deploymentMessage = (DeploymentMessage) getWorkItem(ctx)
        .getParameter(WorkflowConstants.WF_PARAM_DEPLOYMENT_MESSAGE);

    ExecutionResults exResults = new ExecutionResults();
    try {
      if (rankCloudProvidersMessage == null) {
        throw new IllegalArgumentException(String.format("WF parameter <%s> cannot be null",
            WorkflowConstants.WF_PARAM_RANK_CLOUD_PROVIDERS_MESSAGE));
      }
      if (deploymentMessage == null) {
        throw new IllegalArgumentException(String.format("WF parameter <%s> cannot be null",
            WorkflowConstants.WF_PARAM_DEPLOYMENT_MESSAGE));
      }

      Deployment deployment =
          deploymentRepository.findOne(rankCloudProvidersMessage.getDeploymentId());

      // Choose Cloud Provider
      RankedCloudProvider chosenCp = cloudProviderEndpointServiceImpl
          .chooseCloudProvider(deployment, rankCloudProvidersMessage);

      // Set the chosen CP in deploymentMessage
      deploymentMessage.setChosenCloudProvider(
          rankCloudProvidersMessage.getCloudProviders().get(chosenCp.getName()));

      // Update Deployment
      deployment.setCloudProviderName(chosenCp.getName());

      // FIXME Set/update all required selected CP data

      // FIXME Generate CP Endpoint
      CloudProviderEndpoint chosenCloudProviderEndpoint = cloudProviderEndpointServiceImpl
          .getCloudProviderEndpoint(deployment, rankCloudProvidersMessage, chosenCp);
      deploymentMessage.setChosenCloudProviderEndpoint(chosenCloudProviderEndpoint);
      LOG.debug("Generated Cloud Provider Endpoint is: {}", chosenCloudProviderEndpoint);

      // FIXME Use another method to hold CP Endpoint (i.e. CMDB service ID reference?)
      // Save CPE in Deployment for future use
      deployment.setCloudProviderEndpoint(chosenCloudProviderEndpoint);

      // FIXME Implement OneData scheduling properly and move in a dedicated command
      generateOneDataParameters(deploymentMessage);

      exResults.getData().putAll(resultOccurred(true).getData());
      exResults.setData(WorkflowConstants.WF_PARAM_DEPLOYMENT_MESSAGE, deploymentMessage);
    } catch (Exception ex) {
      LOG.error(ex);
      exResults.getData().putAll(resultOccurred(false).getData());

      // Update deployment with error
      // TODO: what if this fails??
      deploymentStatusHelper.updateOnError(rankCloudProvidersMessage.getDeploymentId(), ex);
    }

    return exResults;
  }

  protected void generateOneDataParameters(DeploymentMessage deploymentMessage) {
    // Just copy requirements to parameters (in the future the Orchestrator will need to edit I/O
    // providers, but not for now)
    deploymentMessage.getOneDataParameters().putAll(deploymentMessage.getOneDataRequirements());

    // No Requirements -> Service space
    if (deploymentMessage.getOneDataRequirements().isEmpty()) {
      deploymentMessage
          .setOneDataParameters(ImmutableMap.of("service", generateStubOneData(deploymentMessage)));
      LOG.warn("GENERATING STUB ONE DATA FOR SERVICE"
          + " (remove once OneData parameters generation is completed!)");
    } else {
      LOG.debug("User specified I/O OneData requirements; service space will not be generated.");
    }
  }

  /**
   * Temporary method to generate default OneData settings.
   * 
   * @return the {@link OneData} settings.
   */
  protected OneData generateStubOneData(DeploymentMessage deploymentMessage) {

    String path = new StringBuilder().append(this.path).append(deploymentMessage.getDeploymentId())
        .toString();

    LOG.info(String.format("Generating OneData settings with parameters: %s",
        Arrays.asList(token, space, path, provider)));

    return new OneData(token, space, path, provider);
  }

}