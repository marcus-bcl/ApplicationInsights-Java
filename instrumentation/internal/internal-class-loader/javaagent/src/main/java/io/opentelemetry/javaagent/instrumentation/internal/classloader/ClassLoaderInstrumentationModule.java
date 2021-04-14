/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.classloader;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ClassLoaderInstrumentationModule extends InstrumentationModule {
  public ClassLoaderInstrumentationModule() {
    super("internal-class-loader");
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.equals("io.opentelemetry.javaagent.tooling.Constants");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new ClassLoaderInstrumentation(), new ResourceInjectionInstrumentation());
  }
}
