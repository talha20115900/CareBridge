import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { Activity, ArrowRight, CalendarDays, ClipboardList, FileText, LayoutDashboard,
  LogOut, ShieldCheck, Sparkles, Stethoscope, UserRound, UsersRound } from 'lucide-react';
import { api } from './api.js';
import './styles.css';

const clinicDay = () => new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Kolkata', year: 'numeric', month: '2-digit', day: '2-digit',
}).format(new Date());
const clinicNow = () => new Intl.DateTimeFormat('sv-SE', {
  timeZone: 'Asia/Kolkata', dateStyle: 'short', timeStyle: 'medium',
}).format(new Date()).replace(' ', 'T');
const dateLabel = value => new Intl.DateTimeFormat('en-IN', {
  dateStyle: 'medium', timeZone: 'UTC',
}).format(new Date(value.slice(0, 10) + 'T00:00:00Z'));
const visitLabel = value => dateLabel(value) + ' · ' + value.slice(11, 16) + ' IST';
const upcoming = visit => visit.status === 'BOOKED' && visit.startsAt > clinicNow();
const formValues = event => Object.fromEntries(new FormData(event.currentTarget));

function useAction() {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  async function run(action, message = '') {
    setError('');
    setSuccess('');
    setPending(true);
    try {
      await action();
      setSuccess(message);
    } catch (failure) {
      setError(failure.message);
    } finally {
      setPending(false);
    }
  }
  return { pending, error, success, run };
}

function Notice({ action }) {
  return <>
    {action.error && <p className="notice error" role="alert">{action.error}</p>}
    {action.success && <p className="notice good" role="status">{action.success}</p>}
  </>;
}

function Empty({ children }) {
  return <p className="empty">{children}</p>;
}

function Brand() {
  return <div className="brand"><span className="brand-icon"><Activity size={22} /></span>CareBridge</div>;
}

function Auth({ onLogin }) {
  const [mode, setMode] = useState('login');
  const [role, setRole] = useState('PATIENT');
  const action = useAction();
  function submit(event) {
    event.preventDefault();
    const values = formValues(event);
    action.run(async () => {
      if (mode === 'register') {
        await api('/auth/register', { method: 'POST', body: { ...values, role } });
      }
      const { email, password } = values;
      await api('/auth/login', { method: 'POST', body: new URLSearchParams({ email, password }) });
      onLogin(await api('/auth/me'));
    });
  }

  return <main className="auth-layout">
    <section className="auth-story">
      <Brand />
      <div>
        <h1>Your care, better connected.</h1>
        <p>Find a doctor, manage appointments, and keep your reports together in one workspace.</p>
        <ul className="auth-features">
          <li><CalendarDays /> Book a visit</li>
          <li><ShieldCheck /> Share selected reports</li>
          <li><Sparkles /> Prepare with AI assistance</li>
        </ul>
      </div>
      <small>VCube Hack Quest · Synthetic demo data only</small>
    </section>
    <section className="auth-area">
      <form className="auth-form" onSubmit={submit}>
        <h2>{mode === 'login' ? 'Welcome back' : 'Create an account'}</h2>
        <p className="muted">{mode === 'login' ? 'Sign in to your CareBridge workspace.' : 'Select your account type to get started.'}</p>
        <Notice action={action} />
        {mode === 'register' && <>
          <label>Account type<select value={role} onChange={event => setRole(event.target.value)}>
            <option value="PATIENT">Patient</option><option value="DOCTOR">Doctor</option>
          </select></label>
          <label>Full name<input name="name" required maxLength={100} autoComplete="name" /></label>
          {role === 'DOCTOR' && <>
            <label>Specialty<input name="specialty" required maxLength={100} placeholder="General Medicine" /></label>
            <label>Hospital<input name="hospital" required maxLength={120} /></label>
            <label>Doctor invitation code<input name="doctorCode" type="password" required maxLength={256} /></label>
          </>}
        </>}
        <label>Email<input name="email" type="email" required maxLength={190} autoComplete="username" /></label>
        <label>Password<input name="password" type="password" required minLength={mode === 'register' ? 10 : undefined}
          maxLength={72} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} /></label>
        <button className="primary" disabled={action.pending}>
          {action.pending ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Register and sign in'} <ArrowRight size={17} />
        </button>
        <p className="muted">{mode === 'login' ? 'New to CareBridge?' : 'Already registered?'}{' '}
          <button className="link" type="button" disabled={action.pending}
            onClick={() => setMode(mode === 'login' ? 'register' : 'login')}>
            {mode === 'login' ? 'Create an account' : 'Sign in'}
          </button>
        </p>
      </form>
    </section>
  </main>;
}

function Dashboard({ user, visits, reports, navigate }) {
  const patient = user.role === 'PATIENT';
  const future = visits.filter(upcoming).sort((a, b) => a.startsAt.localeCompare(b.startsAt));
  const next = future[0];
  return <>
    <section className="hero">
      <div><p className="hero-label">{patient ? 'Patient workspace' : 'Doctor workspace'}</p>
        <h2>{patient ? 'Plan your next visit' : 'Manage your clinic visits'}</h2>
        <p>{patient ? 'Book an appointment and organize the records you want to bring.' : 'View your schedule and open reports patients have shared with you.'}</p>
      </div>
      <button className="light" onClick={() => navigate(patient ? 'directory' : 'appointments')}>
        {patient ? 'Find a doctor' : 'View appointments'} <ArrowRight size={17} />
      </button>
    </section>
    <div className="metrics">
      <div className="metric"><CalendarDays /><strong>{future.length}</strong><span>Upcoming appointments</span></div>
      <div className="metric"><FileText /><strong>{reports.length}</strong><span>{patient ? 'Uploaded reports' : 'Shared reports'}</span></div>
      <div className="metric"><ClipboardList /><strong>{visits.filter(visit => visit.status === 'COMPLETED').length}</strong><span>Completed appointments</span></div>
    </div>
    <section className="panel">
      <div className="section-line"><h2>Next appointment</h2>
        <button className="link" onClick={() => navigate('appointments')}>View all</button>
      </div>
      {next ? <div className="row"><div>
        <strong>{patient ? next.doctorName : next.patientName}</strong>
        <p className="muted">{next.specialty} · {next.hospital}</p>
      </div><span>{visitLabel(next.startsAt)}</span></div>
        : <Empty>No upcoming appointments.</Empty>}
    </section>
  </>;
}

function Directory({ doctors, reload, navigate }) {
  const [search, setSearch] = useState('');
  const [doctor, setDoctor] = useState(null);
  const [day, setDay] = useState(clinicDay);
  const [slots, setSlots] = useState([]);
  const [slot, setSlot] = useState('');
  const [slotError, setSlotError] = useState('');
  const [loadingSlots, setLoadingSlots] = useState(false);
  const action = useAction();

  useEffect(() => {
    if (!doctor || !day) return;
    const controller = new AbortController();
    setLoadingSlots(true);
    setSlotError('');
    api('/doctors/' + doctor.id + '/slots?day=' + encodeURIComponent(day), { signal: controller.signal })
      .then(setSlots).catch(error => {
        if (!controller.signal.aborted) setSlotError(error.message);
      }).finally(() => {
        if (!controller.signal.aborted) setLoadingSlots(false);
      });
    return () => controller.abort();
  }, [doctor, day]);

  function resetSlot() {
    setSlot('');
    setSlots([]);
  }

  function book(event) {
    event.preventDefault();
    const { reason } = formValues(event);
    action.run(async () => {
      await api('/patient/appointments', { method: 'POST', body: { doctorId: doctor.id, startsAt: slot, reason } });
      await reload();
      navigate('appointments');
    });
  }

  const filteredDoctors = doctors.filter(item => [item.name, item.specialty, item.hospital].join(' ')
    .toLowerCase().includes(search.trim().toLowerCase()));

  return <>
    <label className="search">Search doctors<input value={search} onChange={event => setSearch(event.target.value)}
      placeholder="Search by name, specialty, or hospital" /></label>
    <div className="directory">
      {filteredDoctors.map(item => <article className="panel doctor-card" key={item.id}>
        <span className="icon-box"><Stethoscope size={22} /></span>
        <h2>{item.name}</h2><div className="specialty">{item.specialty}</div>
        <p className="muted">{item.hospital}</p>
        {item.bio && <p>{item.bio}</p>}
        <button className="secondary" onClick={() => {
          if (doctor?.id !== item.id) { resetSlot(); setDoctor(item); }
          requestAnimationFrame(() => document.getElementById('booking-form')?.scrollIntoView({ block: 'nearest' }));
        }}>Choose a slot</button>
      </article>)}
    </div>
    {!doctors.length && <Empty>No doctors have registered yet.</Empty>}
    {!!doctors.length && !filteredDoctors.length && <Empty>No doctors match this search.</Empty>}
    {doctor && <section className="panel" id="booking-form">
      <h2>Book with {doctor.name}</h2>
      <p className="muted">Appointments are 30 minutes. Times are in India Standard Time.</p>
      <form className="stack" onSubmit={book}>
        <label>Date<input type="date" min={clinicDay()} value={day} required
          onChange={event => { resetSlot(); setDay(event.target.value); }} /></label>
        <label>Available time<select required value={slot} onChange={event => setSlot(event.target.value)}>
          <option value="">{loadingSlots ? 'Loading…' : slots.length ? 'Select a time' : 'No available slots'}</option>
          {slots.map(time => <option key={time} value={time}>{time.slice(11, 16)} IST</option>)}
        </select></label>
        {slotError && <p className="notice error" role="alert">{slotError}</p>}
        <label>Reason for visit<textarea name="reason" required maxLength={400} rows={3} placeholder="Use synthetic information for this demo." /></label>
        <Notice action={action} />
        <button className="primary fit" disabled={!slot || action.pending}>Confirm appointment</button>
      </form>
    </section>}
  </>;
}

function Appointments({ user, visits, reload }) {
  const [filter, setFilter] = useState('All');
  const action = useAction();
  const doctor = user.role === 'DOCTOR';
  const statuses = ['All', 'Booked', 'Completed', 'Cancelled'];
  const filtered = visits.filter(visit => filter === 'All' || visit.status === filter.toUpperCase())
    .sort((a, b) => Number(upcoming(b)) - Number(upcoming(a)) ||
      (upcoming(a) ? a.startsAt.localeCompare(b.startsAt) : b.startsAt.localeCompare(a.startsAt)));

  function change(visit, status) {
    if (status === 'CANCELLED' && !window.confirm('Cancel this appointment and release the time slot?')) return;
    action.run(async () => {
      await api('/appointments/' + visit.id + '?status=' + status, { method: 'PATCH' });
      await reload();
    }, status === 'COMPLETED' ? 'Appointment completed.' : 'Appointment cancelled.');
  }

  return <section className="panel">
    <div className="pills" aria-label="Filter appointments">
      {statuses.map(status => <button type="button" key={status} aria-pressed={status === filter}
        onClick={() => setFilter(status)}>{status}</button>)}
    </div>
    <Notice action={action} />
    {filtered.length ? filtered.map(visit => <article className="row" key={visit.id}>
      <div>
        <h3>{doctor ? visit.patientName : visit.doctorName}</h3>
        <p className="muted">{visitLabel(visit.startsAt)} · {visit.hospital}</p>
        <p>{visit.reason}</p>
      </div>
      <div className="row-actions">
        <span className={'status ' + visit.status.toLowerCase()}>{visit.status.toLowerCase()}</span>
        {upcoming(visit) && <button className="text-danger" disabled={action.pending}
          onClick={() => change(visit, 'CANCELLED')}>Cancel</button>}
        {doctor && visit.status === 'BOOKED' && !upcoming(visit) && <button className="secondary"
          disabled={action.pending} onClick={() => change(visit, 'COMPLETED')}>Mark completed</button>}
      </div>
    </article>) : <Empty>No appointments in this view.</Empty>}
  </section>;
}

function History({ entries, reload }) {
  const [edit, setEdit] = useState(null);
  const action = useAction();

  function save(event) {
    event.preventDefault();
    const body = formValues(event);
    const form = event.currentTarget;
    action.run(async () => {
      await api('/patient/history' + (edit ? '/' + edit.id : ''), { method: edit ? 'PUT' : 'POST', body });
      form.reset();
      setEdit(null);
      await reload();
    }, edit ? 'Entry updated.' : 'Entry added to your history.');
  }

  function remove(item) {
    if (!window.confirm('Delete this history entry?')) return;
    action.run(async () => {
      await api('/patient/history/' + item.id, { method: 'DELETE' });
      if (edit?.id === item.id) setEdit(null);
      await reload();
    }, 'Entry deleted.');
  }

  return <div className="split">
    <section className="panel">
      <h2>{edit ? 'Edit history entry' : 'Add a history entry'}</h2>
      <p className="muted">Your timeline is visible in your patient account.</p>
      <form className="stack" key={edit?.id || 'new'} onSubmit={save}>
        <label>Date<input type="date" name="eventDate" max={clinicDay()} min="1900-01-01" required defaultValue={edit?.eventDate || clinicDay()} /></label>
        <label>Title<input name="title" required maxLength={120} defaultValue={edit?.title} /></label>
        <label>Details<textarea name="details" rows={4} required maxLength={2000} defaultValue={edit?.details} /></label>
        <Notice action={action} />
        <div className="row-actions"><button className="primary" disabled={action.pending}>Save entry</button>
          {edit && <button type="button" className="secondary" onClick={() => setEdit(null)}>Discard edits</button>}
        </div>
      </form>
    </section>
    <section className="panel"><h2>History timeline</h2>
      {entries.length ? entries.map(item => <article className="row" key={item.id}>
        <div><p className="muted">{dateLabel(item.eventDate)}</p><h3>{item.title}</h3><p className="preserve">{item.details}</p></div>
        <div className="row-actions"><button className="link" onClick={() => setEdit(item)}>Edit</button>
          <button className="text-danger" disabled={action.pending} onClick={() => remove(item)}>Delete</button></div>
      </article>) : <Empty>No history entries yet.</Empty>}
    </section>
  </div>;
}

function Reports({ user, reports, visits, doctors, reload, openSummary }) {
  const action = useAction();
  const [sharing, setSharing] = useState(null);
  const patient = user.role === 'PATIENT';
  const eligible = new Map(visits.filter(visit => visit.status !== 'CANCELLED').map(visit => [visit.doctorId, visit.doctorName]));
  const names = new Map(doctors.map(doctor => [doctor.id, doctor.name]));

  function upload(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const body = new FormData(form);
    action.run(async () => {
      await api('/patient/reports', { method: 'POST', body });
      form.reset();
      await reload();
    }, 'Report uploaded.');
  }

  function grant(event, id) {
    event.preventDefault();
    const { doctorId } = formValues(event);
    action.run(async () => {
      await api('/patient/reports/' + id + '/shares/' + doctorId, { method: 'PUT' });
      await reload();
    }, 'Report shared.');
  }

  function remove(id, doctorId) {
    if (!doctorId && !window.confirm('Delete this report and all of its sharing permissions?')) return;
    action.run(async () => {
      const suffix = doctorId ? '/shares/' + doctorId : '';
      await api('/patient/reports/' + id + suffix, { method: 'DELETE' });
      await reload();
    }, doctorId ? 'Access removed.' : 'Report deleted.');
  }

  return <>
    {patient && <section className="panel upload">
      <div><h2>Upload a report</h2><p className="muted">Selectable-text PDF or UTF-8 TXT, up to 3 MB. Use synthetic demo data.</p></div>
      <form className="row-actions" onSubmit={upload}><label>Report file
        <input type="file" name="file" accept=".txt,.pdf" required /></label>
        <button className="primary" disabled={action.pending}>Upload</button>
      </form>
    </section>}
    <Notice action={action} />
    <section className="panel">
      <h2>{patient ? 'My reports' : 'Reports shared with me'}</h2>
      {reports.length ? reports.map(report => <article className="report" key={report.id}>
        <div className="row">
          <div><h3>{report.filename}</h3><p className="muted">{visitLabel(report.uploadedAt)}{!patient && ' · ' + report.patient}</p></div>
          <div className="row-actions">
            <a className="secondary" href={'/api/reports/' + report.id + '/download'}>Download</a>
            {patient && <>
              <button className="secondary" onClick={() => openSummary(report.id)}>Summarize</button>
              <button className="link" aria-expanded={sharing === report.id}
                onClick={() => setSharing(sharing === report.id ? null : report.id)}>Sharing</button>
              <button className="text-danger" disabled={action.pending} onClick={() => remove(report.id)}>Delete</button>
            </>}
          </div>
        </div>
        {patient && sharing === report.id && <div className="share-box">
          <p className="muted">Shared doctors can download this report until you remove access.</p>
          {report.sharedWith.map(id => <div className="section-line" key={id}>
            <span>{names.get(id) || 'Doctor #' + id}</span>
            <button className="text-danger" disabled={action.pending} onClick={() => remove(report.id, id)}>Remove access</button>
          </div>)}
          <form className="row-actions" onSubmit={event => grant(event, report.id)}>
            <label>Share with a doctor<select name="doctorId" required defaultValue="">
              <option value="">Select an appointment doctor</option>
              {[...eligible.entries()].filter(([id]) => !report.sharedWith.includes(id))
                .map(([id, name]) => <option key={id} value={id}>{name}</option>)}
            </select></label>
            <button className="primary" disabled={action.pending}>Share report</button>
          </form>
        </div>}
      </article>) : <Empty>{patient ? 'No reports uploaded yet.' : 'No patients have shared reports with you.'}</Empty>}
    </section>
  </>;
}

const toolLabels = {
  INFORMATION: 'Healthcare information',
  TERMINOLOGY: 'Explain a medical term',
  SUMMARY: 'Summarize a report',
  PREPARATION: 'Prepare for a visit',
};

function AiPanel({ user, reports, visits, initialReport }) {
  const [feature, setFeature] = useState(initialReport ? 'SUMMARY' : 'INFORMATION');
  const [options, setOptions] = useState(null);
  const [result, setResult] = useState(null);
  const action = useAction();
  const patient = user.role === 'PATIENT';

  useEffect(() => {
    action.run(async () => setOptions(await api('/ai/options')));
  }, []);

  function ask(event) {
    event.preventDefault();
    const { topic, referenceId, consent, demoCode } = formValues(event);
    setResult(null);
    action.run(async () => {
      setResult(await api('/ai', { method: 'POST', body: {
        feature, topic, referenceId: referenceId ? Number(referenceId) : null,
        consent: consent === 'on', demoCode,
      } }));
    });
  }

  return <div className="split">
    <section className="panel stack">
      <div><h2>Choose an AI tool</h2><p className="muted">Use these tools to understand information and prepare questions for your clinician.</p></div>
      <Notice action={action} />
      {options && !options.enabled && <p className="notice" role="status">AI is disabled. Configure the server with a provider key to enable it.</p>}
      {options && <form className="stack" onSubmit={ask}>
        <label>Tool<select value={feature} disabled={action.pending}
          onChange={event => { setResult(null); setFeature(event.target.value); }}>
          {Object.entries(toolLabels).filter(([key]) => patient || ['INFORMATION', 'TERMINOLOGY'].includes(key))
            .map(([key, label]) => <option key={key} value={key}>{label}</option>)}
        </select></label>
        {(feature === 'INFORMATION' || feature === 'TERMINOLOGY') && <label key={feature}>
          {feature === 'INFORMATION' ? 'Topic' : 'Medical term'}
          <select name="topic" required>
            {(feature === 'INFORMATION' ? options.topics : options.terms).map(item => <option key={item}>{item}</option>)}
          </select>
        </label>}
        {feature === 'SUMMARY' && <>
          <label>Report<select name="referenceId" required defaultValue={initialReport || ''}>
            <option value="">Choose an uploaded report</option>
            {reports.map(report => <option key={report.id} value={report.id}>{report.filename}</option>)}
          </select></label>
          <label className="checkbox"><input type="checkbox" name="consent" required />
            I confirm that this is synthetic demo data and allow its text to be sent to the AI provider.
          </label>
        </>}
        {feature === 'PREPARATION' && <label>Upcoming appointment<select name="referenceId" required defaultValue="" key="visit">
          <option value="">Choose an appointment</option>
          {visits.filter(upcoming).map(visit => <option key={visit.id} value={visit.id}>
            {visit.doctorName} · {visitLabel(visit.startsAt)}
          </option>)}
        </select></label>}
        {options.requiresCode && <label>AI demo access code<input name="demoCode" type="password" required maxLength={256} /></label>}
        <button className="primary fit" disabled={action.pending || !options.enabled}>
          <Sparkles size={17} /> {action.pending ? 'Generating…' : 'Generate'}
        </button>
      </form>}
    </section>
    <section className="panel result" aria-live="polite">
      <h2>{result?.context || 'AI result'}</h2>
      {result ? <><ul>{result.items.map(item => <li key={item}>{item}</li>)}</ul><p className="muted">{result.note}</p></>
        : <Empty>Your result will appear here.</Empty>}
    </section>
  </div>;
}

function Profile({ profile, reload }) {
  const action = useAction();
  function save(event) {
    event.preventDefault();
    const body = formValues(event);
    action.run(async () => {
      await api('/doctor/profile', { method: 'PUT', body });
      await reload();
    }, 'Doctor profile updated.');
  }
  if (!profile) return <Empty>Loading profile…</Empty>;
  return <section className="panel profile">
    <h2>{profile.name}</h2>
    <p className="muted">Patients see these details in the doctor directory.</p>
    <form className="stack" onSubmit={save}>
      <label>Specialty<input name="specialty" required maxLength={100} defaultValue={profile.specialty} /></label>
      <label>Hospital<input name="hospital" required maxLength={120} defaultValue={profile.hospital} /></label>
      <label>About<textarea name="bio" maxLength={1000} rows={5} defaultValue={profile.bio} /></label>
      <Notice action={action} />
      <button className="primary fit" disabled={action.pending}>Save profile</button>
    </form>
  </section>;
}

function Workspace({ user, logout }) {
  const [page, setPage] = useState('dashboard');
  const [initialReport, setInitialReport] = useState(null);
  const [data, setData] = useState(null);
  const action = useAction();
  const patient = user.role === 'PATIENT';
  const pages = [
    ['dashboard', 'Overview', LayoutDashboard],
    ...(patient ? [['directory', 'Find a doctor', UsersRound]] : []),
    ['appointments', 'Appointments', CalendarDays],
    ...(patient ? [['history', 'Medical history', ClipboardList]] : []),
    ['reports', patient ? 'Medical reports' : 'Shared reports', FileText],
    ['ai', 'AI assistant', Sparkles],
    ...(!patient ? [['profile', 'Doctor profile', UserRound]] : []),
  ];

  async function reload() {
    const [visits, reports, doctors, history, profile] = await Promise.all([
      api('/appointments'), api('/reports'), patient ? api('/doctors') : [],
      patient ? api('/patient/history') : [], patient ? null : api('/doctor/profile'),
    ]);
    setData({ visits, reports, doctors, history, profile });
  }

  useEffect(() => { action.run(reload); }, []);

  function navigate(destination, report = null) {
    setInitialReport(report);
    setPage(destination);
    window.scrollTo(0, 0);
  }

  const pageTitle = pages.find(([key]) => key === page)[1];
  return <div className="workspace">
    <aside className="sidebar">
      <Brand />
      <nav aria-label="Workspace">
        {pages.map(([key, label, Icon]) => <button key={key} className={page === key ? 'active' : ''}
          aria-current={page === key ? 'page' : undefined} onClick={() => navigate(key)}>
          <Icon size={19} /><span>{label}</span>
        </button>)}
      </nav>
      <button className="signout" disabled={action.pending} onClick={() => action.run(logout)}><LogOut size={18} /> Sign out</button>
    </aside>
    <main className="workspace-main">
      <header className="topbar"><span>{patient ? 'Patient portal' : 'Doctor portal'}</span>
        <div className="account">
          <button className="link" disabled={action.pending} onClick={() => action.run(reload, 'Workspace refreshed.')}>Refresh</button>
          <span className="avatar">{user.name.slice(0, 1).toUpperCase()}</span>{user.name}
        </div>
      </header>
      <div className="content">
        <h1>{page === 'dashboard' ? 'Welcome, ' + user.name.split(' ')[0] : pageTitle}</h1>
        <Notice action={action} />
        {!data && !action.error && <Empty>Loading your workspace…</Empty>}
        {!data && action.error && <button className="secondary" onClick={() => action.run(reload)}>Reload workspace</button>}
        {data && page === 'dashboard' && <Dashboard user={user} {...data} navigate={navigate} />}
        {data && page === 'directory' && <Directory doctors={data.doctors} reload={reload} navigate={navigate} />}
        {data && page === 'appointments' && <Appointments user={user} visits={data.visits} reload={reload} />}
        {data && page === 'history' && <History entries={data.history} reload={reload} />}
        {data && page === 'reports' && <Reports user={user} {...data} reload={reload} openSummary={id => navigate('ai', id)} />}
        {data && page === 'ai' && <AiPanel key={initialReport || 'ai'} user={user} {...data} initialReport={initialReport} />}
        {data && page === 'profile' && <Profile profile={data.profile} reload={reload} />}
      </div>
    </main>
  </div>;
}

function App() {
  const [user, setUser] = useState(undefined);
  useEffect(() => { api('/auth/me').then(setUser).catch(() => setUser(null)); }, []);
  if (user === undefined) return <main className="loading"><Brand /><p>Opening your workspace…</p></main>;
  if (!user) return <Auth onLogin={setUser} />;
  return <Workspace user={user} logout={async () => {
    await api('/auth/logout', { method: 'POST' });
    setUser(null);
  }} />;
}

createRoot(document.getElementById('root')).render(<App />);
