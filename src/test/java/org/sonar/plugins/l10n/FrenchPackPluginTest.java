/*
 * French Pack for SonarQube
 * Copyright (C) 2011-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.plugins.l10n;

import org.junit.Assert;
import org.junit.Test;
import org.sonar.api.Plugin;
import org.sonar.api.SonarEdition;
import org.sonar.api.SonarQubeSide;
import org.sonar.api.SonarRuntime;
import org.sonar.api.internal.PluginContextImpl;
import org.sonar.api.internal.SonarRuntimeImpl;
import org.sonar.api.utils.Version;
import org.sonar.test.i18n.I18nMatchers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.test.i18n.BundleSynchronizedMatcher.L10N_PATH;

public class FrenchPackPluginTest {
  @Test
  public void testFrenchPackPluginName() {
    FrenchPackPlugin frenchPackPlugin = new FrenchPackPlugin();
    String pluginName = frenchPackPlugin.toString();
    Assert.assertEquals("FrenchPackPlugin", pluginName);
  }

  @Test
  public void noExtensions() {
    FrenchPackPlugin frenchPackPlugin = new FrenchPackPlugin();
    SonarRuntime runtime = SonarRuntimeImpl.forSonarQube(Version.create(9, 8),
            SonarQubeSide.SCANNER, SonarEdition.COMMUNITY);
    Plugin.Context context = new PluginContextImpl.Builder().setSonarRuntime(runtime).build();
    frenchPackPlugin.define(context);

    assertThat(context.getExtensions()).isEmpty();
  }

  @Test
  public void bundles_should_be_up_to_date() {
    I18nMatchers.assertBundlesUpToDate();
  }

  @Test
  public void non_acsii_character_should_be_escaped() throws IOException {
    try (BufferedReader lineReader = new BufferedReader(new InputStreamReader(
            Objects.requireNonNull(FrenchPackPlugin.class.getResourceAsStream(L10N_PATH + "core_fr.properties")),
            StandardCharsets.ISO_8859_1
    ))) {
      String line;
      boolean matched = false;
      while ((line = lineReader.readLine()) != null) {
        if (line.startsWith("login.login_to_sonarqube=")) {
          matched = true;
          assertThat(line).isEqualTo("login.login_to_sonarqube=Connexion \\u00E0 SonarQube");
        }
      }
      assertThat(matched).isTrue();
    }
  }
}
