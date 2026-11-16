DESIGN_SYSTEM

Part one: the design direction. The style is clean and minimal, with a technical feel that suits a job search tool. 
          Keep the look consistent across screens, and make sure it works on phones and desktops.
		  
Part two: colors and type. Use one main accent color for buttons and links, a light background, and dark text. 
          Success, warning, and error each get their own color. Use a single sans-serif font for everything, with clear heading sizes.
		  
Part three: components and states. Buttons have primary, secondary, and danger styles. Inputs show a clear focus outline and an error message. 
            Every screen needs loading, empty, error, and success states. Layouts stack on mobile and widen on desktop

Part four: dashboard colorful extension (approved addition, extends parts one and two).
            Part two says "one main accent color". The dashboard now also uses four per-card accents as category cues only:
            profile blue (--accent-profile), resume violet (--accent-resume), jobs teal (--accent-jobs), applications orange (--accent-applications), Fifth per-card accent (added with the scraper): Latest jobs pink (--accent-latest #be185d, --accent-latest-bg #fce7f3), a category cue only, like the other four.
            each with a light tint (-bg). Buttons and primary links elsewhere still use the single --color-accent.
            Other new tokens in tokens.css: --color-bg-tint (page background, replaces --color-bg on body, softly blue "light background"),
            --color-border-soft, --gradient-banner (profile card banner), --radius-lg (14px cards), --shadow-hover,
            and status badge pairs --status-{neutral,info,progress,success,warning,danger}(-bg).
            Rules: text/background pairs meet WCAG AA (4.5:1); status is always shown as text, never by color alone;
            hover lift is disabled under prefers-reduced-motion; layout is 1 column on phones, 2 columns from 768px, 3 columns from 1024px,
            with the applications card full width. Components: Avatar (initials circle, decorative) and StatusBadge (colored status pill).


Part five: visual refresh (approved addition, extends parts one, two and four).
            The dashboard looked too plain, so it gets a livelier look while staying accessible. Part one still holds (clean, consistent, works on phones and desktops), and buttons and links elsewhere keep the single --color-accent.
            New tokens in tokens.css: --gradient-hero (greeting banner), per-card bar gradients (--gradient-profile, -resume, -latest, -applications, -jobs), a teal accent for the mailbox card (--accent-jobs #0f766e, --accent-jobs-bg #ccfbf1), --shadow-soft and --color-gauge-track. The page background gets two soft radial tints. The new rules live in styles/refresh.css, loaded after components.css.
            Hero: the page opens with a gradient banner holding the h1 "Dashboard" as a small label, a greeting with the first name ("Good morning, Jane", or "Welcome back"), the Log out button, and four stat tiles (Applications, Replies, Latest jobs, Resumes) built from data the page already loads; a dash shows while a value is loading or failed. White text sits on dark gradient stops only, so it meets 4.5:1.
            Cards: a tinted top fading to white, a 5px gradient bar in the card accent, an icon beside each heading, and EmptyState (decorative icon plus a short message and optional action) for empty states. Icons are inline SVG from components/Icon.jsx, always aria-hidden, always next to visible text. No new dependencies.
            Layout: 1 column on phones, 2 from 768px, 3 from 1024px (profile, resume and mailbox in the first row, Latest jobs and Applications full width).
            List rows: Latest jobs and /jobs show each job as one row, not a card. The left side has the job title (an h3) with its category pill, then company, location and remote type, then source, "Posted N days ago" and the posting link. The right side (under the row on screens narrower than 900px) has compact buttons: Match with my resume, Tailor my resume, Suggestions, Interview questions, Track and View details. Results (match, suggestions, tailoring errors, description) open in one panel directly under the row. Expanded state is exposed with aria-expanded.
            Match score: a circular gauge with the score as text and a band label (Strong, Partial, Weak), so color is never the only cue.
            Suggestions: grouped cards with an icon and friendly title (Add a skill, Close match, Keywords to include, Headline, Location, Tips), a short hint, a priority word (High, Medium, Low) and a Copy button per suggestion that confirms with text. The data contract is unchanged.
            Motion: the gauge, hover and button transitions are turned off under prefers-reduced-motion.
