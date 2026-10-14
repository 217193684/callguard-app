# JobRadar

A personal Android job-search app — Indeed/PNet style — built around your own CVs.

- **Upload all your CVs at once** (PDF / DOCX). Skills and job titles are detected on the phone.
- **Search** Adzuna (SA + UK/NZ/AU/CA/NL/US), Careerjet, Remotive and Arbeitnow together, with advanced filters
  (date posted, job type, salary, distance, exclude words, remote only, minimum match %).
- **Search by CV**: each CV becomes its own search from its job titles; every result shows your match % and best CV.
- **PNet, Indeed, LinkedIn, CareerJunction, Careers24, Google Jobs, Seek** open inside the app (no scraping);
  tap **Check fit** on any job page there.
- **✨ Claude fit check** button per job — only runs when tapped, uses Haiku, sends the job + your 3 closest CVs
  as compact summaries, and caches the answer.
- **Live alerts**: background checks every 15 min – 6 h for each CV and saved search; notifies new jobs above your
  match threshold. No Claude tokens used.
- **My Jobs** tracker: Saved → Applied → Interview → Offer / Rejected, with notes.
- **Claude assistant** tab that can operate the app: search, check fit, track jobs, create alerts, edit CVs,
  change settings, open screens and sites.

## Keys (enter in the app's Settings)
- Anthropic API key — console.anthropic.com (billed separately from the Claude app subscription)
- Adzuna App ID + App Key — developer.adzuna.com (free)
- Careerjet API key — optional

## Build
Every push to `main` builds `JobRadar.apk` with GitHub Actions and publishes it under **Releases**.
