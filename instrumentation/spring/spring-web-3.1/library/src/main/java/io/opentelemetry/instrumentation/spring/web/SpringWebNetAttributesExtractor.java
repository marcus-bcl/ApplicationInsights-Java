/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.web;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;

final class SpringWebNetAttributesExtractor
    extends NetClientAttributesExtractor<HttpRequest, ClientHttpResponse> {
  @Override
  public String transport(HttpRequest httpRequest, ClientHttpResponse response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  @Nullable
  public String peerName(HttpRequest httpRequest, ClientHttpResponse response) {
    return httpRequest.getURI().getHost();
  }

  @Override
  public Integer peerPort(HttpRequest httpRequest, ClientHttpResponse response) {
    return httpRequest.getURI().getPort();
  }

  @Override
  @Nullable
  public String peerIp(HttpRequest httpRequest, ClientHttpResponse response) {
    return null;
  }
}
