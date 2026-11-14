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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Rewrites {@code core_fr.properties} from the English reference bundle shipped in
 * sonar-core, carrying existing translations over.
 *
 * <p>Run it with {@code tools/sync-bundle.sh}, which wraps
 * {@code mvn -Psync test-compile exec:java}.
 *
 * <p>Why this exists: {@code translated_file_structure_shoud_be_same_as_base} requires the
 * translated file to be structurally identical to the reference -- same comments, same blank
 * lines, same key order, same duplicated keys. Keeping that true by hand across a version
 * bump that adds some 1500 keys is not realistic, and one misplaced line fails the build
 * with a diff thousands of lines long. Generating the file makes the structure correct by
 * construction; the only thing a human edits is the text to the right of the separator.
 *
 * <p>The two files are not encoded the same way, and getting that wrong silently corrupts
 * the bundle:
 * <ul>
 *   <li>the reference is UTF-8 -- SonarSource writes it that way even though
 *       {@code .properties} historically means ISO-8859-1. Java 9+ reads properties as UTF-8
 *       with a fallback, so both work at runtime;</li>
 *   <li>{@code core_fr.properties} is ISO-8859-1 in the working tree, as declared in
 *       {@code .gitattributes}. Characters Latin-1 can represent are written literally and
 *       the rest escaped, which is the convention the file already follows and the one that
 *       keeps diffs readable. {@code native2ascii-maven-plugin} escapes the remainder at
 *       package time.</li>
 * </ul>
 *
 * <p>Obsolete keys -- present in the translation, gone from the reference -- are dropped
 * rather than kept behind a "to remove" marker comment: this bundle targets one SonarQube
 * line, and older lines have their own branch.
 */
public final class BundleSync {

  private static final String REFERENCE_RESOURCE = "/org/sonar/l10n/core.properties";
  private static final Path TRANSLATED = Paths.get("src/main/resources/org/sonar/l10n/core_fr.properties");
  private static final Path REPORT = Paths.get("target/bundle-sync-report.txt");
  private static final String BY_PREFIX_HEADING = "\n== Still untranslated, by prefix ==\n";

  private BundleSync() {
    // utility class
  }

  public static void main(String[] args) throws IOException {
    List<String> reference = readReference();
    Map<String, String> translated = readTranslated();

    List<String> output = new ArrayList<>(reference.size());
    Set<String> referenceKeys = new LinkedHashSet<>();
    List<String> added = new ArrayList<>();
    List<String> untranslated = new ArrayList<>();

    for (String line : reference) {
      if (isBlankOrComment(line)) {
        output.add(line);
        continue;
      }
      int separator = separatorIndex(line);
      if (separator < 0) {
        throw new IllegalStateException("No key/value separator in reference line: " + line);
      }
      String key = line.substring(0, separator);
      String referenceValue = escapeNonLatin1(line.substring(separator + 1));

      // A duplicated key is emitted as many times as the reference emits it: the structure
      // test compares line by line, so collapsing duplicates would fail it.
      boolean firstOccurrence = referenceKeys.add(key);

      String value = translated.get(key);
      if (value == null) {
        value = referenceValue;
        if (firstOccurrence) {
          added.add(key);
        }
      }
      if (firstOccurrence && unescape(value).equals(unescape(referenceValue))) {
        untranslated.add(key);
      }
      output.add(key + "=" + value);
    }

    List<String> removed = new ArrayList<>();
    for (String key : translated.keySet()) {
      if (!referenceKeys.contains(key)) {
        removed.add(key);
      }
    }

    write(output);
    report(referenceKeys.size(), added, removed, untranslated);
  }

  private static boolean isBlankOrComment(String line) {
    String trimmed = line.trim();
    return trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!");
  }

  private static List<String> readReference() throws IOException {
    try (InputStream input = BundleSync.class.getResourceAsStream(REFERENCE_RESOURCE)) {
      if (input == null) {
        throw new IllegalStateException(REFERENCE_RESOURCE
          + " not found on the classpath. It comes from the sonar-core test dependency;"
          + " run through Maven so that artifact is resolved.");
      }
      return new String(input.readAllBytes(), StandardCharsets.UTF_8)
        .lines()
        .toList();
    }
  }

  private static Map<String, String> readTranslated() throws IOException {
    Map<String, String> result = new LinkedHashMap<>();
    if (!Files.exists(TRANSLATED)) {
      return result;
    }
    for (String line : Files.readAllLines(TRANSLATED, StandardCharsets.ISO_8859_1)) {
      if (isBlankOrComment(line)) {
        continue;
      }
      int separator = separatorIndex(line);
      if (separator >= 0) {
        // Raw text, deliberately not unescaped: carrying the value over verbatim is lossless
        // and keeps the diff limited to the keys that actually changed.
        result.put(line.substring(0, separator), line.substring(separator + 1));
      }
    }
    return result;
  }

  /**
   * Index of the separator that actually splits key from value. The reference does contain
   * keys with an escaped separator, such as the "not equals" operator label whose key ends
   * with a backslash followed by the separator, so a plain indexOf would cut in the wrong
   * place.
   */
  private static int separatorIndex(String line) {
    for (int i = 0; i < line.length(); i++) {
      if (line.charAt(i) == '=' && (i == 0 || line.charAt(i - 1) != '\\')) {
        return i;
      }
    }
    return -1;
  }

  /** Characters ISO-8859-1 cannot hold are escaped; the rest stay readable in the diff. */
  private static String escapeNonLatin1(String value) {
    StringBuilder out = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c > 0xFF) {
        out.append(String.format("\\u%04X", (int) c));
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }

  /** Resolves unicode escapes so a translated value can be compared with the reference. */
  private static String unescape(String value) {
    StringBuilder out = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '\\' && i + 5 < value.length() && (value.charAt(i + 1) == 'u' || value.charAt(i + 1) == 'U')) {
        try {
          out.append((char) Integer.parseInt(value.substring(i + 2, i + 6), 16));
          i += 5;
          continue;
        } catch (NumberFormatException notAnEscape) {
          // fall through and keep the backslash as-is
        }
      }
      out.append(c);
    }
    return out.toString();
  }

  private static void write(List<String> lines) throws IOException {
    // Joined by hand rather than through Files.write(List): that would use the platform
    // separator, and line_endings_must_be_linux_one() rejects carriage returns.
    StringBuilder content = new StringBuilder();
    for (String line : lines) {
      content.append(line).append('\n');
    }
    Files.createDirectories(TRANSLATED.getParent());
    Files.write(TRANSLATED, content.toString().getBytes(StandardCharsets.ISO_8859_1));
  }

  private static void report(int referenceKeys, List<String> added, List<String> removed, List<String> untranslated)
    throws IOException {
    StringBuilder out = new StringBuilder();
    out.append("Reference keys ......... ").append(referenceKeys).append('\n');
    out.append("Added (were missing) ... ").append(added.size()).append('\n');
    out.append("Dropped (obsolete) ..... ").append(removed.size()).append('\n');
    out.append("Still untranslated ..... ").append(untranslated.size()).append('\n');

    out.append(BY_PREFIX_HEADING);
    Map<String, Integer> byPrefix = new TreeMap<>();
    for (String key : untranslated) {
      int dot = key.indexOf('.');
      byPrefix.merge(dot < 0 ? key : key.substring(0, dot), 1, Integer::sum);
    }
    byPrefix.entrySet().stream()
      .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
      .forEach(e -> out.append(String.format("%6d  %s%n", e.getValue(), e.getKey())));

    appendList(out, "Dropped (obsolete)", removed);
    appendList(out, "Still untranslated", untranslated);

    Files.createDirectories(REPORT.getParent());
    Files.write(REPORT, out.toString().getBytes(StandardCharsets.UTF_8));

    System.out.println(out.substring(0, out.indexOf(BY_PREFIX_HEADING)));
    System.out.println("Full report: " + REPORT);
  }

  private static void appendList(StringBuilder out, String title, List<String> keys) {
    out.append('\n').append("== ").append(title).append(" (").append(keys.size()).append(") ==\n");
    keys.stream().sorted().forEach(key -> out.append(key).append('\n'));
  }
}
