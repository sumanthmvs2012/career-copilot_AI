package com.careercopilot.jobs.scraper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort parser that turns a board's free-text location into structured rows. It never
 * throws and never guesses: a field stays null unless the text or a built-in dictionary says
 * what it is.
 */
public final class LocationParser {

  private static final int MAX_ROWS = 20;
  // Bounds the work done on pathological input.
  private static final int MAX_INPUT = 2000;
  private static final int MAX_PLACE = 60;

  private static final String US = "United States";
  private static final String CANADA = "Canada";
  private static final String DASHES = "\\-\u2013\u2014";

  private static final String NOISE_WORDS =
      "remote|hybrid|on-?site|anywhere|global|worldwide|flexible|distributed|nationwide"
          + "|multiple\\s+locations?|various\\s+locations?";

  private static final Pattern LEADING_NOISE =
      Pattern.compile(
          "^(?:(?:" + NOISE_WORDS + ")\\b[\\s," + DASHES + "]*)+", Pattern.CASE_INSENSITIVE);
  private static final Pattern TRAILING_NOISE =
      Pattern.compile(
          "(?:[\\s," + DASHES + "]*\\b(?:" + NOISE_WORDS + "))+$", Pattern.CASE_INSENSITIVE);
  private static final Pattern LEADING_IN =
      Pattern.compile("^(?:in|within|across)\\s+(?:the\\s+)?", Pattern.CASE_INSENSITIVE);
  private static final Pattern EDGE_JUNK = Pattern.compile("^[\\s,.\\-\u2013\u2014]+|[\\s,\\-\u2013\u2014]+$");
  private static final Pattern GREATER = Pattern.compile("^greater\\s+", Pattern.CASE_INSENSITIVE);
  private static final Pattern TRAILING_AREA = Pattern.compile("\\s+area$", Pattern.CASE_INSENSITIVE);
  private static final Pattern METRO =
      Pattern.compile("[\\s\\-]*\\bmetro(?:politan)?\\s+area\\b", Pattern.CASE_INSENSITIVE);
  private static final Pattern NOT_APPLICABLE = Pattern.compile("\\bn/a\\b", Pattern.CASE_INSENSITIVE);
  private static final Pattern PARENS = Pattern.compile("\\(([^)]*)\\)");
  private static final Pattern DASH_SEPARATOR = Pattern.compile("\\s[" + DASHES + "]\\s");
  // A comma before the whitespace means the "OR" is an Oregon code, not a connector.
  private static final Pattern SEGMENT_SPLIT =
      Pattern.compile("[;/|\\n\\r]+|(?<!,)\\s+or\\s+", Pattern.CASE_INSENSITIVE);
  private static final Pattern DC_CITY =
      Pattern.compile("^(.+?)[\\s,]+D\\.?C\\.?$", Pattern.CASE_INSENSITIVE);
  private static final Pattern PLACE_NAME = Pattern.compile("^\\p{L}[\\p{L} .'\u2019\\-]*$");

  private static final Set<String> NOISE_TOKENS =
      Set.of("na", "tbd", "tba", "none", "world", "emea", "apac", "latam", "americas", "europe",
          "north america");
  // Names that are both a state and a city: they are a state only after a city.
  private static final Set<String> AMBIGUOUS_STATES = Set.of("new york", "washington");
  private static final Set<String> CITY_STATES = Set.of("Singapore", "Hong Kong");
  private static final Set<String> CANADIAN_CITIES =
      Set.of("toronto", "vancouver", "montreal", "montr\u00e9al", "ottawa", "calgary", "edmonton",
          "waterloo", "winnipeg", "quebec city", "halifax", "kitchener", "mississauga");

  private record StateInfo(String name, String country) {}

  private static final Map<String, StateInfo> STATES = new HashMap<>();
  private static final Map<String, String> COUNTRIES = new HashMap<>();

  static {
    String[] us = {
      "AL", "Alabama", "AK", "Alaska", "AZ", "Arizona", "AR", "Arkansas", "CA", "California",
      "CO", "Colorado", "CT", "Connecticut", "DE", "Delaware", "DC", "District of Columbia",
      "FL", "Florida", "GA", "Georgia", "HI", "Hawaii", "ID", "Idaho", "IL", "Illinois",
      "IN", "Indiana", "IA", "Iowa", "KS", "Kansas", "KY", "Kentucky", "LA", "Louisiana",
      "ME", "Maine", "MD", "Maryland", "MA", "Massachusetts", "MI", "Michigan", "MN", "Minnesota",
      "MS", "Mississippi", "MO", "Missouri", "MT", "Montana", "NE", "Nebraska", "NV", "Nevada",
      "NH", "New Hampshire", "NJ", "New Jersey", "NM", "New Mexico", "NY", "New York",
      "NC", "North Carolina", "ND", "North Dakota", "OH", "Ohio", "OK", "Oklahoma", "OR", "Oregon",
      "PA", "Pennsylvania", "RI", "Rhode Island", "SC", "South Carolina", "SD", "South Dakota",
      "TN", "Tennessee", "TX", "Texas", "UT", "Utah", "VT", "Vermont", "VA", "Virginia",
      "WA", "Washington", "WV", "West Virginia", "WI", "Wisconsin", "WY", "Wyoming"
    };
    addStates(us, US);
    String[] ca = {
      "AB", "Alberta", "BC", "British Columbia", "MB", "Manitoba", "NB", "New Brunswick",
      "NL", "Newfoundland and Labrador", "NS", "Nova Scotia", "NT", "Northwest Territories",
      "NU", "Nunavut", "ON", "Ontario", "PE", "Prince Edward Island", "QC", "Quebec",
      "SK", "Saskatchewan", "YT", "Yukon"
    };
    addStates(ca, CANADA);
    STATES.put("qu\u00e9bec", new StateInfo("Quebec", CANADA));

    country(US, "us", "usa", "u.s.", "u.s.a.", "united states", "united states of america", "america");
    country("United Kingdom", "uk", "gb", "gbr", "u.k.", "united kingdom", "great britain",
        "britain", "england", "scotland", "wales", "northern ireland");
    country(CANADA, "canada");
    country("India", "india");
    country("Germany", "germany", "deutschland");
    country("Ireland", "ireland", "ie");
    country("France", "france", "fr");
    country("Singapore", "singapore", "sg");
    country("Australia", "australia", "au");
    country("Netherlands", "netherlands", "the netherlands", "holland");
    country("Spain", "spain", "es");
    country("Japan", "japan", "jp");
    country("Brazil", "brazil", "brasil", "br");
    country("Mexico", "mexico", "mx");
    country("Italy", "italy");
    country("Portugal", "portugal");
    country("Poland", "poland");
    country("Sweden", "sweden");
    country("Norway", "norway");
    country("Denmark", "denmark");
    country("Finland", "finland");
    country("Switzerland", "switzerland");
    country("Austria", "austria");
    country("Belgium", "belgium");
    country("China", "china");
    country("Hong Kong", "hong kong");
    country("South Korea", "south korea", "korea");
    country("Israel", "israel");
    country("United Arab Emirates", "uae", "united arab emirates");
    country("Argentina", "argentina");
    country("Colombia", "colombia");
    country("Chile", "chile");
    country("Philippines", "philippines");
    country("Indonesia", "indonesia");
    country("Vietnam", "vietnam");
    country("New Zealand", "new zealand", "nz");
    country("South Africa", "south africa");
    country("Nigeria", "nigeria");
    country("Kenya", "kenya");
    country("Egypt", "egypt");
    country("Turkey", "turkey");
    country("Romania", "romania");
    country("Czech Republic", "czech republic", "czechia");
    country("Ukraine", "ukraine");
    country("Pakistan", "pakistan");
    country("Thailand", "thailand");
    country("Malaysia", "malaysia");
    country("Costa Rica", "costa rica");
    country("Estonia", "estonia");
    country("Greece", "greece");
  }

  private LocationParser() {}

  private static void addStates(String[] pairs, String country) {
    for (int i = 0; i < pairs.length; i += 2) {
      StateInfo info = new StateInfo(pairs[i + 1], country);
      STATES.put(pairs[i].toLowerCase(Locale.ROOT), info);
      STATES.put(pairs[i + 1].toLowerCase(Locale.ROOT), info);
    }
  }

  private static void country(String canonical, String... aliases) {
    for (String alias : aliases) {
      COUNTRIES.put(key(alias), canonical);
    }
  }

  /** Parses free-text; null, blank or unparseable input yields an empty list. */
  public static List<ParsedLocation> parse(String text) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    String work = text.length() > MAX_INPUT ? text.substring(0, MAX_INPUT) : text;
    work = NOT_APPLICABLE.matcher(work).replaceAll("");
    work = METRO.matcher(work).replaceAll("");
    work = PARENS.matcher(work).replaceAll(", $1 ,");
    work = DASH_SEPARATOR.matcher(work).replaceAll(", ");

    Set<ParsedLocation> out = new LinkedHashSet<>();
    for (String segment : SEGMENT_SPLIT.split(work)) {
      List<Tok> tokens = new ArrayList<>();
      for (String raw : segment.split(",")) {
        tokens.add(classify(raw));
      }
      parseSegment(tokens, out);
    }
    return List.copyOf(out);
  }

  // ---- token classification ----

  private enum Kind { NOISE, PLACE, STATE, COUNTRY, CA, CITY_STATE, PREFIX }

  private record Tok(Kind kind, String name, String state, String country, boolean ambiguous, Tok rest) {
    static final Tok NOISE = new Tok(Kind.NOISE, null, null, null, false, null);
  }

  private static Tok classify(String raw) {
    String s = clean(raw);
    String key = key(s);
    if (s.isEmpty() || NOISE_TOKENS.contains(key) || !hasLetter(key)) {
      return Tok.NOISE;
    }
    Matcher dc = DC_CITY.matcher(s);
    if (dc.matches() && isPlace(dc.group(1).trim())) {
      return new Tok(Kind.CITY_STATE, titleCase(dc.group(1).trim()), "District of Columbia", US, false, null);
    }
    if (key.equals("ca")) {
      return new Tok(Kind.CA, null, null, null, false, null);
    }
    String country = COUNTRIES.get(key);
    if (country != null) {
      return new Tok(Kind.COUNTRY, country, null, null, false, null);
    }
    StateInfo state = STATES.get(key);
    if (state != null) {
      return new Tok(Kind.STATE, state.name(), null, state.country(), AMBIGUOUS_STATES.contains(key), null);
    }
    int dash = s.indexOf('-');
    if (dash > 0) {
      String prefix = key(s.substring(0, dash));
      String prefixCountry = prefix.equals("ca") ? CANADA : COUNTRIES.get(prefix);
      String rest = s.substring(dash + 1).trim();
      if (prefixCountry != null && !rest.isEmpty()) {
        return new Tok(Kind.PREFIX, null, null, prefixCountry, false, classify(rest));
      }
    }
    if (!isPlace(s)) {
      return Tok.NOISE;
    }
    return new Tok(Kind.PLACE, titleCase(s), null, null, false, null);
  }

  private static String clean(String raw) {
    String s = raw.trim().replaceAll("\\s+", " ");
    s = LEADING_NOISE.matcher(s).replaceFirst("");
    s = TRAILING_NOISE.matcher(s).replaceFirst("");
    s = LEADING_IN.matcher(s).replaceFirst("");
    s = EDGE_JUNK.matcher(s).replaceAll("");
    s = GREATER.matcher(s).replaceFirst("");
    s = TRAILING_AREA.matcher(s).replaceFirst("");
    return s.trim();
  }

  private static String key(String s) {
    return s.toLowerCase(Locale.ROOT).replace(".", "").replaceAll("\\s+", " ").trim();
  }

  private static boolean hasLetter(String s) {
    return s.codePoints().anyMatch(Character::isLetter);
  }

  private static boolean isPlace(String s) {
    return !s.isEmpty() && s.length() <= MAX_PLACE && PLACE_NAME.matcher(s).matches();
  }

  // Only fixes shouting or all-lowercase input; short all-caps tokens (NYC) are left alone.
  private static String titleCase(String s) {
    boolean upper = s.equals(s.toUpperCase(Locale.ROOT)) && s.length() > 4;
    boolean lower = s.equals(s.toLowerCase(Locale.ROOT));
    if (!upper && !lower) {
      return s;
    }
    StringBuilder sb = new StringBuilder();
    for (String word : s.split(" ")) {
      if (sb.length() > 0) {
        sb.append(' ');
      }
      if (!word.isEmpty()) {
        sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase(Locale.ROOT));
      }
    }
    return sb.toString();
  }

  // ---- row assembly ----

  private static final class Row {
    String city;
    String state;
    String country;

    Row(String city, String state, String country) {
      this.city = city;
      this.state = state;
      this.country = country;
    }
  }

  private static void parseSegment(List<Tok> toks, Set<ParsedLocation> out) {
    Row cur = null;
    for (int i = 0; i < toks.size(); i++) {
      Tok t = toks.get(i);
      switch (t.kind()) {
        case NOISE -> { }
        case PREFIX -> {
          emit(cur, out);
          cur = null;
          Row row = new Row(null, null, t.country());
          applyPrefixRest(row, t.rest());
          emit(row, out);
        }
        case CITY_STATE -> {
          emit(cur, out);
          cur = new Row(t.name(), t.state(), t.country());
        }
        case PLACE -> {
          if (canTakeRegion(cur, toks, i)) {
            cur.state = t.name();
          } else {
            emit(cur, out);
            cur = new Row(t.name(), null, null);
          }
        }
        case STATE -> cur = applyState(cur, t.name(), t.country(), t.ambiguous(), out);
        case CA -> cur = applyCa(cur, out);
        case COUNTRY -> cur = applyCountry(cur, t.name(), out);
      }
    }
    emit(cur, out);
  }

  // "City, Region, Country": an unknown place between a bare city and a country is its region.
  // Known limitation: "Berlin, Munich, Germany" reads Munich as a region.
  private static boolean canTakeRegion(Row cur, List<Tok> toks, int i) {
    return cur != null
        && cur.city != null
        && cur.state == null
        && cur.country == null
        && i + 1 < toks.size()
        && toks.get(i + 1).kind() == Kind.COUNTRY;
  }

  private static void applyPrefixRest(Row row, Tok rest) {
    if (rest == null) {
      return;
    }
    switch (rest.kind()) {
      case PLACE -> row.city = rest.name();
      case STATE -> {
        if (row.country.equals(rest.country())) {
          row.state = rest.name();
        }
      }
      case CITY_STATE -> {
        row.city = rest.name();
        row.state = rest.state();
      }
      default -> { }
    }
  }

  private static Row applyState(Row cur, String state, String country, boolean ambiguous, Set<ParsedLocation> out) {
    if (cur != null
        && cur.city != null
        && cur.state == null
        && (cur.country == null || cur.country.equals(country))) {
      cur.state = state;
      cur.country = country;
      return cur;
    }
    emit(cur, out);
    return ambiguous ? new Row(state, null, null) : new Row(null, state, country);
  }

  // "CA" is California after a city (San Francisco, CA) and Canada after a Canadian city or
  // province, or when it stands alone or as a prefix.
  private static Row applyCa(Row cur, Set<ParsedLocation> out) {
    boolean afterBareCity =
        cur != null
            && cur.city != null
            && cur.state == null
            && cur.country == null
            && !CANADIAN_CITIES.contains(key(cur.city));
    if (afterBareCity) {
      return applyState(cur, "California", US, false, out);
    }
    return applyCountry(cur, CANADA, out);
  }

  private static Row applyCountry(Row cur, String country, Set<ParsedLocation> out) {
    if (cur != null) {
      if (cur.country == null) {
        cur.country = country;
        return cur;
      }
      if (cur.country.equals(country)) {
        return cur;
      }
    }
    emit(cur, out);
    return new Row(CITY_STATES.contains(country) ? country : null, null, country);
  }

  private static void emit(Row row, Set<ParsedLocation> out) {
    if (row == null || out.size() >= MAX_ROWS) {
      return;
    }
    if (row.city != null || row.state != null || row.country != null) {
      out.add(new ParsedLocation(row.city, row.state, row.country));
    }
  }
}
