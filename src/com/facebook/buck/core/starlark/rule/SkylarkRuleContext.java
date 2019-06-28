/*
 * Copyright 2019-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.facebook.buck.core.starlark.rule;

import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.skylarkinterface.SkylarkPrinter;

/** The context passed to user defined rules' implementation functions */
public class SkylarkRuleContext implements SkylarkRuleContextApi {

  private final Label label;

  public SkylarkRuleContext(Label label) {
    this.label = label;
  }

  @Override
  public void repr(SkylarkPrinter printer) {
    printer.append("<ctx>");
  }

  @Override
  public Label getLabel() {
    return label;
  }
}
