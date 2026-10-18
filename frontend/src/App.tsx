import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Activity, AlertCircle, ArrowDownRight, ArrowRight, ArrowUpRight, Bot, Check, CheckCircle2,
  ChevronDown, CircleDot, Clock3, Code2, Cpu, Database, FileCode2, FolderGit2, GitBranch,
  HardDrive, History, LoaderCircle, Plus, RefreshCw, ScanSearch, ShieldCheck, Sparkles,
  Terminal, Wrench, XCircle, Zap,
} from 'lucide-react';
import { api, type Diagnostic, type Health, type PatchPayload, type Repair, type Repository, type Scan } from './lib/api';

function timeAgo(value?: string | null) {
  if (!value) return 'Just now';
  const seconds = Math.max(0, Math.floor((Date.now() - new Date(value).getTime()) / 1000));
  if (seconds < 60) return `${seconds}s ago`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`;
  return `${Math.floor(seconds / 86400)}d ago`;
}
function statusClass(status?: string) { return `status status-${(status || 'unknown').toLowerCase().replaceAll('_', '-')}`; }
function getPatch(repair: Repair | null): PatchPayload | null {
  if (!repair?.patchJson) return null;
  try { return JSON.parse(repair.patchJson) as PatchPayload; } catch { return null; }
}

export default function App() {
  const [repositories, setRepositories] = useState<Repository[]>([]);
  const [scans, setScans] = useState<Scan[]>([]);
  const [health, setHealth] = useState<Health | null>(null);
  const [selectedRepo, setSelectedRepo] = useState<number | ''>('');
  const [activeScan, setActiveScan] = useState<number | null>(null);
  const [diagnostics, setDiagnostics] = useState<Diagnostic[]>([]);
  const [selectedDiagnostic, setSelectedDiagnostic] = useState<Diagnostic | null>(null);
  const [explanation, setExplanation] = useState<string>('');
  const [repair, setRepair] = useState<Repair | null>(null);
  const [runBuild, setRunBuild] = useState(false);
  const [runLint, setRunLint] = useState(false);
  const [repoPath, setRepoPath] = useState('');
  const [registerOpen, setRegisterOpen] = useState(false);
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [tab, setTab] = useState<'overview' | 'diagnostics' | 'repair'>('overview');

  const refresh = useCallback(async () => {
    try {
      const [repos, recent, h] = await Promise.all([api.repositories(), api.scans(), api.health()]);
      setRepositories(repos); setScans(recent); setHealth(h);
      setSelectedRepo(current => current || (repos[0]?.id ?? ''));
      if (activeScan) {
        const updated = await api.scan(activeScan);
        setScans(prev => [updated, ...prev.filter(s => s.id !== updated.id)]);
      }
    } catch (e) { setError(e instanceof Error ? e.message : 'Unable to connect to RepoMedic backend.'); }
  }, [activeScan]);

  useEffect(() => { void refresh(); const timer = window.setInterval(() => { void refresh(); }, 5000); return () => window.clearInterval(timer); }, [refresh]);
  useEffect(() => {
    if (!activeScan) { setDiagnostics([]); return; }
    let cancelled = false;
    const load = async () => { try { const [scan, rows] = await Promise.all([api.scan(activeScan), api.diagnostics(activeScan)]); if (!cancelled) { setDiagnostics(rows); setScans(prev => [scan, ...prev.filter(s => s.id !== scan.id)]); } } catch (e) { if (!cancelled) setError(e instanceof Error ? e.message : 'Could not load scan results.'); } };
    void load(); const timer = window.setInterval(() => void load(), 3500); return () => { cancelled = true; window.clearInterval(timer); };
  }, [activeScan]);

  const activeScanRow = scans.find(s => s.id === activeScan) ?? null;
  const errorCount = diagnostics.filter(d => d.severity === 'ERROR').length;
  const passedCount = scans.filter(s => s.status === 'COMPLETED' && !s.summary?.includes('failed')).length;
  const totalFindings = scans.reduce((n, s) => n + (s.diagnosticsCount || 0), 0);
  const patchPayload = useMemo(() => getPatch(repair), [repair]);

  async function registerRepository(event: React.FormEvent) {
    event.preventDefault(); setBusy('register'); setError(''); setNotice('');
    try { const repo = await api.registerRepository(repoPath.trim()); setRepositories(prev => [repo, ...prev]); setSelectedRepo(repo.id); setRepoPath(''); setRegisterOpen(false); setNotice(`Registered ${repo.name}`); await refresh(); }
    catch (e) { setError(e instanceof Error ? e.message : 'Repository registration failed.'); }
    finally { setBusy(''); }
  }
  async function startScan() {
    if (!selectedRepo) { setError('Register or select a repository first.'); return; }
    setBusy('scan'); setError(''); setNotice(''); setTab('diagnostics'); setRepair(null); setExplanation(''); setSelectedDiagnostic(null);
    try { const scan = await api.startScan(Number(selectedRepo), runBuild, runLint); setActiveScan(scan.id); setScans(prev => [scan, ...prev.filter(s => s.id !== scan.id)]); setNotice('Scan queued. Repository-controlled build/lint scripts run only when explicitly enabled.'); await refresh(); }
    catch (e) { setError(e instanceof Error ? e.message : 'Could not start scan.'); }
    finally { setBusy(''); }
  }
  async function explain(d: Diagnostic) {
    setSelectedDiagnostic(d); setExplanation(''); setRepair(null); setBusy(`explain-${d.id}`); setError(''); setTab('diagnostics');
    try { const result = await api.explain(d.id); setExplanation(`Model: ${result.model}\n\n${result.explanation}`); }
    catch (e) { setError(e instanceof Error ? e.message : 'AI diagnosis failed.'); }
    finally { setBusy(''); }
  }
  async function generateRepair(d: Diagnostic) {
    setSelectedDiagnostic(d); setBusy(`repair-${d.id}`); setError(''); setNotice('');
    try { const result = await api.generateRepair(d.id); setRepair(result); setTab('repair'); setNotice(result.status === 'NEEDS_INPUT' ? 'The model could not propose a safe patch from the available evidence.' : 'Repair proposal generated. Review every changed line before validation.'); }
    catch (e) { setError(e instanceof Error ? e.message : 'Repair generation failed.'); }
    finally { setBusy(''); }
  }
  async function validateRepair() {
    if (!repair) return; setBusy('validate'); setError(''); setNotice('Validation runs code from the repository in an isolated Git worktree. Only continue with repositories you trust.');
    try { const result = await api.validateRepair(repair.id); setRepair(result); setNotice(`Validation finished: ${result.status}`); await refresh(); }
    catch (e) { setError(e instanceof Error ? e.message : 'Validation failed.'); }
    finally { setBusy(''); }
  }
  async function applyRepair() {
    if (!repair || !window.confirm('Apply this validated patch to the selected repository working tree? RepoMedic will check that HEAD has not changed and the working tree is clean.')) return;
    setBusy('apply'); setError(''); setNotice('');
    try { const result = await api.applyRepair(repair.id); setRepair(result); setNotice('Patch applied to the repository working tree. Review the Git diff before committing.'); }
    catch (e) { setError(e instanceof Error ? e.message : 'Patch application failed.'); }
    finally { setBusy(''); }
  }

  return <div className="app-shell">
    <aside className="sidebar">
      <div className="brand"><div className="brand-mark"><Activity size={22}/></div><div><div className="brand-name">repo<span>medic</span></div><div className="brand-sub">LOCAL REPAIR SYSTEM</div></div></div>
      <div className="workspace-label">WORKSPACE</div>
      <button className={`nav-item ${tab === 'overview' ? 'active' : ''}`} onClick={() => setTab('overview')}><HardDrive size={17}/> Overview <span className="nav-count">01</span></button>
      <button className={`nav-item ${tab === 'diagnostics' ? 'active' : ''}`} onClick={() => setTab('diagnostics')}><ScanSearch size={17}/> Diagnostics <span className="nav-count">{diagnostics.length.toString().padStart(2, '0')}</span></button>
      <button className={`nav-item ${tab === 'repair' ? 'active' : ''}`} onClick={() => setTab('repair')}><Wrench size={17}/> Repair lab <span className="nav-dot"/></button>
      <div className="sidebar-divider"/>
      <div className="workspace-label">YOUR REPOSITORIES <button className="icon-button small" onClick={() => setRegisterOpen(true)} title="Add repository"><Plus size={15}/></button></div>
      <div className="repo-list">{repositories.map(repo => <button key={repo.id} className={`repo-item ${selectedRepo === repo.id ? 'selected' : ''}`} onClick={() => { setSelectedRepo(repo.id); setActiveScan(null); setDiagnostics([]); setSelectedDiagnostic(null); setRepair(null); }}><span className="repo-glyph"><FolderGit2 size={16}/></span><span className="repo-text"><strong>{repo.name}</strong><small>{repo.projectType.replaceAll('_', ' ')}</small></span><span className="repo-indicator"/></button>)}{repositories.length === 0 && <div className="empty-repos">No repositories yet.<br/>Add a local Git project to begin.</div>}</div>
      <div className="sidebar-bottom"><div className="privacy-card"><div className="privacy-icon"><ShieldCheck size={17}/></div><div><strong>Local-first by design</strong><p>Source context is sent to your local Ollama runtime, not a hosted AI API.</p></div></div><div className="profile-row"><div className="profile-avatar">RM</div><div className="profile-meta"><strong>Local workspace</strong><span>Single-user mode</span></div><span className="online-dot"/></div></div>
    </aside>

    <main className="main-area">
      <header className="topbar"><div className="breadcrumb"><span>Workspace</span><ArrowRight size={13}/><strong>{tab === 'overview' ? 'Overview' : tab === 'diagnostics' ? 'Diagnostics' : 'Repair lab'}</strong></div><div className="topbar-right"><div className={`connection-pill ${health?.ollamaReachable ? 'connected' : 'disconnected'}`}><span className="pulse-dot"/>{health?.ollamaReachable ? 'Ollama connected' : 'Ollama offline'}</div><button className="icon-button" title="Refresh status" onClick={() => void refresh()}><RefreshCw size={16}/></button><div className="model-pill"><Cpu size={15}/><span>{health?.model || 'Qwen 3.5 9B'}</span><ChevronDown size={13}/></div></div></header>
      <div className="page-content">
        <section className="welcome-row"><div><div className="eyebrow"><span className="eyebrow-line"/> LOCAL AI · VERIFIED REPAIRS</div><h1>Debug with <span>evidence.</span></h1><p className="page-subtitle">Diagnose repository failures, generate focused patches, and validate the result before it touches your working tree.</p></div><div className="welcome-actions"><button className="button secondary" onClick={() => setRegisterOpen(true)}><Plus size={16}/> Add repository</button><button className="button primary" onClick={() => void startScan()} disabled={!selectedRepo || busy === 'scan'}>{busy === 'scan' ? <LoaderCircle className="spin" size={16}/> : <ScanSearch size={16}/>} Analyze repository</button></div></section>
        {(error || notice) && <div className={`toast ${error ? 'toast-error' : 'toast-success'}`}><span>{error ? <AlertCircle size={17}/> : <CheckCircle2 size={17}/>}</span><p>{error || notice}</p><button className="icon-button small" onClick={() => { setError(''); setNotice(''); }}><XCircle size={15}/></button></div>}
        <section className="system-strip"><div className="system-item"><span className={`system-icon ${health?.backend ? 'ok' : 'bad'}`}><Activity size={17}/></span><div><span>API SERVICE</span><strong>{health?.backend ? 'Operational' : 'Checking'}</strong></div><i className={health?.backend ? 'led good' : 'led'}/></div><div className="system-item"><span className={`system-icon ${health?.database ? 'ok' : 'bad'}`}><Database size={17}/></span><div><span>MYSQL DATABASE</span><strong>{health?.database ? 'Connected' : 'Unavailable'}</strong></div><i className={health?.database ? 'led good' : 'led'}/></div><div className="system-item"><span className={`system-icon ${health?.ollamaReachable ? 'ok' : 'bad'}`}><Bot size={17}/></span><div><span>LOCAL INFERENCE</span><strong>{health?.ollamaReachable ? health.model : 'Not reachable'}</strong></div><i className={health?.ollamaReachable ? 'led good' : 'led'}/></div><div className="system-item"><span className="system-icon neutral"><GitBranch size={17}/></span><div><span>REPAIR MODE</span><strong>Isolated worktree</strong></div><span className="mini-tag">SAFE FLOW</span></div></section>

        {tab === 'overview' && <>
          <section className="metrics-grid"><Metric icon={<FolderGit2 size={18}/>} label="Repositories" value={repositories.length.toString().padStart(2, '0')} trend="Registered locally" tone="blue"/><Metric icon={<ScanSearch size={18}/>} label="Scans completed" value={scans.filter(s => ['COMPLETED','FAILED'].includes(s.status)).length.toString().padStart(2, '0')} trend="Recent scan history" tone="violet"/><Metric icon={<AlertCircle size={18}/>} label="Findings recorded" value={totalFindings.toString().padStart(2, '0')} trend="Across recent scans" tone="amber"/><Metric icon={<CheckCircle2 size={18}/>} label="Completed scans" value={passedCount.toString().padStart(2, '0')} trend="Scan finished, not a bug-free guarantee" tone="green"/></section>
          <section className="content-grid"><div className="panel scan-panel"><div className="panel-header"><div><div className="panel-kicker">NEW ANALYSIS</div><h2>Run a repository scan</h2><p>Choose checks explicitly. Build and lint scripts execute code from the selected repository.</p></div><div className="panel-icon"><ScanSearch size={19}/></div></div><label className="field-label">TARGET REPOSITORY</label><div className="select-wrap"><FolderGit2 size={16}/><select value={selectedRepo} onChange={e => setSelectedRepo(e.target.value ? Number(e.target.value) : '')}><option value="">Select a repository</option>{repositories.map(r => <option key={r.id} value={r.id}>{r.name} — {r.localPath}</option>)}</select><ChevronDown size={15}/></div><div className="check-options"><label className="check-option"><input type="checkbox" checked={runBuild} onChange={e => setRunBuild(e.target.checked)}/><span className="custom-check"><Check size={12}/></span><span><strong>Run build script</strong><small>Executes npm run build</small></span><span className="opt-badge">CODE EXECUTION</span></label><label className="check-option"><input type="checkbox" checked={runLint} onChange={e => setRunLint(e.target.checked)}/><span className="custom-check"><Check size={12}/></span><span><strong>Run lint script</strong><small>Executes npm run lint</small></span><span className="opt-badge">CODE EXECUTION</span></label></div><div className="scan-note"><ShieldCheck size={16}/><span>Unchecked scripts are not executed. Only scan repositories you trust.</span></div><button className="button primary full-width" onClick={() => void startScan()} disabled={!selectedRepo || busy === 'scan'}>{busy === 'scan' ? <LoaderCircle className="spin" size={16}/> : <Zap size={16}/>} Start analysis <ArrowRight size={15}/></button></div>
            <div className="panel recent-panel"><div className="panel-header"><div><div className="panel-kicker">ACTIVITY</div><h2>Recent scans</h2><p>Latest repository analysis jobs.</p></div><button className="text-button" onClick={() => setTab('diagnostics')}>View results <ArrowUpRight size={14}/></button></div><div className="recent-list">{scans.slice(0, 6).map(scan => <button className="recent-row" key={scan.id} onClick={() => { setActiveScan(scan.id); setTab('diagnostics'); setRepair(null); }}><span className={`recent-icon ${scan.status === 'COMPLETED' ? 'done' : scan.status === 'FAILED' ? 'failed' : 'running'}`}>{scan.status === 'COMPLETED' ? <CheckCircle2 size={16}/> : scan.status === 'FAILED' ? <AlertCircle size={16}/> : <LoaderCircle size={16}/>}</span><span className="recent-main"><strong>{scan.repositoryName}</strong><small>{scan.summary || `Scan #${scan.id} · ${scan.runBuild ? 'build' : 'manifest'}${scan.runLint ? ' + lint' : ''}`}</small></span><span className={statusClass(scan.status)}>{scan.status}</span><span className="recent-time">{timeAgo(scan.createdAt)}</span><ArrowUpRight className="row-arrow" size={14}/></button>)}{scans.length === 0 && <EmptyState icon={<History size={20}/>} title="No scan history" text="Run your first analysis to see activity here."/>}</div></div></section>
          <section className="bottom-grid"><div className="panel pipeline-panel"><div className="panel-header"><div><div className="panel-kicker">REPAIR PIPELINE</div><h2>Evidence before edits</h2></div><span className="mini-tag">4 STAGES</span></div><div className="pipeline-steps"><PipelineStep number="01" icon={<Terminal size={17}/>} title="Detect" text="Compiler, lint, and build diagnostics"/><div className="pipeline-connector"/><PipelineStep number="02" icon={<Sparkles size={17}/>} title="Diagnose" text="Qwen explains the likely root cause"/><div className="pipeline-connector"/><PipelineStep number="03" icon={<Code2 size={17}/>} title="Propose" text="Generate a constrained patch"/><div className="pipeline-connector"/><PipelineStep number="04" icon={<ShieldCheck size={17}/>} title="Verify" text="Isolated validation, then approval"/></div></div><div className="panel model-panel"><div className="model-art"><div className="orbit orbit-one"/><div className="orbit orbit-two"/><div className="model-core"><Bot size={28}/></div><span className="orbit-node node-one"/><span className="orbit-node node-two"/><span className="orbit-node node-three"/></div><div className="panel-kicker">LOCAL MODEL RUNTIME</div><h2>Qwen-powered diagnosis</h2><p>AI proposes. Deterministic checks verify. Your source stays on your machine unless you configure external services yourself.</p><div className="model-foot"><span><span className={`led ${health?.ollamaReachable ? 'good' : ''}`}/>{health?.ollamaReachable ? 'Runtime reachable' : 'Waiting for Ollama'}</span><span className="model-version">{health?.model || 'qwen3.5:9b'}</span></div></div></section>
        </>}

        {tab === 'diagnostics' && <section className="panel results-panel"><div className="panel-header"><div><div className="panel-kicker">SCAN INSPECTOR</div><h2>{activeScanRow ? `${activeScanRow.repositoryName} / Scan #${activeScanRow.id}` : 'Select a scan'}</h2><p>{activeScanRow?.summary || 'Choose a scan from Recent scans or run a new analysis.'}</p></div>{activeScanRow && <span className={statusClass(activeScanRow.status)}>{activeScanRow.status}</span>}</div>{activeScanRow && <div className="scan-meta"><span><Clock3 size={14}/> Started {timeAgo(activeScanRow.startedAt)}</span><span><AlertCircle size={14}/> {diagnostics.length} findings</span><span><CheckCircle2 size={14}/> {activeScanRow.runBuild ? 'Build check enabled' : 'Build check skipped'}</span><span><Terminal size={14}/> {activeScanRow.runLint ? 'Lint check enabled' : 'Lint check skipped'}</span></div>}
          <div className="diagnostic-list">{diagnostics.map(d => <article className={`diagnostic-card ${selectedDiagnostic?.id === d.id ? 'selected' : ''}`} key={d.id}><div className={`diagnostic-severity ${d.severity.toLowerCase()}`}>{d.severity === 'INFO' ? <CheckCircle2 size={16}/> : <AlertCircle size={16}/>}</div><div className="diagnostic-body"><div className="diagnostic-title-row"><span className="diagnostic-type">{d.errorType.replaceAll('_', ' ')}</span>{d.filePath && <span className="file-location"><FileCode2 size={13}/>{d.filePath}{d.lineNumber ? `:${d.lineNumber}` : ''}</span>}</div><p>{d.message}</p>{d.evidence && <details className="evidence"><summary><Terminal size={13}/> Diagnostic output</summary><pre>{d.evidence}</pre></details>}<div className="diagnostic-actions"><button className="button mini secondary" onClick={() => void explain(d)} disabled={busy === `explain-${d.id}`}>{busy === `explain-${d.id}` ? <LoaderCircle className="spin" size={14}/> : <Sparkles size={14}/>} Explain with Qwen</button><button className="button mini primary" onClick={() => void generateRepair(d)} disabled={busy === `repair-${d.id}` || d.severity === 'INFO'}>{busy === `repair-${d.id}` ? <LoaderCircle className="spin" size={14}/> : <Wrench size={14}/>} Propose repair</button></div>{selectedDiagnostic?.id === d.id && explanation && <div className="explanation-box"><div className="explanation-heading"><Bot size={15}/> AI diagnosis <span>QWEN LOCAL</span></div><pre>{explanation}</pre></div>}</div></article>)}{activeScanRow && ['QUEUED','RUNNING'].includes(activeScanRow.status) && <div className="working-state"><LoaderCircle className="spin" size={20}/><strong>Analysis in progress</strong><span>RepoMedic is collecting diagnostics. This view refreshes automatically.</span></div>}{activeScanRow && ['COMPLETED','FAILED'].includes(activeScanRow.status) && diagnostics.length === 0 && <EmptyState icon={<CheckCircle2 size={20}/>} title="No diagnostic records" text="The scan completed without persisted findings."/>}{!activeScanRow && <EmptyState icon={<ScanSearch size={20}/>} title="Nothing selected" text="Open a recent scan from Overview or start a new one."/>}</div></section>}

        {tab === 'repair' && <section className="panel repair-panel"><div className="panel-header"><div><div className="panel-kicker">PATCH REVIEW</div><h2>Repair lab</h2><p>Every proposed file is untrusted until reviewed and validated.</p></div>{repair && <span className={statusClass(repair.status)}>{repair.status}</span>}</div>{!repair ? <EmptyState icon={<Wrench size={20}/>} title="No repair proposal yet" text="Open Diagnostics and select Propose repair on a finding."/> : <><div className="repair-summary"><div className="repair-summary-icon"><Sparkles size={19}/></div><div><div className="panel-kicker">QWEN PROPOSAL · {repair.modelName}</div><h3>{repair.explanation || 'Suggested code changes'}</h3><p>Generated from the diagnostic evidence. Review all files and reasons before running validation.</p></div></div>{patchPayload?.cautions && <div className="scan-note warning"><AlertCircle size={16}/><span>{patchPayload.cautions}</span></div>}<div className="patch-toolbar"><div><strong>{patchPayload?.files?.length ?? 0} file(s) proposed</strong><span> · Base commit {patchPayload?.baseCommit?.slice(0, 9) || 'unknown'}</span></div><span className="mini-tag">FULL-FILE REPLACEMENTS</span></div>{patchPayload?.files?.map((file, index) => <PatchPreview file={file} key={`${file.path}-${index}`}/>)}{(!patchPayload?.files || patchPayload.files.length === 0) && <div className="empty-patch">No safe patch was generated. Refine the diagnostic context and try again.</div>}{repair.validationSummary && <div className={`validation-summary ${repair.status === 'VALIDATED' || repair.status === 'APPLIED' ? 'success' : 'failure'}`}><div className="validation-heading"><ShieldCheck size={17}/> Validation report</div><pre>{repair.validationSummary}</pre></div>}<div className="repair-footer"><div className="repair-safety"><ShieldCheck size={15}/><span>Validation runs in an isolated worktree. Applying requires a clean original working tree.</span></div><div className="repair-buttons"><button className="button secondary" onClick={() => void validateRepair()} disabled={busy === 'validate' || !patchPayload?.files?.length || repair.status === 'APPLIED'}>{busy === 'validate' ? <LoaderCircle className="spin" size={15}/> : <RefreshCw size={15}/>} Validate patch</button><button className="button primary" onClick={() => void applyRepair()} disabled={busy === 'apply' || repair.status !== 'VALIDATED'}>{busy === 'apply' ? <LoaderCircle className="spin" size={15}/> : <Check size={15}/>} Apply validated patch</button></div></div></>}</section>}
        <footer className="footer"><span>REPOMEDIC <span className="footer-sep">/</span> LOCAL-FIRST DEVELOPER TOOL</span><span>AI suggestions are untrusted until verified by tests.</span><span className="footer-build"><CircleDot size={12}/> MVP BUILD 1.0</span></footer>
      </div>
    </main>

    {registerOpen && <div className="modal-backdrop" onMouseDown={e => { if (e.target === e.currentTarget) setRegisterOpen(false); }}><form className="modal" onSubmit={registerRepository}><div className="modal-icon"><FolderGit2 size={21}/></div><button type="button" className="icon-button modal-close" onClick={() => setRegisterOpen(false)}><XCircle size={17}/></button><div className="panel-kicker">NEW WORKSPACE TARGET</div><h2>Register repository</h2><p>Enter an absolute local path to a Git repository containing a package.json file. The backend must be able to access this path.</p><label className="field-label" htmlFor="repo-path">ABSOLUTE REPOSITORY PATH</label><input id="repo-path" className="text-input" autoFocus placeholder={navigator.platform.toLowerCase().includes('win') ? 'C:\Users\you\projects\my-app' : '/home/you/projects/my-app'} value={repoPath} onChange={e => setRepoPath(e.target.value)} required/><div className="modal-note"><ShieldCheck size={15}/> Path is processed by your local backend. Do not register repositories you do not trust.</div><div className="modal-actions"><button type="button" className="button secondary" onClick={() => setRegisterOpen(false)}>Cancel</button><button type="submit" className="button primary" disabled={busy === 'register' || !repoPath.trim()}>{busy === 'register' ? <LoaderCircle className="spin" size={15}/> : <Plus size={15}/>} Register repository</button></div></form></div>}
  </div>;
}

function Metric({ icon, label, value, trend, tone }: { icon: React.ReactNode; label: string; value: string; trend: string; tone: string }) {
  return <div className="metric-card"><div className={`metric-icon ${tone}`}>{icon}</div><div className="metric-label">{label}</div><div className="metric-value">{value}</div><div className="metric-foot"><span className="metric-line"/>{trend}</div><span className={`metric-corner ${tone}`}><ArrowDownRight size={18}/></span></div>;
}
function PipelineStep({ number, icon, title, text }: { number: string; icon: React.ReactNode; title: string; text: string }) {
  return <div className="pipeline-step"><span className="pipeline-number">{number}</span><span className="pipeline-icon">{icon}</span><strong>{title}</strong><p>{text}</p></div>;
}
function EmptyState({ icon, title, text }: { icon: React.ReactNode; title: string; text: string }) {
  return <div className="empty-state"><div className="empty-icon">{icon}</div><strong>{title}</strong><p>{text}</p></div>;
}
function PatchPreview({ file }: { file: { path: string; content: string; reason: string } }) {
  const lines = file.content.split('\n');
  return <div className="patch-file"><div className="patch-file-head"><div><FileCode2 size={15}/><strong>{file.path}</strong></div><span className="patch-language">PROPOSED CONTENT</span></div><p className="patch-reason">{file.reason}</p><pre className="code-preview">{lines.slice(0, 120).map((line, i) => <span className="code-line" key={i}><span className="line-number">{i + 1}</span><span className="line-plus">+</span>{line || ' '}</span>)}{lines.length > 120 && <span className="code-truncated">… {lines.length - 120} more lines not shown in preview</span>}</pre>{lines.length > 120 && <div className="patch-footnote">Preview truncated for readability. The stored proposal contains the full file.</div>}</div>;
}
