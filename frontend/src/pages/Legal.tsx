import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { Wordmark } from "../components/layout/AppLayout";

const UPDATED = "October 4, 2026";
// Set VITE_CONTACT_EMAIL at build time (on Render: an environment variable).
const CONTACT: string | undefined = import.meta.env.VITE_CONTACT_EMAIL || undefined;

function Contact() {
  return CONTACT ? (
    <a className="font-semibold text-ember-600 underline" href={`mailto:${CONTACT}`}>{CONTACT}</a>
  ) : (
    <>the support email shown on Pacebook's Google sign-in screen</>
  );
}

function LegalPage({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="min-h-dvh bg-paper">
      <header className="border-b border-line">
        <div className="mx-auto flex h-16 max-w-3xl items-center justify-between px-4 sm:px-6">
          <Link to="/" aria-label="Pacebook home">
            <Wordmark />
          </Link>
          <nav className="flex gap-4 text-sm font-semibold text-ink-soft">
            <Link to="/privacy" className="hover:text-ink">Privacy</Link>
            <Link to="/terms" className="hover:text-ink">Terms</Link>
          </nav>
        </div>
      </header>
      <main className="mx-auto max-w-3xl px-4 py-10 sm:px-6">
        <h1 className="font-display text-5xl font-extrabold tracking-tight">{title}</h1>
        <p className="mt-2 text-sm text-muted">Last updated {UPDATED}</p>
        <div className="mt-8 space-y-6 leading-relaxed text-ink-soft [&_h2]:mb-2 [&_h2]:font-display [&_h2]:text-2xl [&_h2]:font-bold [&_h2]:text-ink [&_li]:ml-5 [&_li]:list-disc">
          {children}
        </div>
      </main>
    </div>
  );
}

export function Privacy() {
  return (
    <LegalPage title="Privacy Policy">
      <p>
        Pacebook is a personal running log and training diary. This policy explains what information Pacebook
        collects, how it is used, and the choices you have.
      </p>
      <section>
        <h2>Information we collect</h2>
        <ul>
          <li>
            <strong>Google account basics.</strong> When you sign in with Google we receive your name, email
            address, profile picture and a Google account identifier. We request only the <code>openid</code>,{" "}
            <code>email</code> and <code>profile</code> scopes and never see your Google password.
          </li>
          <li>
            <strong>Training data you enter.</strong> Training blocks, planned workouts, logged activities
            (date, type, distance, pace, average heart rate, comments) and reasons for skipped workouts.
          </li>
          <li>
            <strong>Session cookie.</strong> A single secure, HttpOnly cookie keeps you signed in. We do not use
            advertising or tracking cookies or third-party analytics.
          </li>
        </ul>
      </section>
      <section>
        <h2>How we use it</h2>
        <p>
          Only to provide the service to you: showing your plan, your activities and your statistics. Your data
          is private to your account. We do not sell, rent or share it, and we do not use it for advertising.
        </p>
      </section>
      <section>
        <h2>Google user data</h2>
        <p>
          Pacebook's use of information received from Google APIs adheres to the Google API Services User Data
          Policy, including the Limited Use requirements. Google account data is used solely to sign you in and
          display your name and picture.
        </p>
      </section>
      <section>
        <h2>Storage and security</h2>
        <p>
          Data is stored in a PostgreSQL database hosted by our infrastructure providers (Render for the
          application, Neon for the database) and is transmitted over HTTPS. Session tokens are stored only in
          hashed form.
        </p>
      </section>
      <section>
        <h2>Retention and deletion</h2>
        <p>
          Your data is kept while your account exists. You can delete individual activities and training blocks
          at any time in the app. To delete your account and all associated data, email{" "}
          <Contact /> and it
          will be removed within 30 days. You can also revoke Pacebook's access at any time from your Google
          Account's security settings.
        </p>
      </section>
      <section>
        <h2>Contact</h2>
        <p>
          Questions about this policy: <Contact />.
        </p>
      </section>
    </LegalPage>
  );
}

export function Terms() {
  return (
    <LegalPage title="Terms of Service">
      <p>By using Pacebook you agree to these terms.</p>
      <section>
        <h2>The service</h2>
        <p>
          Pacebook lets you plan training and log your running activities. It is provided free of charge, "as
          is", without warranties of any kind, and may change or be discontinued at any time.
        </p>
      </section>
      <section>
        <h2>Not medical advice</h2>
        <p>
          Pacebook is a record-keeping tool. Nothing in it is medical, health or coaching advice. Consult a
          qualified professional before starting or changing a training programme.
        </p>
      </section>
      <section>
        <h2>Your account and content</h2>
        <p>
          You are responsible for activity on your account. The training data you enter is yours. You may stop
          using Pacebook and request deletion of your data at any time (see the{" "}
          <Link className="font-semibold text-ember-600 underline" to="/privacy">Privacy Policy</Link>).
        </p>
      </section>
      <section>
        <h2>Acceptable use</h2>
        <p>Do not misuse the service, attempt to access other users' data, or interfere with its operation.</p>
      </section>
      <section>
        <h2>Liability</h2>
        <p>
          To the extent permitted by law, Pacebook is not liable for any loss of data or any indirect or
          consequential damages arising from use of the service.
        </p>
      </section>
      <section>
        <h2>Contact</h2>
        <p>
          <Contact />
        </p>
      </section>
    </LegalPage>
  );
}
