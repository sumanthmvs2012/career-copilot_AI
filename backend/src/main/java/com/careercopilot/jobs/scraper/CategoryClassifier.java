package com.careercopilot.jobs.scraper;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Maps a board's department/team, then the job title, onto the fixed category list. Rule order
 * matters: the more specific function wins (Security Engineer is Security, not Software).
 */
public final class CategoryClassifier {

  public static final String SOFTWARE = "Software Engineering";
  public static final String DATA = "Data & ML";
  public static final String PRODUCT = "Product";
  public static final String DESIGN = "Design";
  public static final String SECURITY = "Security";
  public static final String IT = "IT & Infrastructure";
  public static final String SALES = "Sales";
  public static final String MARKETING = "Marketing";
  public static final String SUPPORT = "Customer Support";
  public static final String OPERATIONS = "Operations";
  public static final String FINANCE = "Finance";
  public static final String LEGAL = "Legal";
  public static final String PEOPLE = "People & Recruiting";
  public static final String OTHER = "Other";

  public static final List<String> CATEGORIES =
      List.of(SOFTWARE, DATA, PRODUCT, DESIGN, SECURITY, IT, SALES, MARKETING, SUPPORT,
          OPERATIONS, FINANCE, LEGAL, PEOPLE, OTHER);

  private record Rule(String category, Pattern pattern) {
    Rule(String category, String alternatives) {
      this(category, Pattern.compile("\\b(?:" + alternatives + ")\\b", Pattern.CASE_INSENSITIVE));
    }
  }

  private static final List<Rule> DEPARTMENT_RULES =
      List.of(
          new Rule(SECURITY, "security|infosec|cyber\\w*|trust|appsec"),
          new Rule(DATA, "data|machine learning|ml|ai|analytics|business intelligence|bi"),
          new Rule(PEOPLE, "people|recruit\\w*|talent|hr|human resources|human capital"),
          new Rule(LEGAL, "legal|compliance|counsel|regulatory"),
          new Rule(FINANCE, "finance|financial|accounting|accounts payable|accounts receivable|payroll|treasury|tax|audit|fp&a"),
          new Rule(IT, "it|information technology|infrastructure|platform|sre|devops|site reliability|systems|network|cloud|sysadmin"),
          new Rule(SUPPORT, "customer success|customer support|customer service|customer experience|customer operations|client success|support|cx"),
          new Rule(SALES, "sales|business development|bd|accounts?|account management|partnerships?|revenue|go-to-market|gtm|field|channel"),
          new Rule(DESIGN, "design|ux|ui|creative|user experience|user research"),
          new Rule(MARKETING, "marketing|brand|growth|communications|content|seo|pr|public relations|demand generation|community|developer relations|advocacy"),
          new Rule(SOFTWARE, "engineering|engineers?|software|developers?|dev|r&d|research and development"),
          new Rule(PRODUCT, "product|product management"),
          new Rule(OPERATIONS, "operations?|ops|business ops|biz ops|strategy|program management|chief of staff|facilities|workplace|logistics|supply chain|procurement|administration|admin|g&a"));

  private static final List<Rule> TITLE_RULES =
      List.of(
          new Rule(SECURITY, "security|infosec|cyber\\w*|penetration|pentest\\w*|appsec|threat|vulnerability|soc analyst|trust (?:and|&) safety|fraud"),
          new Rule(PEOPLE, "recruiter|recruiting|recruitment|talent|sourcer|hr|human resources|head of people|chief people officer|people (?:partner|business partner|operations|ops|experience|success|programs?|analytics?)"),
          new Rule(LEGAL, "legal|counsel|attorney|paralegal|lawyer|compliance"),
          new Rule(FINANCE, "finance|financial|accountant|accounting|controller|fp&a|payroll|treasury|auditor|audit|tax|bookkeeper|accounts payable|accounts receivable"),
          new Rule(IT, "it|help ?desk|sysadmin|systems? administrator|systems? engineer|devops|sre|site reliability|network (?:engineer|administrator|architect)|infrastructure (?:engineer|architect|manager)|cloud (?:engineer|architect|operations|administrator)|platform engineer|data cent(?:er|re)|dba|database administrator|desktop support|endpoint"),
          new Rule(SUPPORT, "support|customer (?:success|service|care|experience|operations)|client success"),
          new Rule(SALES, "sales|account executive|account manager|account director|account representative|business development|bdr|sdr|solutions? engineer|solutions? architect|pre-?sales|partnerships?|partner manager|revenue|channel|territory"),
          new Rule(DESIGN, "designer|design(?!\\s+engineer)|ux|user experience|graphic|illustrator|creative director|art director|visual|motion"),
          new Rule(MARKETING, "marketing|seo|content|copywriter|communications|brand|social media|community|growth|demand gen\\w*|lifecycle|public relations|pr|developer advocate|developer relations|devrel|evangelist|technical writer"),
          new Rule(PRODUCT, "product (?:manager|management|owner|lead|director|operations|ops|specialist|analyst)|head of product|chief product officer|(?:vp|vice president)(?: of)? product"),
          new Rule(DATA, "data|machine learning|ml|ai|artificial intelligence|analytics?|analyst|research scientist|applied scientist|scientist|statistician|business intelligence|bi|mlops|nlp|computer vision|deep learning|llm"),
          new Rule(OPERATIONS, "operations?|ops|program manager|programme manager|project manager|chief of staff|strategy|logistics|supply chain|procurement|office manager|executive assistant|administrative|facilities|workplace"),
          new Rule(SOFTWARE, "engineer|engineering|developer|software|programmer|swe|sde|full[- ]?stack|front[- ]?end|back[- ]?end|mobile|ios|android|web|qa|sdet|quality assurance|test automation|architect|firmware|embedded|development|tech lead|technical lead"));

  private CategoryClassifier() {}

  /** Never returns null; unmapped or blank input is "Other". */
  public static String classify(String department, String title) {
    String byDepartment = firstMatch(DEPARTMENT_RULES, department);
    if (byDepartment != null) {
      return byDepartment;
    }
    String byTitle = firstMatch(TITLE_RULES, title);
    return byTitle != null ? byTitle : OTHER;
  }

  private static String firstMatch(List<Rule> rules, String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    for (Rule rule : rules) {
      if (rule.pattern().matcher(text).find()) {
        return rule.category();
      }
    }
    return null;
  }
}
