package it.reply.orchestrator.service.commands;

/*
 * Copyright © 2015-2017 Santer Reply S.p.A.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.UUID;

import org.drools.core.process.instance.impl.WorkItemImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.kie.api.executor.CommandContext;
import org.kie.api.executor.ExecutionResults;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import it.reply.orchestrator.controller.ControllerTestUtils;
import it.reply.orchestrator.dal.entity.Deployment;
import it.reply.orchestrator.dto.CloudProviderEndpoint;
import it.reply.orchestrator.dto.deployment.DeploymentMessage;
import it.reply.orchestrator.enums.NodeStates;
import it.reply.orchestrator.enums.Status;
import it.reply.orchestrator.service.WorkflowConstants;
import it.reply.orchestrator.service.deployment.providers.DeploymentProviderService;
import it.reply.workflowmanager.utils.Constants;

public class UndeployTest {

  @InjectMocks
  Undeploy undeploy = new Undeploy();

  @Mock
  private DeploymentProviderService imService;


  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testCustomExecute() throws Exception {
    DeploymentMessage dm = generateDeployDm();
    dm.setDeleteComplete(true);

    CommandContext commandContext = new CommandContext();

    WorkItemImpl workItem = new WorkItemImpl();
    workItem.setParameter(WorkflowConstants.WF_PARAM_TOSCA_TEMPLATE, "template");
    commandContext.setData(Constants.WORKITEM, workItem);
    Mockito.when(imService.doUndeploy(dm)).thenReturn(true);
    Mockito.doNothing().when(imService).finalizeUndeploy(Mockito.anyObject(), Mockito.anyBoolean());

    ExecutionResults expectedResult = new ExecutionResults();
    expectedResult.setData(Constants.RESULT_STATUS, "OK");
    expectedResult.setData(Constants.OK_RESULT, true);

    ExecutionResults customExecute = undeploy.customExecute(commandContext, dm);

    Assert.assertEquals(expectedResult.getData(Constants.RESULT_STATUS),
        customExecute.getData(Constants.RESULT_STATUS));
    Assert.assertEquals(expectedResult.getData(Constants.OK_RESULT),
        customExecute.getData(Constants.OK_RESULT));
    Assert.assertEquals(undeploy.getErrorMessagePrefix(), "Error undeploying through IM");
  }

  private DeploymentMessage generateDeployDm() {
    DeploymentMessage dm = new DeploymentMessage();
    Deployment deployment = ControllerTestUtils.createDeployment();
    deployment.setStatus(Status.CREATE_IN_PROGRESS);
    dm.setDeployment(deployment);
    dm.setDeploymentId(deployment.getId());
    deployment.getResources().addAll(ControllerTestUtils.createResources(deployment, 2, false));
    deployment.getResources().stream().forEach(r -> r.setState(NodeStates.CREATING));

    CloudProviderEndpoint chosenCloudProviderEndpoint = new CloudProviderEndpoint();
    chosenCloudProviderEndpoint.setCpComputeServiceId(UUID.randomUUID().toString());
    dm.setChosenCloudProviderEndpoint(chosenCloudProviderEndpoint);
    return dm;
  }
}