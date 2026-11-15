package com.careercopilot.jobs.scraper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CategoryClassifierTest {

  private static final String SWE = "Software Engineering";
  private static final String DATA = "Data & ML";

  static Stream<Arguments> byTitle() {
    return Stream.of(
        Arguments.of("Software Engineer", SWE),
        Arguments.of("Senior Backend Developer", SWE),
        Arguments.of("Frontend Engineer", SWE),
        Arguments.of("Senior Android Developer", SWE),
        Arguments.of("iOS Engineer", SWE),
        Arguments.of("QA Engineer", SWE),
        Arguments.of("Engineering Manager", SWE),
        Arguments.of("Software Design Engineer", SWE),
        Arguments.of("Security Engineer", "Security"),
        Arguments.of("Security Analyst", "Security"),
        Arguments.of("Data Engineer", DATA),
        Arguments.of("Senior Data Scientist", DATA),
        Arguments.of("Machine Learning Engineer", DATA),
        Arguments.of("Machine Learning Researcher", DATA),
        Arguments.of("Business Analyst", DATA),
        Arguments.of("AI Researcher", DATA),
        Arguments.of("Sales Engineer", "Sales"),
        Arguments.of("Account Executive", "Sales"),
        Arguments.of("Enterprise Account Executive, EMEA", "Sales"),
        Arguments.of("Solutions Architect", "Sales"),
        Arguments.of("Sales Development Representative", "Sales"),
        Arguments.of("Revenue Operations Manager", "Sales"),
        Arguments.of("Product Designer", "Design"),
        Arguments.of("UX Researcher", "Design"),
        Arguments.of("Brand Designer", "Design"),
        Arguments.of("Graphic Designer", "Design"),
        Arguments.of("Product Manager", "Product"),
        Arguments.of("Group Product Manager", "Product"),
        Arguments.of("Product Operations Manager", "Product"),
        Arguments.of("Product Marketing Manager", "Marketing"),
        Arguments.of("Content Marketing Manager", "Marketing"),
        Arguments.of("Growth Marketing Manager", "Marketing"),
        Arguments.of("Marketing Operations Manager", "Marketing"),
        Arguments.of("Developer Advocate", "Marketing"),
        Arguments.of("Technical Recruiter", "People & Recruiting"),
        Arguments.of("Recruiting Coordinator", "People & Recruiting"),
        Arguments.of("HR Business Partner", "People & Recruiting"),
        Arguments.of("Head of People", "People & Recruiting"),
        Arguments.of("Customer Support Engineer", "Customer Support"),
        Arguments.of("Technical Support Engineer", "Customer Support"),
        Arguments.of("Customer Success Manager", "Customer Support"),
        Arguments.of("Site Reliability Engineer", "IT & Infrastructure"),
        Arguments.of("DevOps Engineer", "IT & Infrastructure"),
        Arguments.of("Staff Platform Engineer", "IT & Infrastructure"),
        Arguments.of("IT Support Specialist", "IT & Infrastructure"),
        Arguments.of("Cloud Engineer", "IT & Infrastructure"),
        Arguments.of("Data Center Technician", "IT & Infrastructure"),
        Arguments.of("Corporate Counsel", "Legal"),
        Arguments.of("Compliance Manager", "Legal"),
        Arguments.of("Senior Accountant", "Finance"),
        Arguments.of("Financial Analyst", "Finance"),
        Arguments.of("Payroll Specialist", "Finance"),
        Arguments.of("Technical Program Manager", "Operations"),
        Arguments.of("Chief of Staff", "Operations"),
        Arguments.of("Executive Assistant", "Operations"),
        Arguments.of("Barista", "Other"),
        Arguments.of("Chief Executive Officer", "Other"));
  }

  @ParameterizedTest(name = "[{index}] title \"{0}\"")
  @MethodSource("byTitle")
  void classifiesByTitleWhenNoDepartment(String title, String expected) {
    assertEquals(expected, CategoryClassifier.classify(null, title));
    assertEquals(expected, CategoryClassifier.classify("  ", title));
  }

  static Stream<Arguments> byDepartment() {
    return Stream.of(
        Arguments.of("Engineering", "Software Engineering"),
        Arguments.of("Data Science", DATA),
        Arguments.of("Data Engineering", DATA),
        Arguments.of("Recruiting", "People & Recruiting"),
        Arguments.of("People", "People & Recruiting"),
        Arguments.of("HR", "People & Recruiting"),
        Arguments.of("People Operations", "People & Recruiting"),
        Arguments.of("Customer Success", "Customer Support"),
        Arguments.of("Support", "Customer Support"),
        Arguments.of("Customer Support Engineering", "Customer Support"),
        Arguments.of("Legal", "Legal"),
        Arguments.of("Compliance", "Legal"),
        Arguments.of("Finance", "Finance"),
        Arguments.of("Accounting", "Finance"),
        Arguments.of("Sales", "Sales"),
        Arguments.of("Business Development", "Sales"),
        Arguments.of("Account Management", "Sales"),
        Arguments.of("Sales Engineering", "Sales"),
        Arguments.of("Sales Operations", "Sales"),
        Arguments.of("Marketing", "Marketing"),
        Arguments.of("Brand", "Marketing"),
        Arguments.of("Growth", "Marketing"),
        Arguments.of("Product Marketing", "Marketing"),
        Arguments.of("Marketing Operations", "Marketing"),
        Arguments.of("Design", "Design"),
        Arguments.of("UX", "Design"),
        Arguments.of("Product Design", "Design"),
        Arguments.of("Product", "Product"),
        Arguments.of("Security", "Security"),
        Arguments.of("Trust & Safety", "Security"),
        Arguments.of("Security Engineering", "Security"),
        Arguments.of("IT", "IT & Infrastructure"),
        Arguments.of("Infrastructure", "IT & Infrastructure"),
        Arguments.of("Platform", "IT & Infrastructure"),
        Arguments.of("SRE", "IT & Infrastructure"),
        Arguments.of("DevOps", "IT & Infrastructure"),
        Arguments.of("Operations", "Operations"),
        Arguments.of("Business Ops", "Operations"),
        Arguments.of("Strategy", "Operations"));
  }

  @ParameterizedTest(name = "[{index}] department \"{0}\"")
  @MethodSource("byDepartment")
  void departmentWinsOverTitle(String department, String expected) {
    // A misleading title proves the department rule is applied first.
    assertEquals(expected, CategoryClassifier.classify(department, "Barista"));
    assertEquals(expected, CategoryClassifier.classify(department, null));
  }

  @Test
  void departmentBeatsConflictingTitle() {
    assertEquals("Software Engineering", CategoryClassifier.classify("Engineering", "Data Analyst"));
    assertEquals("Sales", CategoryClassifier.classify("Sales", "Software Engineer"));
  }

  @Test
  void unmappedDepartmentFallsBackToTitle() {
    assertEquals("Software Engineering", CategoryClassifier.classify("General", "Software Engineer"));
    assertEquals("Sales", CategoryClassifier.classify("   ", "Account Executive"));
    assertEquals("Other", CategoryClassifier.classify("Executive", "Chief Executive Officer"));
  }

  @Test
  void matchingIsCaseInsensitiveAndWordBounded() {
    assertEquals("Security", CategoryClassifier.classify("SECURITY", null));
    assertEquals("Other", CategoryClassifier.classify("Digitalization", "Faithful Barista"));
  }

  @Test
  void blankInputsAreOther() {
    assertEquals("Other", CategoryClassifier.classify(null, null));
    assertEquals("Other", CategoryClassifier.classify("", ""));
    assertEquals("Other", CategoryClassifier.classify("  ", "\t"));
  }

  @Test
  void categoryListIsExact() {
    assertEquals(
        List.of("Software Engineering", "Data & ML", "Product", "Design", "Security",
            "IT & Infrastructure", "Sales", "Marketing", "Customer Support", "Operations",
            "Finance", "Legal", "People & Recruiting", "Other"),
        CategoryClassifier.CATEGORIES);
    assertTrue(CategoryClassifier.CATEGORIES.contains(CategoryClassifier.classify("x", "y")));
  }
}
