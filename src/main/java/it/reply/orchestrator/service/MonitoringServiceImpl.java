/*
 * Copyright © 2015-2018 Santer Reply S.p.A.
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

package it.reply.orchestrator.service;

import it.reply.monitoringpillar.domain.dsl.monitoring.pillar.wrapper.paas.PaaSMetric;
import it.reply.orchestrator.config.properties.MonitoringProperties;
import it.reply.orchestrator.dto.monitoring.MonitoringResponse;
import it.reply.orchestrator.exception.service.DeploymentException;
import it.reply.orchestrator.utils.CommonUtils;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import javax.ws.rs.core.UriBuilder;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
@EnableConfigurationProperties(MonitoringProperties.class)
public class MonitoringServiceImpl implements MonitoringService {

  private MonitoringProperties monitoringProperties;

  private RestTemplate restTemplate;

  public MonitoringServiceImpl(MonitoringProperties monitoringProperties,
      RestTemplateBuilder restTemplateBuilder) {
    this.monitoringProperties = monitoringProperties;
    this.restTemplate = restTemplateBuilder.build();
  }

  @Override
  public List<PaaSMetric> getProviderData(String providerId) {

    URI requestUri = UriBuilder
        .fromUri(monitoringProperties.getUrl() + monitoringProperties.getProviderMetricsPath())
        .build(providerId)
        .normalize();

    try {
      ResponseEntity<MonitoringResponse> response =
          restTemplate.getForEntity(requestUri, MonitoringResponse.class);
      return Optional
          .ofNullable(response.getBody().getResult())
          .map(CommonUtils::checkNotNull)
          .map(result -> result.getGroups())
          .flatMap(groups -> groups.stream().findFirst())
          .map(group -> group.getPaasMachines())
          .flatMap(paasMachines -> paasMachines.stream().findFirst())
          .map(paasMachine -> paasMachine.getServices())
          .flatMap(services -> services.stream().findFirst())
          .map(service -> service.getPaasMetrics())
          .orElseThrow(() -> new DeploymentException(
              "No metrics available for provider <" + providerId + ">"));
    } catch (RestClientException ex) {
      throw new DeploymentException(
          "Error fetching monitoring data for provider <" + providerId + ">", ex);
    }
  }

}
