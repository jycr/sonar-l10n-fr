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

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.PropertiesConfigurationLayout;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.sonar.test.i18n.BundleSynchronizedMatcher;

import com.google.common.io.Files;

class L10nHackyPropertiesUpdater
{
	// TODO : mojo'ify and/or externalize some part of BundleSynchronizedMatcher if that proves useful
	public static void main(String[] args) throws ConfigurationException, IOException
	{
		System.setProperty("file.encoding", "UTF-8");
		URL l10nRoot = FrenchPackPlugin.class.getResource(BundleSynchronizedMatcher.L10N_PATH);

		Collection<File> bundles = FileUtils.listFiles(FileUtils.toFile(l10nRoot), new String[] { "properties" },
				false);

		for (File localizedBundle : bundles)
		{
			String originalVersion = localizedBundle.getName().replaceFirst("_fr\\.", ".");
			System.out.println("Processing " + localizedBundle + " looking for " + originalVersion);
			URL originalBundle = FrenchPackPlugin.class.getResource(BundleSynchronizedMatcher.L10N_PATH + originalVersion);
			if (originalBundle == null)
			{
				System.out.println("\tOriginal bundle not found");
			}
			else
			{
				System.out.println("\tOriginal bundle found, let's try to update the localized version");
				Properties localizedProps = new Properties();
				FileInputStream localizedBundleFile = new FileInputStream(localizedBundle);
				localizedProps.load(localizedBundleFile);
				
				PropertiesConfiguration config = new PropertiesConfiguration();
				config.setEncoding("UTF-8");
				PropertiesConfigurationLayout layout = new PropertiesConfigurationLayout(config);
				layout.load(new InputStreamReader(FrenchPackPlugin.class.
						getResourceAsStream(BundleSynchronizedMatcher.L10N_PATH + originalVersion)));

				for (@SuppressWarnings("unchecked")
				Iterator<String> it = config.getKeys(); it.hasNext();)
				{
					String key = it.next();
					Object localizedValue = localizedProps.get(key);
					if (localizedValue != null)
					{
						config.setProperty(key, localizedValue);
					}
					else
					{
						System.out.println("Nothing found for " + key);
						String currentValue = config.getString(key);
						config.setProperty(key, currentValue + " <== TODO");
					}
				}

				layout.save(new FileWriter(localizedBundle));
				
				System.out.println("\tFixing spaces");
				fixSpacesAroundEqualsAndScrewUpEncoding(localizedBundle);
				System.out.println("OK: file " + localizedBundle + " contains ready-to-translate updated file.");
			}
		}
		
	}

	// Ugly hack because Commons-config isn't configurable about spaces around the "key = value" and the
	// SQ bundles are all written "key=value"... So basically replacing " = " by "="
	private static void fixSpacesAroundEqualsAndScrewUpEncoding(File localizedBundle) throws IOException
	{
		String lines = Files.toString(localizedBundle, Charset.forName("ISO-8859-1"));
		lines = lines.replaceAll(" = ", "=");
		lines = StringEscapeUtils.unescapeJava(lines);
		Files.write(lines, localizedBundle, Charset.forName("ISO-8859-1"));
	}
}
