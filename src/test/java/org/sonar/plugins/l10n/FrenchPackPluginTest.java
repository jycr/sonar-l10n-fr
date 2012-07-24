/*
 * L10n :: French Pack
 * Copyright (C) 2011 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.plugins.l10n;

import com.google.common.collect.Maps;
import org.junit.Test;

import java.util.Map;

import static org.sonar.test.i18n.I18nMatchers.assertAllBundlesUpToDate;

public class FrenchPackPluginTest {

  private static final String SONAR_VERSION = "3.0";
  private static final Map<String, String> pluginIdsToBundleUrlMap = Maps.newHashMap();

  static {
    // TODO charge URL of motion chart to the released 1.4 version and not to trunk
    pluginIdsToBundleUrlMap.put("motionchart", "http://svn.codehaus.org/sonar-plugins/trunk/motion-chart/src/main/resources/org/sonar/l10n/motionchart.properties");
  }

  @Test
  public void test() throws Exception {
    assertAllBundlesUpToDate(SONAR_VERSION, pluginIdsToBundleUrlMap);
  }

}
