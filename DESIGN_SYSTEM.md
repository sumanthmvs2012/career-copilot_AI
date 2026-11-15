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
