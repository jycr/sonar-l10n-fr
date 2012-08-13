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

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.sonar.test.i18n.I18nMatchers.assertAllBundlesUpToDate;

public class FrenchPackPluginTest {

  /**
   * Version of Sonar which is covered by the language pack
   */
  private static final String SONAR_VERSION = "3.0";

  /**
   * Bundles of the forge plugins covered by the language pack
   */
  private static final Map<String, String> pluginIdsToBundleUrlMap = new HashMap<String, String>() {
    {
      put("abacus", "http://svn.codehaus.org/sonar-plugins/tags/sonar-abacus-plugin-0.1/src/main/resources/org/sonar/l10n/abacus.properties");
      put("branding", "http://svn.codehaus.org/sonar-plugins/tags/sonar-branding-plugin-0.3/src/main/resources/org/sonar/l10n/branding.properties");
      put("motionchart", "http://svn.codehaus.org/sonar-plugins/tags/sonar-motion-chart-plugin-1.4/src/main/resources/org/sonar/l10n/motionchart.properties");
    }
  };

  @Test
  public void test() throws Exception {
    assertAllBundlesUpToDate(SONAR_VERSION, pluginIdsToBundleUrlMap);
  }

}
