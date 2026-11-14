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

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.assertj.core.api.SoftAssertions;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rules for the plugin's own bundle, {@code org.sonar.l10n.l10nfr}.
 *
 * <p>That bundle exists because {@code core.properties} is no longer the catalogue the web app
 * reads. {@code DefaultI18n} builds its index from an ENGLISH bundle and calls
 * {@code initPlugin} for every installed plugin, so a plugin that ships
 * {@code l10nfr.properties} gets its keys served -- including keys the server core has never
 * heard of, such as the whole login screen.
 *
 * <p>Two properties matter and neither is covered by {@link FrenchPackPluginTest}, which only
 * looks at {@code org.sonar.l10n.core}:
 * <ul>
 *   <li>the two files must declare exactly the same keys, or a key is either served in English
 *       or declared and never resolved;</li>
 *   <li>a key that also exists in {@code core.properties} is shadowed by this bundle, because
 *       core is registered first and the last write wins. Shadowing is intended only where the
 *       two disagree, so the English side must actually differ from core's. Otherwise the
 *       entry is redundant and would silently freeze a value SonarSource may later fix.</li>
 * </ul>
 */
public class FrenchPackExtensionBundleTest {

  private static final String EXTENSION_BUNDLE = "org.sonar.l10n.l10nfr";
  private static final String CORE_BUNDLE = "org.sonar.l10n.core";

  private ResourceBundle english;
  private ResourceBundle french;
  private ResourceBundle core;

  @Before
  public void init() {
    english = ResourceBundle.getBundle(EXTENSION_BUNDLE, new Locale(""));
    french = ResourceBundle.getBundle(EXTENSION_BUNDLE, Locale.FRENCH);
    core = ResourceBundle.getBundle(CORE_BUNDLE, new Locale(""));
    assertThat(english.keySet()).isNotEmpty();
  }

  @Test
  public void both_sides_must_declare_the_same_keys() {
    assertThat(new TreeSet<>(french.keySet()))
      .describedAs("l10nfr_fr.properties must translate exactly the keys l10nfr.properties declares")
      .isEqualTo(new TreeSet<>(english.keySet()));
  }

  @Test
  public void every_key_must_actually_be_translated() {
    SoftAssertions assertions = new SoftAssertions();
    english.keySet().forEach(key -> assertions
      .assertThat(french.getString(key))
      .describedAs("Key is still in English: " + key)
      .isNotEqualTo(english.getString(key)));
    assertions.assertAll();
  }

  /**
   * Shadowing a core key is deliberate, never incidental: it is how a key whose definition
   * drifted from core.properties gets the arguments the web app really passes. An entry whose
   * English matches core's brings nothing and is rejected.
   */
  @Test
  public void keys_shared_with_core_must_differ_from_core() {
    SoftAssertions assertions = new SoftAssertions();
    english.keySet().stream()
      .filter(key -> core.containsKey(key))
      .forEach(key -> assertions
        .assertThat(english.getString(key))
        .describedAs("Key shadows core.properties without differing from it, so it is redundant: " + key)
        .isNotEqualTo(core.getString(key)));
    assertions.assertAll();
  }

  private static final Pattern NON_EPICENE_TERMS = Pattern.compile(
    "administrateur|utilisateur|développeur|auteur|((^|\\W)êtes)", Pattern.CASE_INSENSITIVE);

  @Test
  public void should_not_use_non_epicene_term() {
    assertThat(french.keySet().stream()
      .filter(key -> NON_EPICENE_TERMS.matcher(french.getString(key)).find())
      .sorted()
      .toList())
      .describedAs("Non-epicene term used for these keys")
      .isEmpty();
  }

  private static final Pattern WRONG_APOSTROPHE = Pattern.compile(
    ".*['ʼʻʽʾʿˈ̓̕՚‘‛`´′ʹ‵Ꞌꞌ].*");

  @Test
  public void apostrophe_should_use_correct_unicode() {
    SoftAssertions assertions = new SoftAssertions();
    french.keySet().forEach(key -> assertions
      .assertThat(french.getString(key))
      .describedAs("Use ’ (\\u2019) for apostrophe punctuation mark for key: " + key)
      .doesNotMatch(WRONG_APOSTROPHE));
    assertions.assertAll();
  }

  /** Any character but U+00A0, the non-breaking space French puts before these marks. */
  private static final Pattern PUNCTUATION_WITHOUT_NON_BREAKING_SPACE =
    Pattern.compile("[^\\u00A0][:;!?]");

  @Test
  public void check_punctuation() {
    assertThat(french.keySet().stream()
      .filter(key -> PUNCTUATION_WITHOUT_NON_BREAKING_SPACE
        // URLs, the CI/CD style shorthand, and code tokens carry colons that are not
        // punctuation. A French colon is always followed by a space, so a colon glued to
        // the next character belongs to something being quoted, such as the rule key
        // java:S1195 the "Restrict Scope of Coding Rules" setting documents itself with.
        .matcher(french.getString(key)
          .replaceAll("https?://(\\w+:\\w+@)?", "")
          .replace("CI/CD", "")
          .replaceAll("(?<=\\w)[:;](?=\\S)", ""))
        .find())
      .sorted()
      .map(key -> key + ": " + french.getString(key))
      .toList())
      .describedAs("Punctuation must be preceded with a non-breaking space")
      .isEmpty();
  }

  @Test
  public void placeholders_must_have_same_name() {
    SoftAssertions assertions = new SoftAssertions();
    english.keySet().forEach(key -> assertions
      .assertThat(arguments(french.getString(key)))
      .describedAs("Placeholder name(s) should be the same for key: " + key)
      .isEqualTo(arguments(english.getString(key))));
    assertions.assertAll();
  }

  /** Same ICU reading as {@link FrenchPackPluginTest}: argument names only, sub-messages included. */
  private static TreeSet<String> arguments(String message) {
    TreeSet<String> result = new TreeSet<>();
    collect(message, result);
    return result;
  }

  private static final Set<String> SUB_MESSAGE_TYPES = Set.of("plural", "select", "selectordinal");

  private static void collect(String message, TreeSet<String> result) {
    for (int i = 0; i < message.length(); i++) {
      if (message.charAt(i) != '{') {
        continue;
      }
      int end = matchingBrace(message, i);
      if (end < 0) {
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
      collect(branches.substring(i + 1, end), result);
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
}
