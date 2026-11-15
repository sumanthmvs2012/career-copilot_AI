package com.careercopilot.jobs.scraper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LocationParserTest {

  private static final String US = "United States";

  // Rows are rendered as city|state|country (empty for null) and joined with "; ".
  private static String render(List<ParsedLocation> locations) {
    return locations.stream()
        .map(l -> nz(l.city()) + "|" + nz(l.state()) + "|" + nz(l.country()))
        .collect(Collectors.joining("; "));
  }

  private static String nz(String s) {
    return s == null ? "" : s;
  }

  static Stream<Arguments> rows() {
    return Stream.of(
        Arguments.of("San Francisco, Seattle, Chicago or Remote", "San Francisco||; Seattle||; Chicago||"),
        Arguments.of(
            "US-Chicago, US-Seattle, US-San Francisco, US-Remote",
            "Chicago||" + US + "; Seattle||" + US + "; San Francisco||" + US + "; ||" + US),
        Arguments.of("New York, NY; Austin, TX", "New York|New York|" + US + "; Austin|Texas|" + US),
        Arguments.of("London, UK", "London||United Kingdom"),
        Arguments.of("Bengaluru, Karnataka, India", "Bengaluru|Karnataka|India"),
        Arguments.of("Singapore", "Singapore||Singapore"),
        Arguments.of("Toronto, ON, Canada", "Toronto|Ontario|Canada"),
        Arguments.of("Remote - Canada", "||Canada"),
        Arguments.of("Washington DC-metro area", "Washington|District of Columbia|" + US),
        Arguments.of("Seattle, WA", "Seattle|Washington|" + US),
        Arguments.of("na", ""),
        Arguments.of("N/A", ""),
        Arguments.of("Remote", ""),
        Arguments.of("Anywhere", ""),
        Arguments.of("Hybrid", ""),
        Arguments.of("Austin, Texas", "Austin|Texas|" + US),
        Arguments.of("Boston, MA, USA", "Boston|Massachusetts|" + US),
        Arguments.of("Dublin, Ireland", "Dublin||Ireland"),
        Arguments.of("Berlin, Germany", "Berlin||Germany"),
        Arguments.of("Paris, France | Remote", "Paris||France"),
        Arguments.of("Vancouver, BC", "Vancouver|British Columbia|Canada"),
        Arguments.of("Montreal, Quebec, Canada", "Montreal|Quebec|Canada"),
        Arguments.of("Sydney, Australia", "Sydney||Australia"),
        Arguments.of("Remote, US", "||" + US),
        Arguments.of("United States", "||" + US),
        Arguments.of("Remote (US)", "||" + US),
        Arguments.of("Seattle, WA (Hybrid)", "Seattle|Washington|" + US),
        Arguments.of("Seattle, WA, Remote", "Seattle|Washington|" + US),
        Arguments.of("Toronto, CA", "Toronto||Canada"),
        Arguments.of("Toronto, ON, CA", "Toronto|Ontario|Canada"),
        Arguments.of("San Jose, CA", "San Jose|California|" + US),
        Arguments.of("Irvine, CA, United States", "Irvine|California|" + US),
        Arguments.of("CA", "||Canada"),
        Arguments.of("Portland, OR or Remote", "Portland|Oregon|" + US),
        Arguments.of("Chicago, IL; Remote", "Chicago|Illinois|" + US),
        Arguments.of("Remote - US", "||" + US),
        Arguments.of("Hybrid - London, UK", "London||United Kingdom"),
        Arguments.of("Austin, TX or Remote - US", "Austin|Texas|" + US + "; ||" + US),
        Arguments.of("NYC", "New York||"),
        Arguments.of("SAN FRANCISCO", "San Francisco||"),
        Arguments.of("berlin, germany", "Berlin||Germany"),
        Arguments.of("seattle, wa", "Seattle|Washington|" + US),
        Arguments.of("Zurich, Switzerland", "Zurich||Switzerland"),
        Arguments.of("São Paulo, Brazil", "São Paulo||Brazil"),
        Arguments.of("Tokyo, Japan", "Tokyo||Japan"),
        Arguments.of(
            "Seattle, WA; Washington, DC",
            "Seattle|Washington|" + US + "; Washington|District of Columbia|" + US),
        Arguments.of("Washington DC", "Washington|District of Columbia|" + US),
        Arguments.of("Washington, D.C.", "Washington|District of Columbia|" + US),
        Arguments.of("Mexico City, Mexico", "Mexico City||Mexico"),
        Arguments.of("Tel Aviv, Israel", "Tel Aviv||Israel"),
        Arguments.of("Bangalore, India / Hyderabad, India", "Bengaluru||India; Hyderabad||India"),
        Arguments.of("Gurgaon, Haryana, India", "Gurgaon|Haryana|India"),
        Arguments.of("Remote, EMEA", ""),
        Arguments.of("Multiple Locations", ""),
        Arguments.of("12345", ""),
        Arguments.of("Remote, USA", "||" + US),
        Arguments.of("Brooklyn, NY, United States", "Brooklyn|New York|" + US),
        Arguments.of("Edinburgh, Scotland", "Edinburgh||United Kingdom"),
        Arguments.of("Cambridge, England, UK", "Cambridge||United Kingdom"),
        Arguments.of("Seattle, Washington, United States", "Seattle|Washington|" + US),
        Arguments.of("New York, New York", "New York|New York|" + US),
        Arguments.of("New York", "New York||"),
        Arguments.of("Washington", "Washington||"),
        Arguments.of("Washington, USA", "Washington||" + US),
        Arguments.of("California", "|California|" + US),
        Arguments.of("Texas, USA", "|Texas|" + US),
        Arguments.of("Ontario", "|Ontario|Canada"),
        Arguments.of("Ottawa, Ontario", "Ottawa|Ontario|Canada"),
        Arguments.of("Berlin; Munich; Hamburg", "Berlin||; Munich||; Hamburg||"),
        Arguments.of("Seattle / Bellevue, WA", "Seattle||; Bellevue|Washington|" + US),
        Arguments.of("Denver, CO", "Denver|Colorado|" + US),
        Arguments.of("Lagos, Nigeria", "Lagos||Nigeria"),
        Arguments.of("Remote - Anywhere", ""),
        Arguments.of("Anywhere in the world", ""),
        Arguments.of("Remote in the US", "||" + US),
        Arguments.of("Onsite, Hybrid", ""),
        Arguments.of("O'Fallon, MO", "O'Fallon|Missouri|" + US),
        Arguments.of("St. Louis, MO", "St. Louis|Missouri|" + US),
        Arguments.of("Winston-Salem, NC", "Winston-Salem|North Carolina|" + US),
        Arguments.of("India-Bangalore", "Bengaluru||India"),
        Arguments.of("UK-London", "London||United Kingdom"),
        Arguments.of("CA-Toronto", "Toronto||Canada"),
        Arguments.of("Germany-Remote", "||Germany"),
        Arguments.of("Greater Seattle Area", "Seattle||"),
        Arguments.of("Singapore, Singapore", "Singapore||Singapore"),
        Arguments.of("Seattle, WA / Seattle, WA", "Seattle|Washington|" + US),
        Arguments.of("Room 12 #5 !!", ""),
        Arguments.of("Dallas, TX, US; Remote", "Dallas|Texas|" + US),
        // A city between a city and a US/Canada country is a city, never a state.
        Arguments.of("Seattle, Atlanta, United States", "Seattle||; Atlanta||" + US),
        Arguments.of("San Francisco, Seattle, US", "San Francisco||; Seattle||" + US),
        Arguments.of("Toronto, Atlanta, Canada", "Toronto||; Atlanta||Canada"),
        Arguments.of("Sydney, Melbourne, Australia", "Sydney||; Melbourne||Australia"),
        // Aliases collapse to one spelling.
        Arguments.of("SF", "San Francisco||"),
        Arguments.of("San Francisco, CA", "San Francisco|California|" + US),
        Arguments.of("SF, CA", "San Francisco|California|" + US),
        Arguments.of("New York City", "New York||"),
        Arguments.of("New York City, NY", "New York|New York|" + US),
        Arguments.of("NYC; New York", "New York||"),
        Arguments.of("SEA", "Seattle||"),
        Arguments.of("Seattle; SEA", "Seattle||"),
        Arguments.of("IN-Bengaluru", "Bengaluru||India"),
        Arguments.of("Bangalore", "Bengaluru||"),
        Arguments.of("Bengaluru, Karnataka, India", "Bengaluru|Karnataka|India"),
        Arguments.of("NYC-Privy", "New York||"),
        // Junk tokens and countries.
        Arguments.of("Location", ""),
        Arguments.of("Location, Remote", ""),
        Arguments.of("Taiwan", "||Taiwan"),
        Arguments.of("Taipei, Taiwan", "Taipei||Taiwan"));
  }

  @ParameterizedTest(name = "[{index}] \"{0}\"")
  @MethodSource("rows")
  void parsesRealisticLocations(String input, String expected) {
    assertEquals(expected, render(LocationParser.parse(input)));
  }

  @Test
  void returnsEmptyForNullAndBlank() {
    assertTrue(LocationParser.parse(null).isEmpty());
    assertTrue(LocationParser.parse("").isEmpty());
    assertTrue(LocationParser.parse("   \n ").isEmpty());
  }

  @Test
  void usesStructuredFieldsOfRecord() {
    assertEquals(
        List.of(new ParsedLocation("Austin", "Texas", US)), LocationParser.parse("Austin, TX"));
  }

  @Test
  void capsAtTwentyRowsInOrder() {
    String names =
        "Alpha, Bravo, Charlie, Delta, Echo, Foxtrot, Golf, Hotel, India City, Juliet, Kilo, Lima, "
            + "Mike, November, Oscar, Papa, Quebec City, Romeo, Sierra, Tango, Uniform, Victor";
    List<ParsedLocation> parsed = LocationParser.parse(names);
    assertEquals(20, parsed.size());
    assertEquals("Alpha", parsed.get(0).city());
    assertEquals("Tango", parsed.get(19).city());
  }

  @Test
  void ignoresOverlongAndNonPlaceTokens() {
    assertTrue(LocationParser.parse("A".repeat(61)).isEmpty());
    assertTrue(LocationParser.parse("x".repeat(5000).replace('x', '1')).isEmpty());
  }

  @Test
  void neverThrowsOnOddPunctuation() {
    for (String odd : new String[] {",", ",,,", ";;;", "(", ")", "((Remote)", "- - -", "/", "|", " or ", "US-", "-US", "CA-"}) {
      LocationParser.parse(odd);
    }
  }
}
