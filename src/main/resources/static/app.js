/* =========================================================================
 * Task Tracker frontend
 * - Axios for API calls
 * - DB-backed auth: every user logs in with their own credentials (HTTP Basic).
 *   The whole API requires login; each user sees only their own projects/tasks.
 *   'admin' is special (ROLE_ADMIN): sees everything and can register users.
 * - Styling is Bootstrap-only; no custom CSS
 * ========================================================================= */

const api = axios.create({ baseURL: '/api' });
const AUTH_KEY = 'tt_auth';   // sessionStorage: base64("user:pass")

// Identity of the signed-in user: { username, admin } from GET /api/me. Null when logged out.
let currentUser = null;

/* ----------------------------- Auth helpers ----------------------------- */

// Attach Basic credentials to every request when logged in.
api.interceptors.request.use(config => {
    const token = sessionStorage.getItem(AUTH_KEY);
    if (token) config.headers.Authorization = 'Basic ' + token;
    return config;
});

function isLoggedIn() { return !!sessionStorage.getItem(AUTH_KEY); }
function isAdmin() { return !!(currentUser && currentUser.admin); }

// Verify credentials and learn the user's identity/role via GET /api/me.
async function login(username, password) {
    const token = btoa(`${username}:${password}`);
    let me;
    try {
        const { data } = await axios.get('/api/me', { headers: { Authorization: 'Basic ' + token } });
        me = data;
    } catch (e) {
        throw new Error('Invalid username or password.');
    }
    sessionStorage.setItem(AUTH_KEY, token);
    currentUser = me;
    refreshAuthUI();
}

// On page load, restore identity from a stored token (if still valid).
async function restoreSession() {
    if (!isLoggedIn()) return;
    try {
        const { data } = await api.get('/me');
        currentUser = data;
    } catch (e) {
        sessionStorage.removeItem(AUTH_KEY);
        currentUser = null;
    }
}

function logout() {
    sessionStorage.removeItem(AUTH_KEY);
    currentUser = null;
    refreshAuthUI();
    showView('auth');
}

function refreshAuthUI() {
    const status = document.getElementById('authStatus');
    const logoutBtn = document.getElementById('logoutBtn');
    if (isLoggedIn() && currentUser) {
        status.textContent = `Signed in as ${currentUser.username}${currentUser.admin ? ' (admin)' : ''}`;
        logoutBtn.classList.remove('d-none');
    } else {
        status.textContent = 'Not signed in';
        logoutBtn.classList.add('d-none');
    }
    // Admin-only controls: register-user button + project owner filter.
    document.getElementById('registerUserBtn').classList.toggle('d-none', !isAdmin());
    document.getElementById('ownerFilterCol').classList.toggle('d-none', !isAdmin());
}

/* ----------------------------- UI helpers ------------------------------- */

function alertMsg(message, type = 'danger') {
    const area = document.getElementById('alertArea');
    const el = document.createElement('div');
    el.className = `alert alert-${type} alert-dismissible fade show`;
    el.innerHTML = `${escapeHtml(message)}<button type="button" class="btn-close" data-bs-dismiss="alert"></button>`;
    area.appendChild(el);
    setTimeout(() => bootstrap.Alert.getOrCreateInstance(el).close(), 4000);
}

function escapeHtml(s) {
    return String(s ?? '').replace(/[&<>"']/g, c =>
        ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

// Turn an axios error into a readable message (uses backend's {message}/{error}).
function apiError(e) {
    if (e.response) {
        if (e.response.status === 401) return 'Not authorized — please log in.';
        if (e.response.status === 403) return "You don't have permission to do that.";
        const d = e.response.data || {};
        return d.message || d.error || `Request failed (${e.response.status}).`;
    }
    return e.message || 'Network error.';
}

function showView(name) {
    // Every view except the login form requires authentication.
    if (name !== 'auth' && !isLoggedIn()) name = 'auth';
    ['auth', 'projects', 'tasks', 'overdue', 'users'].forEach(v =>
        document.getElementById(`view-${v}`).classList.toggle('d-none', v !== name));
    document.querySelectorAll('#mainNav .nav-link').forEach(a =>
        a.classList.toggle('active', a.dataset.view === name));
    if (name === 'projects') loadProjects();
    if (name === 'tasks') loadTaskView();
    if (name === 'overdue') loadOverdueTasks();
    if (name === 'users') loadUsersView();
}

// Require login for a mutating action; returns true if allowed.
function requireLogin() {
    if (isLoggedIn()) return true;
    alertMsg('Please log in to perform this action.', 'warning');
    showView('auth');
    return false;
}

// Bootstrap-style validation gate. Returns true if the form is valid.
function validateForm(form) {
    form.classList.add('was-validated');
    return form.checkValidity();
}

/* ------------------------------- Users ---------------------------------- */

const registerModal = () => bootstrap.Modal.getOrCreateInstance(document.getElementById('registerModal'));

let usersCache = [];

async function loadUsers() {
    const { data } = await api.get('/users');
    usersCache = data;
    return data;
}

// Populate a <select> with users; `includeUnassigned` adds a blank first option.
function fillUserSelect(select, includeUnassigned) {
    select.innerHTML = includeUnassigned ? '<option value="">Unassigned</option>' : '';
    usersCache.forEach(u => {
        const opt = document.createElement('option');
        opt.value = u.id;
        opt.textContent = `${u.username} (#${u.id})`;
        select.appendChild(opt);
    });
}

/* ------------------------------ Projects -------------------------------- */

const projectModal = () => bootstrap.Modal.getOrCreateInstance(document.getElementById('projectModal'));

// Fill the owner filter from the users cache, preserving the current selection.
function fillOwnerFilter() {
    const sel = document.getElementById('projectOwnerFilter');
    const current = sel.value;
    sel.innerHTML = '<option value="">All owners</option>';
    usersCache.forEach(u => {
        const opt = document.createElement('option');
        opt.value = u.id;
        opt.textContent = `${u.username} (#${u.id})`;
        sel.appendChild(opt);
    });
    sel.value = current;
}

async function loadProjects() {
    try {
        await loadUsers();
        fillOwnerFilter();
        const ownerId = document.getElementById('projectOwnerFilter').value;
        let projects;
        if (ownerId) {
            // Custom JPQL query: projects for a single owner (findProjectsByOwnerId).
            const { data } = await api.get(`/projects/by-owner/${ownerId}`);
            projects = data;
        } else {
            const { data } = await api.get('/projects', { params: { size: 100, sort: 'id' } });
            projects = data.content || data; // Page wrapper -> content
        }
        renderProjects(projects);
    } catch (e) {
        alertMsg(apiError(e));
    }
}

function renderProjects(projects) {
    const body = document.getElementById('projectsTableBody');
    body.innerHTML = '';
    if (!projects.length) {
        body.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-3">No projects yet.</td></tr>';
        return;
    }
    projects.forEach(p => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td>${p.id}</td>
            <td>${escapeHtml(p.name)}</td>
            <td class="text-muted">${escapeHtml(p.description || '')}</td>
            <td>${escapeHtml(p.owner ? p.owner.username : '—')}</td>
            <td class="small text-muted">${(p.createdAt || '').slice(0, 10)}</td>
            <td class="text-end text-nowrap">
                <button class="btn btn-sm btn-outline-secondary" data-act="tasks">Tasks</button>
                <button class="btn btn-sm btn-outline-primary" data-act="edit">Edit</button>
                <button class="btn btn-sm btn-outline-danger" data-act="delete">Delete</button>
            </td>`;
        tr.querySelector('[data-act="tasks"]').onclick = () => openTasksForProject(p.id);
        tr.querySelector('[data-act="edit"]').onclick = () => openProjectModal(p);
        tr.querySelector('[data-act="delete"]').onclick = () => deleteProject(p);
        body.appendChild(tr);
    });
}

function openProjectModal(project) {
    if (!requireLogin()) return;
    const form = document.getElementById('projectForm');
    form.reset();
    form.classList.remove('was-validated');
    // Only admins choose an owner; regular users always own what they create.
    const ownerWrap = document.getElementById('projectOwnerWrap');
    ownerWrap.classList.toggle('d-none', !isAdmin());
    form.ownerId.required = isAdmin();
    if (isAdmin()) fillUserSelect(form.ownerId, false);
    document.getElementById('projectModalTitle').textContent = project ? 'Edit Project' : 'New Project';
    form.id.value = project ? project.id : '';
    if (project) {
        form.name.value = project.name;
        form.description.value = project.description || '';
        if (isAdmin() && project.owner) form.ownerId.value = project.owner.id;
    }
    projectModal().show();
}

async function submitProject(e) {
    e.preventDefault();
    const form = e.target;
    if (!validateForm(form)) return;
    const payload = {
        name: form.name.value.trim(),
        description: form.description.value.trim()
    };
    // Admins set the owner explicitly; for regular users the server assigns it.
    if (isAdmin() && form.ownerId.value) payload.owner = { id: Number(form.ownerId.value) };
    try {
        if (form.id.value) {
            await api.put(`/projects/${form.id.value}`, payload);
            alertMsg('Project updated.', 'success');
        } else {
            await api.post('/projects', payload);
            alertMsg('Project created.', 'success');
        }
        projectModal().hide();
        loadProjects();
    } catch (err) {
        alertMsg(apiError(err));
    }
}

async function deleteProject(p) {
    if (!requireLogin()) return;
    if (!confirm(`Delete project "${p.name}"? Its tasks will be removed too.`)) return;
    try {
        await api.delete(`/projects/${p.id}`);
        alertMsg('Project deleted.', 'success');
        loadProjects();
    } catch (e) {
        alertMsg(apiError(e));
    }
}

/* -------------------------------- Tasks --------------------------------- */

const taskModal = () => bootstrap.Modal.getOrCreateInstance(document.getElementById('taskModal'));

async function loadTaskView() {
    try {
        await loadUsers();
        // Projects the user can browse tasks in (owned OR assigned-a-task-in), so an assignee
        // can reach their tasks in a project they don't own. Returns a plain list, not a Page.
        const { data } = await api.get('/projects/accessible');
        const projects = data.content || data;
        const sel = document.getElementById('taskProjectSelect');
        const current = sel.value;
        sel.innerHTML = projects.length ? '' : '<option value="">No projects — create one first</option>';
        projects.forEach(p => {
            const opt = document.createElement('option');
            opt.value = p.id;
            opt.textContent = `${p.name} (#${p.id})`;
            sel.appendChild(opt);
        });
        if (current) sel.value = current;

        // Assignee filter options (preserve current selection across reloads).
        const af = document.getElementById('taskAssigneeFilter');
        const currentAssignee = af.value;
        af.innerHTML = '<option value="">All assignees</option>';
        usersCache.forEach(u => {
            const opt = document.createElement('option');
            opt.value = u.id;
            opt.textContent = `${u.username} (#${u.id})`;
            af.appendChild(opt);
        });
        af.value = currentAssignee;

        document.getElementById('newTaskBtn').disabled = !projects.length;
        if (projects.length) loadTasks();
        else document.getElementById('tasksTableBody').innerHTML =
            '<tr><td colspan="7" class="text-center text-muted py-3">No projects yet.</td></tr>';
    } catch (e) {
        alertMsg(apiError(e));
    }
}

function selectedProjectId() { return document.getElementById('taskProjectSelect').value; }

// Reads the filters panel; only includes params that are actually set, so the backend
// Specification skips absent filters. Keys match the getAllTasks query params.
function taskFilterParams() {
    const val = id => document.getElementById(id).value.trim();
    const fields = {
        title: 'taskTitleFilter',
        description: 'taskDescriptionFilter',
        status: 'taskStatusFilter',
        priority: 'taskPriorityFilter',
        assigneeId: 'taskAssigneeFilter',
        dueAfter: 'taskDueAfterFilter',
        dueBefore: 'taskDueBeforeFilter'
    };
    const params = {};
    for (const [param, id] of Object.entries(fields)) {
        const v = val(id);
        if (v) params[param] = v;
    }
    return params;
}

async function loadTasks() {
    const projectId = selectedProjectId();
    if (!projectId) return;
    try {
        const { data } = await api.get(`/projects/${projectId}/tasks`, {
            params: { size: 100, ...taskFilterParams() }
        });
        renderTasks(data.content || data);
    } catch (e) {
        alertMsg(apiError(e));
    }
}

const STATUS_BADGE = { TODO: 'secondary', IN_PROGRESS: 'info', COMPLETED: 'success' };
const PRIORITY_BADGE = { LOW: 'success', MEDIUM: 'warning', HIGH: 'danger' };

function renderTasks(tasks) {
    const body = document.getElementById('tasksTableBody');
    body.innerHTML = '';
    if (!tasks.length) {
        // Distinguish "truly empty" from "filtered to nothing": a filter being active
        // means the user may still have other tasks here that just didn't match.
        const filterActive = Object.keys(taskFilterParams()).length > 0;
        const msg = filterActive ? 'No tasks match your filters.' : 'You have no tasks in this project.';
        body.innerHTML = `<tr><td colspan="7" class="text-center text-muted py-3">${msg}</td></tr>`;
        return;
    }
    tasks.forEach(t => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td>${t.id}</td>
            <td>${escapeHtml(t.title)}</td>
            <td><span class="badge text-bg-${STATUS_BADGE[t.status] || 'secondary'}">${t.status}</span></td>
            <td><span class="badge text-bg-${PRIORITY_BADGE[t.priority] || 'secondary'}">${t.priority}</span></td>
            <td class="small">${t.dueDate || '—'}</td>
            <td>${escapeHtml(t.assignee ? t.assignee.username : '—')}</td>
            <td class="text-end text-nowrap">
                <button class="btn btn-sm btn-outline-secondary" data-act="activity">Activity</button>
                <button class="btn btn-sm btn-outline-primary" data-act="edit">Edit</button>
                <button class="btn btn-sm btn-outline-danger" data-act="delete">Delete</button>
            </td>`;
        tr.querySelector('[data-act="activity"]').onclick = () => openTaskActivity(t);
        tr.querySelector('[data-act="edit"]').onclick = () => openTaskModal(t);
        tr.querySelector('[data-act="delete"]').onclick = () => deleteTask(t);
        body.appendChild(tr);
    });
}

function openTasksForProject(projectId) {
    showView('tasks');
    // loadTaskView runs from showView; set the project once options exist.
    setTimeout(() => {
        const sel = document.getElementById('taskProjectSelect');
        sel.value = projectId;
        loadTasks();
    }, 150);
}

function openTaskModal(task) {
    if (!requireLogin()) return;
    if (!selectedProjectId()) { alertMsg('Select a project first.', 'warning'); return; }
    const form = document.getElementById('taskForm');
    form.reset();
    form.classList.remove('was-validated');
    fillUserSelect(form.assigneeId, true);
    document.getElementById('taskModalTitle').textContent = task ? 'Edit Task' : 'New Task';
    form.id.value = task ? task.id : '';
    if (task) {
        form.title.value = task.title;
        form.description.value = task.description || '';
        form.status.value = task.status;
        form.priority.value = task.priority;
        form.dueDate.value = task.dueDate || '';
        if (task.assignee) form.assigneeId.value = task.assignee.id;
    }
    taskModal().show();
}

async function submitTask(e) {
    e.preventDefault();
    const form = e.target;
    if (!validateForm(form)) return;
    const payload = {
        title: form.title.value.trim(),
        description: form.description.value.trim(),
        status: form.status.value,
        priority: form.priority.value,
        dueDate: form.dueDate.value || null,
        assignee: form.assigneeId.value ? { id: Number(form.assigneeId.value) } : null
    };
    try {
        if (form.id.value) {
            await api.put(`/tasks/${form.id.value}`, payload);
            alertMsg('Task updated.', 'success');
        } else {
            await api.post(`/projects/${selectedProjectId()}/tasks`, payload);
            alertMsg('Task created.', 'success');
        }
        taskModal().hide();
        loadTasks();
    } catch (err) {
        alertMsg(apiError(err));
    }
}

async function deleteTask(t) {
    if (!requireLogin()) return;
    if (!confirm(`Delete task "${t.title}"?`)) return;
    try {
        await api.delete(`/tasks/${t.id}`);
        alertMsg('Task deleted.', 'success');
        loadTasks();
    } catch (e) {
        alertMsg(apiError(e));
    }
}

/* --------------------------- Task activity ------------------------------ */

const activityModal = () => bootstrap.Modal.getOrCreateInstance(document.getElementById('activityModal'));

const ACTION_BADGE = { CREATE: 'success', UPDATE: 'info', DELETE: 'danger' };

// Opens the audit trail for one task. The backend gates this by the same visibility rule
// as the task itself (404 if missing, 403 if not yours), so no extra client-side check needed.
async function openTaskActivity(t) {
    if (!requireLogin()) return;
    document.getElementById('activityModalTitle').textContent = `Activity — ${t.title} (#${t.id})`;
    const body = document.getElementById('activityTableBody');
    body.innerHTML = '<tr><td colspan="4" class="text-center text-muted py-3">Loading…</td></tr>';
    activityModal().show();
    try {
        const { data } = await api.get(`/tasks/${t.id}/activity`, { params: { size: 100 } });
        renderActivity(data.content || data);   // Page wrapper -> content
    } catch (e) {
        body.innerHTML =
            `<tr><td colspan="4" class="text-center text-danger py-3">${escapeHtml(apiError(e))}</td></tr>`;
    }
}

// One change cell: "field: old → new" for updates; a dash for whole-entity create/delete.
function activityChange(a) {
    if (!a.fieldChanged) return '<span class="text-muted">—</span>';
    const fmt = v => (v === null || v === undefined || v === '')
        ? '<span class="text-muted fst-italic">(none)</span>'
        : escapeHtml(v);
    return `<code>${escapeHtml(a.fieldChanged)}</code>: ${fmt(a.oldValue)} → ${fmt(a.newValue)}`;
}

function renderActivity(entries) {
    const body = document.getElementById('activityTableBody');
    body.innerHTML = '';
    if (!entries.length) {
        body.innerHTML = '<tr><td colspan="4" class="text-center text-muted py-3">No activity recorded yet.</td></tr>';
        return;
    }
    entries.forEach(a => {
        const when = (a.createdAt || '').replace('T', ' ').slice(0, 19);
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td class="small text-muted text-nowrap">${escapeHtml(when)}</td>
            <td>${escapeHtml(a.username || '—')}</td>
            <td><span class="badge text-bg-${ACTION_BADGE[a.action] || 'secondary'}">${escapeHtml(a.action)}</span></td>
            <td>${activityChange(a)}</td>`;
        body.appendChild(tr);
    });
}

/* --------------------------- Overdue tasks ------------------------------ */

// Uses GET /api/tasks/overdue -> TaskRepository.findOverdueTasks (custom JPQL).
async function loadOverdueTasks() {
    try {
        const { data } = await api.get('/tasks/overdue');
        renderOverdueTasks(data);
    } catch (e) {
        alertMsg(apiError(e));
    }
}

function renderOverdueTasks(tasks) {
    const body = document.getElementById('overdueTableBody');
    body.innerHTML = '';
    if (!tasks.length) {
        body.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-3">No overdue tasks. 🎉</td></tr>';
        return;
    }
    tasks.forEach(t => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td>${t.id}</td>
            <td>${escapeHtml(t.title)}</td>
            <td><span class="badge text-bg-${STATUS_BADGE[t.status] || 'secondary'}">${t.status}</span></td>
            <td><span class="badge text-bg-${PRIORITY_BADGE[t.priority] || 'secondary'}">${t.priority}</span></td>
            <td class="small text-danger fw-semibold">${t.dueDate || '—'}</td>
            <td>${escapeHtml(t.assignee ? t.assignee.username : '—')}</td>`;
        body.appendChild(tr);
    });
}

/* ------------------------------ User search ----------------------------- */

// Uses GET /api/users/search?keyword= -> UserRepository.searchByUsernameOrEmail (custom JPQL).
async function loadUsersView() {
    await searchUsers(document.getElementById('userSearchInput').value);
}

async function searchUsers(keyword) {
    try {
        const { data } = await api.get('/users/search', { params: { keyword: keyword || '' } });
        renderUsers(data);
    } catch (e) {
        alertMsg(apiError(e));
    }
}

function renderUsers(users) {
    const body = document.getElementById('usersTableBody');
    body.innerHTML = '';
    if (!users.length) {
        body.innerHTML = '<tr><td colspan="4" class="text-center text-muted py-3">No matching users.</td></tr>';
        return;
    }
    users.forEach(u => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td>${u.id}</td>
            <td>${escapeHtml(u.username)}</td>
            <td>${escapeHtml(u.email)}</td>
            <td class="small text-muted">${(u.createdAt || '').slice(0, 10)}</td>`;
        body.appendChild(tr);
    });
}

/* ------------------------------ Wiring ---------------------------------- */

document.addEventListener('DOMContentLoaded', async () => {
    await restoreSession();
    refreshAuthUI();

    // Navbar navigation
    document.querySelectorAll('#mainNav .nav-link').forEach(a =>
        a.addEventListener('click', e => { e.preventDefault(); showView(a.dataset.view); }));
    document.getElementById('logoutBtn').addEventListener('click', logout);

    // Auth forms
    document.getElementById('loginForm').addEventListener('submit', async e => {
        e.preventDefault();
        const form = e.target;
        if (!validateForm(form)) return;
        try {
            await login(form.username.value.trim(), form.password.value);
            alertMsg('Logged in.', 'success');
            showView('projects');
        } catch (err) {
            alertMsg(err.message);
        }
    });

    // Register-user button (Projects page) opens the modal; login required.
    document.getElementById('registerUserBtn').addEventListener('click', () => {
        if (!requireLogin()) return;
        const form = document.getElementById('registerForm');
        form.reset();
        form.classList.remove('was-validated');
        registerModal().show();
    });

    document.getElementById('registerForm').addEventListener('submit', async e => {
        e.preventDefault();
        const form = e.target;
        if (!validateForm(form)) return;
        if (!requireLogin()) return;
        try {
            await api.post('/users', {
                username: form.username.value.trim(),
                email: form.email.value.trim(),
                password: form.password.value
            });
            alertMsg('User registered.', 'success');
            registerModal().hide();
        } catch (err) {
            alertMsg(apiError(err));
        }
    });

    // Project + task forms / buttons
    document.getElementById('projectForm').addEventListener('submit', submitProject);
    document.getElementById('taskForm').addEventListener('submit', submitTask);
    document.getElementById('newProjectBtn').addEventListener('click', () => openProjectModal(null));
    document.getElementById('newTaskBtn').addEventListener('click', () => openTaskModal(null));
    document.getElementById('taskProjectSelect').addEventListener('change', loadTasks);

    // Task filter panel: selects/dates reload immediately; text inputs are debounced.
    ['taskStatusFilter', 'taskPriorityFilter', 'taskAssigneeFilter',
     'taskDueAfterFilter', 'taskDueBeforeFilter']
        .forEach(id => document.getElementById(id).addEventListener('change', loadTasks));

    let taskTextFilterTimer;
    const debouncedTaskReload = () => {
        clearTimeout(taskTextFilterTimer);
        taskTextFilterTimer = setTimeout(loadTasks, 300);
    };
    document.getElementById('taskTitleFilter').addEventListener('input', debouncedTaskReload);
    document.getElementById('taskDescriptionFilter').addEventListener('input', debouncedTaskReload);

    document.getElementById('clearTaskFiltersBtn').addEventListener('click', () => {
        ['taskStatusFilter', 'taskPriorityFilter', 'taskAssigneeFilter', 'taskTitleFilter',
         'taskDescriptionFilter', 'taskDueAfterFilter', 'taskDueBeforeFilter']
            .forEach(id => document.getElementById(id).value = '');
        loadTasks();
    });

    // Owner filter (Projects view) -> reloads via findProjectsByOwnerId.
    document.getElementById('projectOwnerFilter').addEventListener('change', loadProjects);

    // User search (Users view) -> debounced calls to /users/search.
    let userSearchTimer;
    document.getElementById('userSearchInput').addEventListener('input', e => {
        clearTimeout(userSearchTimer);
        const keyword = e.target.value;
        userSearchTimer = setTimeout(() => searchUsers(keyword), 250);
    });

    // Initial view
    showView(isLoggedIn() ? 'projects' : 'auth');
});
