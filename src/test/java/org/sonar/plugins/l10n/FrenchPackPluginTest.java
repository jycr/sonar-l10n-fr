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
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
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
	public void init() {
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

	/**
	 * The packaged bundle must be pure ASCII, every other character escaped.
	 *
	 * <p>Only {@code native2ascii-maven-plugin} does that, so this test is meaningful when
	 * run through Maven and vacuous otherwise -- it reads the processed copy in
	 * {@code target/classes}, not the source file.
	 *
	 * <p>It used to pin one translation, {@code login.login_to_sonarqube}, and assert its
	 * exact escaped form. That made an ordinary wording change look like an escaping
	 * regression. Asserting the property itself -- no byte above 127, and escapes actually
	 * present -- covers strictly more and survives translation work.
	 */
	@Test
	public void non_acsii_character_should_be_escaped() throws IOException {
		SoftAssertions assertions = new SoftAssertions();
		boolean escapeFound = false;
		try (BufferedReader lineReader = new BufferedReader(new InputStreamReader(
				Objects.requireNonNull(FrenchPackPlugin.class.getResourceAsStream(L10N_PATH + "core_fr.properties")),
				StandardCharsets.ISO_8859_1
		))) {
			String line;
			int lineNumber = 0;
			while ((line = lineReader.readLine()) != null) {
				++lineNumber;
				for (int i = 0; i < line.length(); i++) {
					if (line.charAt(i) > 127) {
						assertions.fail("Non-ASCII character at line " + lineNumber + ", column " + (i + 1)
								+ ": must be escaped by native2ascii-maven-plugin. Line: " + line);
						break;
					}
				}
				escapeFound = escapeFound || ESCAPED_CHARACTER.matcher(line).find();
			}
		}
		assertions.assertThat(escapeFound)
				.describedAs("The bundle is expected to contain escaped characters")
				.isTrue();
		assertions.assertAll();
	}

	private static final Pattern ESCAPED_CHARACTER = Pattern.compile("\\\\u[0-9a-fA-F]{4}");

	/** The characters {@code \\s} stands for in a regular expression. */
	private static final String SPACES = " \t\n\u000B\f\r";

	private static String leadingSpace(String value) {
		int end = 0;
		while (end < value.length() && SPACES.indexOf(value.charAt(end)) >= 0) {
			++end;
		}
		return value.substring(0, end);
	}

	private static String trailingSpace(String value) {
		int start = value.length();
		while (start > 0 && SPACES.indexOf(value.charAt(start - 1)) >= 0) {
			--start;
		}
		return value.substring(start);
	}

	@Test
	public void start_spaces_should_remain() {
		SoftAssertions assertions = new SoftAssertions();
		base.keySet().forEach(key -> assertions.assertThat(leadingSpace(translated.getString(key)))
				.describedAs("Start spaces should match for key: " + key)
				.isEqualTo(leadingSpace(base.getString(key))));
		assertions.assertAll();
	}

	@Test
	public void end_spaces_should_remain() {
		SoftAssertions assertions = new SoftAssertions();
		base.keySet().forEach(key -> assertions.assertThat(trailingSpace(translated.getString(key)))
				.describedAs("End spaces should match for key: " + key)
				.isEqualTo(trailingSpace(base.getString(key))));
		assertions.assertAll();
	}

	private List<String> getKeysToRemove() {
		var baseKeys = base.keySet();
		return translated.keySet().stream()
				.filter(k -> !baseKeys.contains(k))
				.toList();
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
					return !value.trim().isEmpty() &&
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

		final String expected = readLines(RESOURCE_BUNDLE_PATH_CORE)
				.map(line -> {
					var matcher = keyPattern.matcher(line);
					var normalizedLine = line;
					if (matcher.matches()) {
						normalizedLine = matcher.replaceFirst("$1");
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

	private static final Pattern NON_BREAKING_SPACE_PUNCTUATIONS_WITHOUT_NON_BREAKING_SPACE = Pattern.compile("[^\\u00A0][:;!?]");
	private static final Pattern NON_SPACE_PUNCTUATIONS_WITH_SPACE_BEFORE = Pattern.compile("\\s[.,…]");

	// NB: No space
	private static final Pattern SPACE_AFTER_PUNCTUATIONS = Pattern.compile("\\s([.,:;!?]\\S|…[^\\s,])");

	/**
	 * Keys quoting English text the user must find verbatim elsewhere, where French typography
	 * does not apply: the Bitbucket permission names, written as the Bitbucket screens show them.
	 */
	private static final Set<String> PUNCTUATION_EXCEPTIONS = Set.of(
			"onboarding.create_project.pat_help.instructions.bitbucket_server",
			"onboarding.create_project.enter_password.instructions.bitbucket_cloud");

	/**
	 * What remains of a value once the parts that are not French prose are taken out: URLs, the
	 * ':' sample, the .NET trademark, tokens glued to a dot (a file extension, a path such as
	 * .github/workflows, the character list .-_@), a colon glued between two words
	 * (groupId:artifactId) and a single character quoted between apostrophes (’:’).
	 */
	private static String prose(String value) {
		return value
				.replaceAll("https?://(\\w+:\\w+@)?", "")
				.replace("':'", "")
				.replace(".NET", "")
				.replaceAll("(?<![\\w.])\\.[\\w./@-]+", "")
				.replaceAll("(?<=\\w)[:;](?=\\w)", "")
				.replaceAll("’[.,:;!?]’", "");
	}

	@Test
	public void check_punctuation() {
		SoftAssertions assertions = new SoftAssertions();
		translated.keySet().stream()
				.filter(key -> !PUNCTUATION_EXCEPTIONS.contains(key))
				.sorted()
				.forEach(key -> {
					String value = translated.getString(key);
					String prose = prose(value);
					assertions.assertThat(NON_BREAKING_SPACE_PUNCTUATIONS_WITHOUT_NON_BREAKING_SPACE.matcher(prose).find())
							.describedAs("Punctuation with non breaking space must be preceded with non-breaking space for key '" + key + "': " + value)
							.isFalse();
					assertions.assertThat(NON_SPACE_PUNCTUATIONS_WITH_SPACE_BEFORE.matcher(prose).find())
							.describedAs("Punctuation without space before must not be preceded with a space for key '" + key + "': " + value)
							.isFalse();
					assertions.assertThat(SPACE_AFTER_PUNCTUATIONS.matcher(prose).find())
							.describedAs("Punctuation must be followed by space for key '" + key + "': " + value)
							.isFalse();
				});
		assertions.assertAll();
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
		List<String> found = new ArrayList<>();
		translated.keySet().stream().sorted().forEach(key -> {
			Matcher matcher = NON_EPICENE_TERMS.matcher(translated.getString(key));
			while (matcher.find()) {
				found.add(key + ": " + matcher.group(0));
			}
		});
		assertThat(found)
				.describedAs("Non-epicene terms must not be used")
				.isEmpty();
	}

	private static final Pattern QUALITY_GATE = Pattern.compile("Barri(e|è)(?<plural>s?)\\s.*?Qualit(e|é)(s?)", Pattern.CASE_INSENSITIVE);
	private static final Pattern PORTFOLIO = Pattern.compile("(Portfolio|portefeuille)(?<plural>s?)", Pattern.CASE_INSENSITIVE);

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

	/**
	 * Names of the arguments a message expects, those inside sub-messages included.
	 *
	 * <p>A regular expression is not enough. ICU nests messages inside {@code plural} and
	 * {@code select} arguments, and a branch body is translatable text, not an argument name:
	 *
	 * <pre>
	 * {conditions} {conditions, plural, one {failed condition} other {failed conditions}}
	 * {show, select, true {Show} other {Hide}} multiple issues on this line
	 * </pre>
	 *
	 * <p>Each of those takes exactly one argument, {@code conditions} and {@code show}. The
	 * previous pattern special-cased the {@code plural} form but read every other brace pair
	 * as an argument, so it saw "Show" and "Hide" as argument names and then required the
	 * French file to keep them in English in order to match. Ten {@code select} messages were
	 * untranslatable for that reason alone.
	 */
	private TreeSet<String> extractPlaceHolders(String value) {
		TreeSet<String> result = new TreeSet<>();
		collectArguments(value, result);
		return result;
	}

	/** Argument types whose tail is a list of {@code keyword {sub-message}} pairs. */
	private static final List<String> SUB_MESSAGE_TYPES = List.of("plural", "select", "selectordinal");

	private static void collectArguments(String message, TreeSet<String> result) {
		for (int i = 0; i < message.length(); i++) {
			if (message.charAt(i) != '{') {
				continue;
			}
			int end = matchingBrace(message, i);
			if (end < 0) {
				// Unbalanced braces: nothing sensible left to read.
				return;
			}
			String body = message.substring(i + 1, end);
			int comma = body.indexOf(',');
			result.add((comma < 0 ? body : body.substring(0, comma)).trim());
			if (comma >= 0) {
				collectSubMessages(body.substring(comma + 1).trim(), result);
			}
			i = end;
		}
	}

	private static void collectSubMessages(String remainder, TreeSet<String> result) {
		int comma = remainder.indexOf(',');
		if (comma < 0 || !SUB_MESSAGE_TYPES.contains(remainder.substring(0, comma).trim())) {
			// Formats such as `number` or `date` carry no sub-message.
			return;
		}
		String branches = remainder.substring(comma + 1);
		for (int i = 0; i < branches.length(); i++) {
			if (branches.charAt(i) != '{') {
				continue;
			}
			int end = matchingBrace(branches, i);
			if (end < 0) {
				return;
			}
			collectArguments(branches.substring(i + 1, end), result);
			i = end;
		}
	}

	private static int matchingBrace(String message, int open) {
		int depth = 0;
		for (int i = open; i < message.length(); i++) {
			char c = message.charAt(i);
			if (c == '{') {
				++depth;
			} else if (c == '}' && --depth == 0) {
				return i;
			}
		}
		return -1;
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
		List<Integer> badLines = new ArrayList<>();
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
					badLines.add(line);
				}
			}
		}
		assertThat(badLines)
				.describedAs("Bad line endings (CR) on these lines")
				.isEmpty();
	}

	/**
	 * <p>From <a href="https://en.wikipedia.org/wiki/Apostrophe#Unicode">Wikipedia - Apostrophe (#Unicode)</a></p>
	 * <bloquote>
	 * U+2019 ’ RIGHT SINGLE QUOTATION MARK is preferred where the character is to represent a punctuation mark,
	 * as for contractions: "we’ve", and the code is also referred to as a punctuation apostrophe.
	 * The closing single quote and the apostrophe were unified in Unicode 2.1 "to correct problems in the mapping
	 * tables from Windows and Macintosh code pages."[112] This can make searching text more difficult as quotes
	 * and apostrophes cannot be distinguished without context.
	 * </bloquote>
	 */
	@Test
	public void apostrophe_should_use_correct_unicode() {
		SoftAssertions assertions = new SoftAssertions();
		base.keySet().forEach(key -> {
			assertions
							.assertThat(translated.getString(key))
							.describedAs("Use ’ (\\u2019) for apostrophe punctuation mark for key: " + key)
							.doesNotMatch(Pattern.compile(".*[\\u0027\\u02BC\\u02BB\\u02BD\\u02BE\\u02BF\\u02C8\\u0313\\u0315\\u055A\\u2018\\u201B\\u0060\\u00B4\\u2032\\u02B9\\u2035\\uA78B\\uA78C].*"));
		});
		assertions.assertAll();
	}
}
