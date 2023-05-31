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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.test.i18n.BundleSynchronizedMatcher.L10N_PATH;

public class FrenchPackPluginTest {
	private static final String RESOURCE_BUNDLE = "org.sonar.l10n.core";
	private static final String RESOURCE_BUNDLE_PATH_CORE = RESOURCE_BUNDLE.replace('.', '/') + ".properties";
	private static final String RESOURCE_BUNDLE_PATH_TRANSLATED = RESOURCE_BUNDLE.replace('.', '/') + "_fr.properties";
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

	private Optional<String> matchKeyToRemove(List<String> getKeysToRemove, String line) {
		return getKeysToRemove.stream().filter(k -> line.startsWith(k + "=")).findFirst();
	}

	@Test
	public void non_existent_key_should_be_marked_with_TODO_to_remove_comment() throws IOException {
		var toRemove = getKeysToRemove();
		SoftAssertions assertions = new SoftAssertions();
		final AtomicReference<String> previousLine = new AtomicReference<>("");
		readLines(RESOURCE_BUNDLE_PATH_TRANSLATED)
				.forEach(line -> {
					Optional<String> matchedKeyToCheck = matchKeyToRemove(toRemove, line);
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
					return base.getString(key).trim().length() > 0 &&
							// Exception for "Barrière Qualité" because must be capitalized
							!("quality gate".equalsIgnoreCase(value) && translated.getString(key).equals("Barrière Qualité"));
				})
				.forEach(key -> {
					var firstCharacterBase = base.getString(key).trim().charAt(0);
					var firstCharacterTranslated = translated.getString(key).trim().charAt(0);

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

	private static class OccurenceToKeep {
		private final int indexToKeep;
		private int currentIndex = 0;

		OccurenceToKeep(int indexToKeep) {
			this.indexToKeep = indexToKeep;
		}

		boolean isToIgnore() {
			return ++currentIndex != this.indexToKeep;
		}

		static OccurenceToKeep KEEP_ALL = new OccurenceToKeep(-1) {
			@Override
			boolean isToIgnore() {
				return false;
			}
		};
	}

	@Test
	public void translated_file_structure_shoud_be_same_as_base() throws IOException {
		final List<String> keysToRemove = getKeysToRemove();
		Pattern keyPattern = Pattern.compile("^([^#=]+=).*$");

		// Set line position for bad key in source file
		Map<String, OccurenceToKeep> badSourceKey = Map.of(
				"show_all=", new OccurenceToKeep(2),
				"marketplace.installed=", new OccurenceToKeep(2),
				"users.update=", new OccurenceToKeep(2)
		);
		final String expected = readLines(RESOURCE_BUNDLE_PATH_CORE)
				.map(line -> {
					var matcher = keyPattern.matcher(line);
					var normalizedLine = line;
					if (matcher.matches()) {
						normalizedLine = matcher.replaceFirst("$1");
					}
					if (badSourceKey.getOrDefault(normalizedLine, OccurenceToKeep.KEEP_ALL).isToIgnore()) {
						return null;
					}
					return normalizedLine;
				})
				.filter(Objects::nonNull)
				.map(String::trim)
				.collect(Collectors.joining("\n"));
		final String result = readLines(RESOURCE_BUNDLE_PATH_TRANSLATED)
				.filter(line -> !line.equals("# //TODO: To remove") && matchKeyToRemove(keysToRemove, line).isEmpty())
				.map(line -> {
					var matcher = keyPattern.matcher(line);
					if (matcher.matches()) {
						return matcher.replaceFirst("$1");
					}
					return line;
				})
				.map(String::trim)
				.collect(Collectors.joining("\n"));

		assertThat(result).isEqualTo(expected);
	}

	private static final Pattern NON_BREAKING_SPACE_PUNCTUATIONS_WITHOUT_NON_BREAKING_SPACE = Pattern.compile(".*[^ ][:;!?].*");
	private static final Pattern NON_SPACE_PUNCTUATIONS_WITH_SPACE_BEFORE = Pattern.compile(".*\\s[.,…].*");

	// NB: No space
	private static final Pattern SPACE_AFTER_PUNCTUATIONS = Pattern.compile(".*\\s([.,:;!?]\\S|…[^\\s,])");

	@Test
	public void check_punctuation() {
		SoftAssertions assertions = new SoftAssertions();
		translated.keySet().stream()
				.filter(key -> NON_BREAKING_SPACE_PUNCTUATIONS_WITHOUT_NON_BREAKING_SPACE
						.matcher(
								translated.getString(key)
										// Ignore URL
										.replaceAll("https?://(\\w+:\\w+@)?", "")
										// Ignore string used for sample
										.replace("':'", "")
						)
						.matches()
				)
				.forEach(key -> assertions
						.fail("Punctuation with non breaking space must be preceded with non-breaking space for key '" + key + "': " + translated.getString(key))
				);
		translated.keySet().stream()
				.filter(key -> NON_SPACE_PUNCTUATIONS_WITH_SPACE_BEFORE.matcher(
								translated.getString(key)
										// Ignore ".NET" trademark
										.replace(".NET", "")
										// Ignore extension
										.replaceAll("\\.[a-z]{3}(\\W)", "$1")
						).matches()
				)
				.forEach(key -> assertions
						.fail("Punctuation without space before must not be preceded with a space for key '" + key + "': " + translated.getString(key))
				);
		translated.keySet().stream()
				.filter(key -> SPACE_AFTER_PUNCTUATIONS.matcher(
								translated.getString(key)
						).matches()
				)
				.forEach(key -> assertions
						.fail("Punctuation must be followed by space for key '" + key + "': " + translated.getString(key))
				);
	}

	private static final Pattern TERMINAL_PUNCTUATION = Pattern.compile("^.*?(?<terminalPunctuation>[,;.:?!…]?)$");
	private static final Pattern ABBREVIATION = Pattern.compile("(Coef|Préc|Suiv)\\.", Pattern.CASE_INSENSITIVE);

	@Test
	public void terminal_punctuation_should_be_same_as_core() {
		final List<String> dayOfWeekAbbreviations = List.of(
				"Sun",
				"Mon",
				"Tue",
				"Wed",
				"Thu",
				"Fri",
				"Sat",
				"Su",
				"Mo",
				"Tu",
				"We",
				"Th",
				"Fr",
				"Sa"
		);
		SoftAssertions assertions = new SoftAssertions();

		base.keySet()
				.stream()
				.filter(key ->
						// Abbreviation must be followed by stop point
						!ABBREVIATION.matcher(translated.getString(key)).matches()
				)
				.forEach(key -> {
					if (dayOfWeekAbbreviations.contains(key)) {
						assertions.assertThat(translated.getString(key))
								.describedAs("day of week abbreviations must ends with dot for key:" + key)
								.endsWith(".");
					} else {
						final String baseValue = base.getString(key).trim()
								// Replace three dots punctuation with real unicode symbol
								.replace("...", "…");
						var baseMatcher = TERMINAL_PUNCTUATION.matcher(baseValue);
						assertThat(baseMatcher.find()).isTrue();
						var baseTerminalPunctuation = baseMatcher.group("terminalPunctuation");

						final String translatedValue = translated.getString(key).trim();
						var translatedMatcher = TERMINAL_PUNCTUATION.matcher(translatedValue);
						assertThat(translatedMatcher.find()).isTrue();
						var translatedTerminalPunctuation = translatedMatcher.group("terminalPunctuation");

						assertions.assertThat(translatedTerminalPunctuation)
								.describedAs("Terminal punctuation must match for key: " + key)
								.isEqualTo(baseTerminalPunctuation);
					}
				});
		assertions.assertAll();
	}

	private static final Pattern NON_EPICENE_TERMS = Pattern.compile(
			"administrateur|utilisateur|développeur|auteur|((^|\\W)êtes)",
			Pattern.CASE_INSENSITIVE
	);

	@Test
	public void should_not_use_non_epicene_term() {
		SoftAssertions assertions = new SoftAssertions();
		translated.keySet().stream()
				.filter(key -> NON_EPICENE_TERMS.matcher(translated.getString(key)).find())
				.forEach(key -> {
					Matcher matcher = NON_EPICENE_TERMS.matcher(translated.getString(key));
					while (matcher.find()) {
						assertions.fail("Non-epicene term '" + matcher.group(0) + "' must not be used for key: " + key);
					}
				});
		assertions.assertAll();
	}

	private static final Pattern QUALITY_GATE = Pattern.compile("Barri(e|è)(?<plural>s?)\\s.*?Qualit(e|é)(s?)", Pattern.CASE_INSENSITIVE);
	private static final Pattern PORTFOLIO = Pattern.compile("(Portfolio|portefeuille)(?<plural>s?)", Pattern.CASE_INSENSITIVE);
	private static final Pattern QUALITY_PROFIL = Pattern.compile("Profil(?<plural>s?).*?Qualité", Pattern.CASE_INSENSITIVE);

	@Test
	public void right_term_must_be_used() {
		SoftAssertions assertions = new SoftAssertions();
		translated.keySet().stream()
				.filter(key -> QUALITY_GATE.matcher(translated.getString(key)).find())
				.forEach(key -> {
					Matcher matcher = QUALITY_GATE.matcher(translated.getString(key));
					while (matcher.find()) {
						assertions.assertThat(matcher.group(0))
								.describedAs("'Barrière(s) Qualité(s)' must be written in the right form for key: " + key)
								.isEqualTo("Barrière" + ofNullable(matcher.group("plural")).orElse("") + " Qualité");
					}
				});
		translated.keySet().stream()
				.filter(key -> PORTFOLIO.matcher(translated.getString(key)).find())
				.forEach(key -> {
					Matcher matcher = PORTFOLIO.matcher(translated.getString(key));
					while (matcher.find()) {
						assertions.assertThat(matcher.group(0))
								.describedAs("'Portfolio(s)' must be written in the right form for key: " + key)
								.isEqualTo("Portfolio" + ofNullable(matcher.group("plural")).orElse(""));
					}
				});
		assertions.assertAll();
	}

	/*
	 * Can match complex placeholder definition.
	 * <p>
	 * For example:
	 * {warningsCount} {warningsCount, plural, one {warning} other {warnings}}
	 */
	private static final Pattern REGEX_PLACEHOLDER = Pattern.compile("\\{(?<name>[^{}]+)(, plural, one \\{[^{}]+} other \\{[^{}]+})?}");


	private TreeSet<String> extractPlaceHolders(String value) {
		var matcher = REGEX_PLACEHOLDER.matcher(value);
		TreeSet<String> result = new TreeSet<String>();
		while (matcher.find()) {
			result.add(matcher.group(1));
		}
		return result;
	}

	@Test
	public void placeholders_must_have_same_name() {
		SoftAssertions assertions = new SoftAssertions();
		base.keySet().forEach(key -> {
			assertions
					.assertThat(extractPlaceHolders(translated.getString(key)))
					.describedAs("Placeholder name(s) should be the same for key: " + key)
					.isEqualTo(extractPlaceHolders(base.getString(key)));
		});
		assertions.assertAll();
	}

	@Test
	public void line_endings_must_be_linux_one() throws IOException {
		SoftAssertions assertions = new SoftAssertions();
		var url = ClassLoader.getSystemResource(RESOURCE_BUNDLE_PATH_TRANSLATED);
		assertThat(url).isNotNull();
		try (var reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
			int charIntValue;
			int line = 1;
			while ((charIntValue = reader.read()) != -1) {
				char charValue = (char) charIntValue;
				if ('\n' == charValue) {
					++line;
				}
				if ('\r' == charValue) {
					assertions.fail("Bad line endings on line: " + line);
				}
			}
		}
		assertions.assertAll();
	}
}
