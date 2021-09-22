/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.AppServerBridge;
import io.opentelemetry.instrumentation.servlet.v3_0.Servlet3HttpServerTracer;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LibertyHttpServerTracer extends Servlet3HttpServerTracer {
  private static final LibertyHttpServerTracer TRACER = new LibertyHttpServerTracer();

  public static LibertyHttpServerTracer tracer() {
    return TRACER;
  }

  public Context startSpan(HttpServletRequest request, HttpServletResponse response) {
    return startSpan(request, response, "HTTP " + request.getMethod(), /* servlet= */ false);
  }

  @Override
  protected Context customizeContext(Context context, HttpServletRequest httpServletRequest) {
    context = super.customizeContext(context, httpServletRequest);
    return AppServerBridge.init(context);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.liberty";
  }
}
