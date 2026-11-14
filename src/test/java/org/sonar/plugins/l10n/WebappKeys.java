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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dumps the message catalogue the SonarQube web app actually ships, read from a running server.
 *
 * <p>Usage: {@code tools/webapp-keys.sh [url]}, default {@code http://localhost:9020}.
 * Writes {@code target/webapp-keys.properties}.
 *
 * <p>Why this is needed: {@code org/sonar/l10n/core.properties} is labelled "LEGACY FILE" by
 * SonarSource and is no longer the catalogue the front end uses. The real one lives in the
 * web app bundle, and the two have diverged in both directions:
 * <ul>
 *   <li>keys the web app has and core.properties does not -- roughly three thousand of them,
 *       including the whole login screen. {@code DefaultI18n} indexes only what the English
 *       bundle declares, so adding those to {@code core_fr.properties} would have no effect
 *       whatsoever: they are served through the plugin's own bundle instead;</li>
 *   <li>keys both have, with different argument names. The French file follows
 *       core.properties because that is what the harness compares against, so at runtime it
 *       hands react-intl an argument the component never passes, and the placeholder is
 *       rendered literally on screen.</li>
 * </ul>
 *
 * <p>Both are only visible by looking at what the server serves, which is what this does.
 * There is no published artifact to read instead: the generator SonarSource mentions in the
 * header of core.properties lives in a private part of their front-end repository.
 */
public final class WebappKeys {

  private static final Path REPORT = Paths.get("target/webapp-keys.properties");
  private static final Path UNDEFINED = Paths.get("target/webapp-keys-undefined.txt");
  private static final Path UNDEFINED_DEFAULTS = Paths.get("target/webapp-keys-undefined-defaults.properties");

  /** Script tags in index.html, which is where the entry bundle names are. */
  private static final Pattern SCRIPT = Pattern.compile("src=\"([^\"]+\\.js)\"");

  /**
   * A chunk another bundle loads: {@code window.__assetsPath("js/x.js")} in the preload map of
   * the entry bundle, {@code "./x.js"} in a static or dynamic import between chunks.
   *
   * <p>Up to 26.9 the catalogue sat in a bundle index.html names. From 26.10 the entry bundle is
   * a 13 kB loader, and the catalogue lives in a chunk it only imports lazily -- reading the
   * script tags alone finds no message at all. Every chunk is therefore followed, transitively.
   */
  private static final Pattern CHUNK =
    Pattern.compile("(?:__assetsPath\\(\"js/|\"\\./)([A-Za-z0-9_.\\-]+\\.js)\"");

  /** A message key: it must contain a dot, see {@link #QUOTED_ENTRY}. */
  private static final String DOTTED_KEY = "\"([a-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_\\-]+)+)\":";

  /**
   * A quoted key mapped to a quoted string. The key must contain a dot: bare identifiers in
   * minified code are overwhelmingly variable names, not message keys, and the few real ones
   * are recovered by {@link #BARE_ENTRIES}.
   */
  private static final Pattern QUOTED_ENTRY =
    Pattern.compile(DOTTED_KEY + "\"((?:[^\"\\\\]|\\\\.)*)\"");

  /**
   * The same entry, written with a single-quoted value. The minifier switches quotes when the
   * message itself contains a double quote, which happens wherever the English quotes an
   * argument -- {@code Copy permalink to current issue: "{title}"}. Sixty-nine keys were
   * invisible to {@link #QUOTED_ENTRY} for that reason alone.
   */
  private static final Pattern SINGLE_QUOTED_ENTRY =
    Pattern.compile(DOTTED_KEY + "'((?:[^'\\\\]|\\\\.)*)'");

  /**
   * The same entry, written with a template literal. 26.10 is minified by Rolldown, which writes
   * every string value of the catalogue between backticks: neither pattern above matches a
   * single one of its 8,000 entries.
   */
  private static final Pattern BACKTICK_ENTRY =
    Pattern.compile(DOTTED_KEY + "`((?:[^`\\\\]|\\\\.)*)`");

  private static final List<Pattern> DOTTED_ENTRIES = List.of(QUOTED_ENTRY, SINGLE_QUOTED_ENTRY, BACKTICK_ENTRY);

  /**
   * Unquoted key mapped to a string, for the single-word keys such as {@code username}, under
   * both minifiers' quoting.
   *
   * <p>Only read in the catalogue itself, see {@link #CATALOGUE_THRESHOLD}: elsewhere the same
   * shape is mostly code -- date-fns locales, highlight.js grammars, the design system's props
   * -- and following every chunk would otherwise turn hundreds of them into "messages".
   */
  private static final List<Pattern> BARE_ENTRIES = List.of(
    Pattern.compile("[{,]([a-z][a-z0-9_]{2,}):\"((?:[^\"\\\\]|\\\\.)*)\""),
    Pattern.compile("[{,]([a-z][a-z0-9_]{2,}):`((?:[^`\\\\]|\\\\.)*)`"));

  /**
   * How many dotted entries make a bundle the catalogue. The catalogue chunk of 26.10 carries
   * about 8,000; no other chunk carries more than one.
   */
  private static final int CATALOGUE_THRESHOLD = 1000;

  /**
   * A key the code asks for, rather than one the catalogue defines: a call whose single
   * argument is an object starting with {@code id}, as {@code formatMessage({id: "..."})}
   * compiles. The call itself is what tells it apart from a DOM identifier, as in
   * {@code <TextField id="name.github">}, which compiles to a props object passed as a second
   * argument, {@code jsx(TextField, {id: ...})}, and is not a message at all. The function name
   * cannot be relied on: Rolldown minifies {@code formatMessage} to a single letter.
   *
   * <p>A key asked for but never defined is an upstream bug -- react-intl renders the key
   * itself on screen -- and a translation the pack can supply, since nothing else does.
   */
  private static final Pattern REFERENCED =
    Pattern.compile("\\(\\s*\\{\\s*id:\\s*[\"`]([a-zA-Z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_\\-]+)+)[\"`]");

  /** A string literal in either quoting the minifier uses, its content in the group. */
  private static final String LITERAL = "(?:`((?:[^`\\\\]|\\\\.)*)`|\"((?:[^\"\\\\]|\\\\.)*)\")";

  /** A key literal, the key in the group. */
  private static final String KEY_LITERAL = "[\"`]([a-zA-Z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_\\-]+)+)[\"`]";

  /**
   * A message descriptor passed as props, {@code jsx(FormattedMessage, {defaultMessage: ...,
   * id: ...})}, which {@link #REFERENCED} leaves out on purpose since the props shape is also
   * that of a DOM id. Carrying a {@code defaultMessage} is what makes it a message for sure.
   * The design system writes these with the id last -- the pagination buttons, {@code
   * pagination.previous} and {@code pagination.next}, went unnoticed until 26.11 for that
   * reason -- so both orders are read. The default message is kept: it is the English the
   * plugin bundle needs for a key nothing else defines.
   */
  private static final List<Pattern> DESCRIPTORS = List.of(
    Pattern.compile("\\{\\s*defaultMessage:\\s*" + LITERAL + "\\s*,(?:\\s*description:\\s*" + LITERAL
      + "\\s*,)?\\s*id:\\s*" + KEY_LITERAL),
    Pattern.compile("\\{\\s*id:\\s*" + KEY_LITERAL + "\\s*,\\s*defaultMessage:\\s*" + LITERAL));

  private WebappKeys() {
    // utility class
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    String base = args.length > 0 ? args[0] : "http://localhost:9020";
    HttpClient client = HttpClient.newHttpClient();

    String index = get(client, base + "/");
    Set<String> scripts = new LinkedHashSet<>();
    Matcher matcher = SCRIPT.matcher(index);
    while (matcher.find()) {
      scripts.add(matcher.group(1));
    }
    if (scripts.isEmpty()) {
      throw new IllegalStateException("No script tag found at " + base + " -- is the server up?");
    }

    TreeMap<String, String> messages = new TreeMap<>();
    Set<String> referenced = new TreeSet<>();
    TreeMap<String, String> defaults = new TreeMap<>();
    Deque<String> pending = new ArrayDeque<>(scripts);
    Set<String> read = new HashSet<>();
    while (!pending.isEmpty()) {
      String script = pending.pop();
      if (!read.add(script)) {
        continue;
      }
      String body = get(client, script.startsWith("http") ? script : base + script);
      TreeMap<String, String> dotted = new TreeMap<>();
      DOTTED_ENTRIES.forEach(pattern -> collect(pattern, body, dotted, true));
      messages.putAll(dotted);
      if (dotted.size() >= CATALOGUE_THRESHOLD) {
        BARE_ENTRIES.forEach(pattern -> collect(pattern, body, messages, false));
      }
      Matcher asked = REFERENCED.matcher(body);
      while (asked.find()) {
        referenced.add(asked.group(1));
      }
      collectDescriptors(body, referenced, defaults);
      Matcher chunk = CHUNK.matcher(body);
      while (chunk.find()) {
        pending.push("/js/" + chunk.group(1));
      }
    }

    StringBuilder out = new StringBuilder();
    messages.forEach((key, value) -> out.append(key).append('=').append(value).append('\n'));
    Files.createDirectories(REPORT.getParent());
    Files.write(REPORT, out.toString().getBytes(StandardCharsets.UTF_8));

    referenced.removeAll(messages.keySet());
    Files.write(UNDEFINED, String.join("\n", referenced).getBytes(StandardCharsets.UTF_8));
    StringBuilder known = new StringBuilder();
    referenced.stream().filter(defaults::containsKey)
      .forEach(key -> known.append(key).append('=').append(defaults.get(key)).append('\n'));
    Files.write(UNDEFINED_DEFAULTS, known.toString().getBytes(StandardCharsets.UTF_8));

    System.out.printf("%d messages read from %d bundle(s) at %s%n", messages.size(), read.size(), base);
    System.out.println("Written to " + REPORT);
    System.out.printf("%d key(s) the code asks for but nothing defines -- see %s%n",
      referenced.size(), UNDEFINED);
  }

  private static void collectDescriptors(String body, Set<String> referenced, TreeMap<String, String> defaults) {
    Matcher idLast = DESCRIPTORS.get(0).matcher(body);
    while (idLast.find()) {
      String key = idLast.group(5);
      referenced.add(key);
      defaults.putIfAbsent(key, literal(idLast, 1));
    }
    Matcher idFirst = DESCRIPTORS.get(1).matcher(body);
    while (idFirst.find()) {
      String key = idFirst.group(1);
      referenced.add(key);
      defaults.putIfAbsent(key, literal(idFirst, 2));
    }
  }

  /** The content of a {@link #LITERAL} whose first group is {@code group}: one quoting matched. */
  private static String literal(Matcher matcher, int group) {
    return Objects.requireNonNullElse(matcher.group(group), matcher.group(group + 1));
  }

  private static void collect(Pattern pattern, String body, TreeMap<String, String> into, boolean overwrite) {
    Matcher matcher = pattern.matcher(body);
    while (matcher.find()) {
      String key = matcher.group(1);
      if (overwrite || !into.containsKey(key)) {
        into.put(key, unescapeJs(matcher.group(2)));
      }
    }
  }

  /** Undoes the escaping the JavaScript string literal carries, so values compare as text. */
  private static String unescapeJs(String value) {
    StringBuilder out = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c != '\\' || i + 1 >= value.length()) {
        out.append(c);
        continue;
      }
      char next = value.charAt(++i);
      switch (next) {
        case 'n' -> out.append('\n');
        case 't' -> out.append('\t');
        case 'r' -> out.append('\r');
        case 'u' -> {
          if (i + 4 < value.length()) {
            out.append((char) Integer.parseInt(value.substring(i + 1, i + 5), 16));
            i += 4;
          }
        }
        // Rolldown writes a non-breaking space as \xA0. Read as an unknown escape, it came out
        // as the letters "xA0", glued to the word before it.
        case 'x' -> {
          if (i + 2 < value.length()) {
            out.append((char) Integer.parseInt(value.substring(i + 1, i + 3), 16));
            i += 2;
          }
        }
        default -> out.append(next);
      }
    }
    return out.toString();
  }

  private static String get(HttpClient client, String url) throws IOException, InterruptedException {
    HttpResponse<String> response = client.send(
      HttpRequest.newBuilder(URI.create(url)).GET().build(),
      HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    if (response.statusCode() != 200) {
      throw new IllegalStateException("HTTP " + response.statusCode() + " for " + url);
    }
    return response.body();
  }
}
