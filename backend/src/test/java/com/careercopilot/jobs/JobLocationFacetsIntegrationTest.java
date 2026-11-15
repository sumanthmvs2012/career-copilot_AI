package com.careercopilot.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careercopilot.auth.UserRepository;
import com.careercopilot.common.ratelimit.RateLimiter;
import com.careercopilot.profiles.ProfileRepository;
import com.careercopilot.resumes.ResumeRepository;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Category and place filters plus /jobs/facets, on the same H2 setup as the other job endpoint tests. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobLocationFacetsIntegrationTest {

    private static final String US = "United States";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private ProfileRepository profiles;

    @Autowired
    private ResumeRepository resumes;

    @Autowired
    private JobRepository jobs;

    @Autowired
    private JobLocationRepository locations;

    @Autowired
    private CompanyRepository companies;

    @Autowired
    private RateLimiter rateLimiter;

    private String jane;
    private String john;

    @BeforeEach
    void setUp() throws Exception {
        clean();
        rateLimiter.reset();
        jane = tokenWithProfile("jane@example.com");
        john = tokenWithProfile("john@example.com");
    }

    @AfterEach
    void clean() {
        locations.deleteAll();
        jobs.deleteAll();
        companies.deleteAll();
        resumes.deleteAll();
        profiles.deleteAll();
        users.deleteAll();
    }

    private String tokenWithProfile(String email) throws Exception {
        String json = mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"secret-pass\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String token = "Bearer " + JsonPath.read(json, "$.token");
        mvc.perform(put("/profile").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Some One\"}"))
                .andExpect(status().isOk());
        return token;
    }

    /** Places are given as {city, state, country} triples. */
    private void scraped(String title, String category, int daysAgo, String[]... places) {
        Job job = new Job("greenhouse", title, "Scraped description");
        job.setExternalId(title);
        job.setCategory(category);
        job.setPostedAt(Instant.now().minus(Duration.ofDays(daysAgo)));
        Job saved = jobs.save(job);
        for (String[] place : places) {
            locations.save(new JobLocation(saved.getId(), place[0], place[1], place[2]));
        }
    }

    private static String[] place(String city, String state, String country) {
        return new String[] {city, state, country};
    }

    private void seed() {
        scraped("Backend Engineer", "Software Engineering", 1,
                place("Austin", "Texas", US), place("Berlin", null, "Germany"));
        scraped("Designer", "Design", 2, place("Austin", "Texas", US));
        scraped("Sales Rep", "Sales", 3, place("Portland", "Oregon", US), place("Portland", "Maine", US));
        scraped("Dev UK", "Software Engineering", 4, place("London", null, "United Kingdom"));
        scraped("No Location", "Software Engineering", 5);
    }

    private void paste(String token, String title, String location) throws Exception {
        mvc.perform(post("/jobs/match").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"description\":\"Build and run backend services in Java.\","
                                + "\"location\":\"" + location + "\"}"))
                .andExpect(status().isCreated());
    }

    private List<String> titles(String token, String query) throws Exception {
        String json = mvc.perform(get(URI.create("/jobs" + query)).header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new ArrayList<>(JsonPath.<List<String>>read(json, "$[*].title"));
    }

    /** Returns "value:count" strings for one facet group, in response order. */
    private List<String> facet(String token, String query, String group) throws Exception {
        String json = mvc.perform(get(URI.create("/jobs/facets" + query)).header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> values = JsonPath.read(json, "$." + group + "[*].value");
        List<Integer> counts = JsonPath.read(json, "$." + group + "[*].count");
        List<String> out = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            out.add(values.get(i) + ":" + counts.get(i));
        }
        return out;
    }

    @Test
    void categoryFilterIsCaseInsensitiveAndCombinesWithOtherFilters() throws Exception {
        seed();

        assertThat(titles(jane, "?category=design")).containsExactly("Designer");
        assertThat(titles(jane, "?category=SOFTWARE%20ENGINEERING"))
                .containsExactly("Backend Engineer", "Dev UK", "No Location");
        assertThat(titles(jane, "?category=Software%20Engineering&country=" + US.replace(" ", "%20")))
                .containsExactly("Backend Engineer");
        assertThat(titles(jane, "?category=Design&source=lever")).isEmpty();
    }

    @Test
    void countryStateAndCityMatchExactlyIgnoringCase() throws Exception {
        seed();

        assertThat(titles(jane, "?country=united%20states")).containsExactly("Backend Engineer", "Designer", "Sales Rep");
        assertThat(titles(jane, "?country=GERMANY")).containsExactly("Backend Engineer");
        assertThat(titles(jane, "?state=texas")).containsExactly("Backend Engineer", "Designer");
        assertThat(titles(jane, "?city=%20austin%20")).containsExactly("Backend Engineer", "Designer");
        assertThat(titles(jane, "?city=aust")).isEmpty();
        assertThat(titles(jane, "?city=Portland&state=Maine")).containsExactly("Sales Rep");
    }

    @Test
    void allGivenPlacePartsMustMatchTheSameLocationRow() throws Exception {
        seed();

        // The backend job has Austin (US) and Berlin (Germany) on different rows.
        assertThat(titles(jane, "?country=Germany&city=Austin")).isEmpty();
        assertThat(titles(jane, "?country=Germany&city=Berlin")).containsExactly("Backend Engineer");
        assertThat(titles(jane, "?city=Portland&state=Texas")).isEmpty();
        assertThat(titles(jane, "?country=United%20States&state=Oregon&city=Portland")).containsExactly("Sales Rep");
    }

    @Test
    void responseCarriesCategoryAndLocations() throws Exception {
        seed();

        String json = mvc.perform(get(URI.create("/jobs?category=Sales")).header("Authorization", jane))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(json, "$[0].category")).isEqualTo("Sales");
        assertThat(JsonPath.<List<String>>read(json, "$[0].locations[*].city")).containsExactly("Portland", "Portland");
        assertThat(JsonPath.<List<String>>read(json, "$[0].locations[*].state")).containsExactly("Oregon", "Maine");
        String none = mvc.perform(get(URI.create("/jobs?q=No%20Location")).header("Authorization", jane))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Object>>read(none, "$[0].locations")).isEmpty();
    }

    @Test
    void pastedJobsGetACategoryAndParsedLocations() throws Exception {
        String json = mvc.perform(post("/jobs/match").header("Authorization", jane)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Senior Backend Engineer\",\"description\":\"Build and run backend services in Java.\","
                                + "\"location\":\"Berlin, Germany\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(json, "$.job.category")).isEqualTo("Software Engineering");
        assertThat(JsonPath.<List<String>>read(json, "$.job.locations[*].country")).containsExactly("Germany");
        assertThat(titles(jane, "?scope=pasted&city=berlin&category=software%20engineering"))
                .containsExactly("Senior Backend Engineer");
    }

    @Test
    void invalidCategoryAndPlaceValuesReturn400() throws Exception {
        String[] bad = {"category=Astronaut", "category=", "category=%20", "country=", "state=%20",
                "city=", "country=" + "a".repeat(121), "state=" + "a".repeat(121), "city=" + "a".repeat(121)};
        for (String query : bad) {
            mvc.perform(get(URI.create("/jobs?" + query)).header("Authorization", jane))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").isString());
        }
        assertThat(titles(jane, "?city=" + "a".repeat(120))).isEmpty();
    }

    @Test
    void facetsCountDistinctJobsMostCommonFirstWithValueAsTieBreaker() throws Exception {
        seed();

        assertThat(facet(jane, "", "categories"))
                .containsExactly("Software Engineering:3", "Design:1", "Sales:1");
        assertThat(facet(jane, "", "countries"))
                .containsExactly("United States:3", "Germany:1", "United Kingdom:1");
        assertThat(facet(jane, "", "states")).containsExactly("Texas:2", "Maine:1", "Oregon:1");
        // Portland has two rows on one job, which still counts once.
        assertThat(facet(jane, "", "cities"))
                .containsExactly("Austin:2", "Berlin:1", "London:1", "Portland:1");
    }

    @Test
    void statesAndCitiesCascadeFromCountryAndState() throws Exception {
        seed();

        assertThat(facet(jane, "?country=united%20states", "states"))
                .containsExactly("Texas:2", "Maine:1", "Oregon:1");
        assertThat(facet(jane, "?country=Germany", "states")).isEmpty();
        assertThat(facet(jane, "?country=united%20states", "cities")).containsExactly("Austin:2", "Portland:1");
        assertThat(facet(jane, "?country=United%20States&state=texas", "cities")).containsExactly("Austin:2");
        assertThat(facet(jane, "?country=Germany", "cities")).containsExactly("Berlin:1");
        assertThat(facet(jane, "?country=United%20States&state=Nowhere", "cities")).isEmpty();
        // Countries and categories ignore the cascade params.
        assertThat(facet(jane, "?country=Germany", "countries")).hasSize(3);
    }

    @Test
    void citiesAreCappedAtTheHundredMostCommon() throws Exception {
        for (int i = 0; i < 105; i++) {
            scraped("Job " + i, "Other", 1, place(String.format("City %03d", i), null, "Freedonia"));
        }

        assertThat(facet(jane, "?country=Freedonia", "cities")).hasSize(100);
    }

    @Test
    void scopeDefaultsToScrapedAndAnInvalidScopeIs400() throws Exception {
        seed();
        paste(jane, "Janes Pasted Designer", "Berlin, Germany");

        assertThat(facet(jane, "", "categories")).containsExactly("Software Engineering:3", "Design:1", "Sales:1");
        assertThat(facet(jane, "?scope=scraped", "countries")).contains("Germany:1");
        assertThat(facet(jane, "?scope=pasted", "countries")).containsExactly("Germany:1");
        assertThat(facet(jane, "?scope=PASTED", "categories")).containsExactly("Design:1");
        for (String bad : new String[] {"?scope=all", "?scope=bogus", "?country=", "?state=%20",
                "?country=" + "a".repeat(121)}) {
            mvc.perform(get(URI.create("/jobs/facets" + bad)).header("Authorization", jane))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    @Test
    void anotherUsersPastedJobsNeverAppearInTheListOrInFacets() throws Exception {
        seed();
        paste(john, "Johns Secret Engineer", "Paris, France");
        paste(jane, "Janes Engineer", "Berlin, Germany");

        for (String query : new String[] {"?country=France", "?city=Paris", "?scope=pasted&country=France",
                "?scope=pasted&city=Paris&category=Software%20Engineering", "?q=secret&country=France"}) {
            assertThat(titles(jane, query)).as("jane " + query).isEmpty();
        }
        assertThat(titles(jane, "?scope=pasted&category=Software%20Engineering")).containsExactly("Janes Engineer");
        assertThat(titles(john, "?country=France")).containsExactly("Johns Secret Engineer");

        for (String scope : new String[] {"", "?scope=scraped", "?scope=pasted"}) {
            assertThat(facet(jane, scope, "countries")).as("jane countries " + scope)
                    .noneMatch(value -> value.startsWith("France"));
            assertThat(facet(jane, scope, "cities")).as("jane cities " + scope)
                    .noneMatch(value -> value.startsWith("Paris"));
        }
        assertThat(facet(jane, "?scope=pasted", "categories")).containsExactly("Software Engineering:1");
        assertThat(facet(jane, "?scope=pasted&country=France", "cities")).isEmpty();
        assertThat(facet(john, "?scope=pasted", "countries")).containsExactly("France:1");
        assertThat(facet(john, "?scope=pasted", "cities")).containsExactly("Paris:1");
        // The scraped scope never contains pasted jobs of anyone.
        assertThat(facet(john, "?scope=scraped", "countries")).noneMatch(value -> value.startsWith("France"));
    }

    @Test
    void categoryNarrowsCountriesStatesAndCitiesButNotItself() throws Exception {
        seed();

        assertThat(facet(jane, "?category=design", "countries")).containsExactly("United States:1");
        assertThat(facet(jane, "?category=design", "states")).containsExactly("Texas:1");
        assertThat(facet(jane, "?category=Design", "cities")).containsExactly("Austin:1");
        assertThat(facet(jane, "?category=Design", "categories"))
                .containsExactly("Software Engineering:3", "Design:1", "Sales:1");
        assertThat(facet(jane, "?category=Software%20Engineering", "countries"))
                .containsExactly("Germany:1", "United Kingdom:1", "United States:1");
        assertThat(facet(jane, "?category=Sales", "states")).containsExactly("Maine:1", "Oregon:1");
    }

    @Test
    void countryNarrowsCategoriesStatesAndCitiesButNotItself() throws Exception {
        seed();

        assertThat(facet(jane, "?country=Germany", "categories")).containsExactly("Software Engineering:1");
        assertThat(facet(jane, "?country=united%20states", "categories"))
                .containsExactly("Design:1", "Sales:1", "Software Engineering:1");
        assertThat(facet(jane, "?country=Germany", "cities")).containsExactly("Berlin:1");
        assertThat(facet(jane, "?country=Germany", "countries"))
                .containsExactly("United States:3", "Germany:1", "United Kingdom:1");
    }

    @Test
    void stateAndCityNarrowTheOtherListsOnTheSameLocationRow() throws Exception {
        seed();

        assertThat(facet(jane, "?state=texas", "cities")).containsExactly("Austin:2");
        assertThat(facet(jane, "?state=texas", "countries")).containsExactly("United States:2");
        assertThat(facet(jane, "?state=texas", "categories")).containsExactly("Design:1", "Software Engineering:1");
        // Its own dimension is ignored, so other states stay selectable.
        assertThat(facet(jane, "?state=texas", "states")).containsExactly("Texas:2", "Maine:1", "Oregon:1");
        assertThat(facet(jane, "?city=Austin", "states")).containsExactly("Texas:2");
        assertThat(facet(jane, "?city=Portland", "states")).containsExactly("Maine:1", "Oregon:1");
        assertThat(facet(jane, "?country=United%20States&category=Software%20Engineering", "cities"))
                .containsExactly("Austin:1");
    }

    @Test
    void facetsAlsoHonourTheOtherListFilters() throws Exception {
        seed();

        assertThat(facet(jane, "?q=designer", "categories")).containsExactly("Design:1");
        assertThat(facet(jane, "?q=designer", "countries")).containsExactly("United States:1");
        scraped("Old Marketer", "Marketing", 40, place("Rome", null, "Italy"));
        assertThat(facet(jane, "", "categories")).contains("Marketing:1");
        assertThat(facet(jane, "?postedWithin=30", "categories")).noneMatch(value -> value.startsWith("Marketing"));
        assertThat(facet(jane, "?postedWithin=30", "countries")).noneMatch(value -> value.startsWith("Italy"));
        assertThat(facet(jane, "?source=lever", "categories")).isEmpty();
        assertThat(facet(jane, "?remoteType=remote", "countries")).isEmpty();
    }

    @Test
    void facetsRejectInvalidListFilters() throws Exception {
        for (String bad : new String[] {"?category=Astronaut", "?category=", "?q=", "?q=" + "a".repeat(101),
                "?source=bogus", "?remoteType=moon", "?postedWithin=5", "?location=", "?city=", "?city=%20"}) {
            mvc.perform(get(URI.create("/jobs/facets" + bad)).header("Authorization", jane))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    @Test
    void crossFilteredFacetsNeverLeakAnotherUsersPastedJobs() throws Exception {
        seed();
        paste(john, "Johns Secret Engineer", "Paris, France");

        for (String query : new String[] {"?category=Software%20Engineering", "?country=France",
                "?scope=pasted&category=Software%20Engineering", "?scope=pasted&country=France", "?q=secret"}) {
            for (String group : new String[] {"categories", "countries", "states", "cities"}) {
                assertThat(facet(jane, query, group)).as("jane " + query + " " + group)
                        .noneMatch(value -> value.startsWith("France") || value.startsWith("Paris"));
            }
        }
        assertThat(facet(jane, "?scope=pasted&country=France", "categories")).isEmpty();
        assertThat(facet(john, "?scope=pasted&country=France", "categories")).containsExactly("Software Engineering:1");
        assertThat(facet(john, "?scope=pasted&category=Software%20Engineering", "cities")).containsExactly("Paris:1");
    }

    @Test
    void facetsNeedAToken() throws Exception {
        mvc.perform(get("/jobs/facets")).andExpect(status().isUnauthorized());
    }
}
