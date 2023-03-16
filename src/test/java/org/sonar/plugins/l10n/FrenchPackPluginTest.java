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

import org.assertj.core.api.SoftAssertions;
import org.junit.Assert;
import org.junit.Before;
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
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.test.i18n.BundleSynchronizedMatcher.L10N_PATH;

public class FrenchPackPluginTest {
	private static final String RESOURCE_BUNDLE = "org.sonar.l10n.core";
	private ResourceBundle base;
	private ResourceBundle translated;

	@Before
	public void init() throws IOException {
		base = ResourceBundle.getBundle(RESOURCE_BUNDLE, new Locale(""));
		assertThat(base).isNotNull();
		assertThat(base.getString("anonymous"))
				.describedAs("Label must be in english")
				.isEqualTo("Anonymous");
		translated = ResourceBundle.getBundle(RESOURCE_BUNDLE, Locale.FRENCH);
		assertThat(translated).isNotNull();
		assertThat(translated.getString("anonymous"))
				.describedAs("Label must be in french")
				.isEqualTo("Anonyme");
	}

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
					// This test must be executed with Maven because only 'native2ascii-maven-plugin' plugin escape characters
					assertThat(line).isEqualTo("login.login_to_sonarqube=Connexion \\u00E0 SonarQube");
				}
			}
			assertThat(matched).isTrue();
		}
	}

	private static final Pattern REGEX_START_SPACE = Pattern.compile("^(?<space>\\s*)(?<value>.*?)$");

	@Test
	public void start_spaces_should_remain() {
		SoftAssertions assertions = new SoftAssertions();
		base.keySet().stream()
				.forEach(key -> {
					Matcher translatedMatcher = REGEX_START_SPACE.matcher(translated.getString(key));
					Matcher baseMatcher = REGEX_START_SPACE.matcher(base.getString(key));
					assertions.assertThat(translatedMatcher.find()).isTrue();
					assertions.assertThat(baseMatcher.find()).isTrue();
					assertions.assertThat(translatedMatcher.group("space"))
							.describedAs("Start spaces should match for key: " + key)
							.isEqualTo(baseMatcher.group("space"));
				});
		assertions.assertAll();
	}

	private static final Pattern REGEX_END_SPACE = Pattern.compile("^(?<value>.*?)(?<space>\\s*)$");

	@Test
	public void end_spaces_should_remain() {
		SoftAssertions assertions = new SoftAssertions();
		base.keySet().stream()
				.forEach(key -> {
					Matcher translatedMatcher = REGEX_END_SPACE.matcher(translated.getString(key));
					Matcher baseMatcher = REGEX_END_SPACE.matcher(base.getString(key));
					assertions.assertThat(translatedMatcher.find()).isTrue();
					assertions.assertThat(baseMatcher.find()).isTrue();
					assertions.assertThat(translatedMatcher.group("space"))
							.describedAs("End spaces should match for key: " + key)
							.isEqualTo(baseMatcher.group("space"));
				});
		assertions.assertAll();
	}

	private List<String> getKeysToRemove() {
		var baseKeys = base.keySet();
		return translated.keySet().stream()
				.filter(k -> !baseKeys.contains(k))
				.collect(Collectors.toList());
	}

	private Stream<String> readLines(String path) throws IOException {
		var url = ClassLoader.getSystemResource(path);
		assertThat(url).isNotNull();
		try (var input = new BufferedReader(new InputStreamReader(url.openStream()))) {
			String line = null;
			List<String> result = new ArrayList<>();
			while ((line = input.readLine()) != null) {
				result.add(line);
			}
			return result.stream();
		}
	}

	@Test
	public void non_existent_key_should_be_marked_with_TODO_to_remove_comment() throws IOException {
		var toRemove = getKeysToRemove();
		SoftAssertions assertions = new SoftAssertions();
		final AtomicReference<String> previousLine = new AtomicReference<>("");
		readLines(RESOURCE_BUNDLE.replace('.', '/') + "_fr.properties")
				.forEach(line -> {
					final String finalLine = line;
					Optional<String> matchedKeyToCheck = toRemove.stream().filter(k -> finalLine.startsWith(k + "=")).findFirst();
					if (matchedKeyToCheck.isPresent()) {
						assertions.assertThat(previousLine.get())
								.describedAs("Key must be mark to remove: " + matchedKeyToCheck.get())
								.isEqualTo("# //TODO: To remove");
					}
					previousLine.set(line);
				});

		assertions.assertAll();
	}

	@Test
	public void start_case_should_matches_for_letter() {
		SoftAssertions assertions = new SoftAssertions();
		base.keySet().stream()
				.filter(key -> {
					var value = base.getString(key);
					return base.getString(key).length() > 0 &&
							// Exception for "Barrière Qualité" because must be capitalized
							!("quality gate".equalsIgnoreCase(value) && translated.getString(key).equals("Barrière Qualité"));
				})
				.forEach(key -> {
					var firstCharacterBase = base.getString(key).replaceFirst("^\\s+", "").charAt(0);
					var firstCharacterTranslated = translated.getString(key).replaceFirst("^\\s+", "").charAt(0);

					Boolean baseIsLower = null;
					Boolean translatedIsLower = null;

					// if non alpha character, both isLowerCase and isUpperCase == false
					if (Character.isLowerCase(firstCharacterBase) || Character.isUpperCase(firstCharacterBase)) {
						baseIsLower = Character.isLowerCase(firstCharacterBase);
					}
					if (Character.isLowerCase(firstCharacterTranslated) || Character.isUpperCase(firstCharacterTranslated)) {
						translatedIsLower = Character.isLowerCase(firstCharacterTranslated);
					}
					if (baseIsLower != null && translatedIsLower != null) {
						assertions.assertThat(translatedIsLower)
								.describedAs("First character case must match for: " + key)
								.isEqualTo(baseIsLower);
					}
				});
		assertions.assertAll();
	}
}
