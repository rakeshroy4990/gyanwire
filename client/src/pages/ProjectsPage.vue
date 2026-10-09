<script setup>
import { onMounted, ref, watch } from 'vue';
import { useAuth } from '../composables/useAuth.js';
import { useProduct } from '../composables/useProduct.js';
import { fetchProjectMarkdown, projectExportUrl } from '../services/product.service.js';
import PageSkeleton from '../components/ui/PageSkeleton.vue';

const { isAuthenticated, openLoginPopup } = useAuth();
const { projects: loadProjects, addProject, error, loading } = useProduct();

const projects = ref([]);
const name = ref('');
const notice = ref('');
const copiedId = ref('');
const copyError = ref('');
const booting = ref(true);

async function refresh({ initial = false } = {}) {
  notice.value = '';
  if (initial) booting.value = true;
  try {
    const data = await loadProjects();
    projects.value = data?.projects || [];
  } finally {
    if (initial) booting.value = false;
  }
}

async function create() {
  const next = name.value.trim();
  if (!next) {
    notice.value = 'Give the project a name.';
    return;
  }
  notice.value = '';
  try {
    await addProject(next);
    name.value = '';
    await refresh();
    notice.value = 'Project created. Save findings into it from research.';
  } catch {
    notice.value = '';
  }
}

async function copyEmail(project) {
  copyError.value = '';
  copiedId.value = '';
  try {
    const markdown = await fetchProjectMarkdown(project.id);
    await navigator.clipboard.writeText(markdown);
    copiedId.value = project.id;
  } catch (err) {
    copyError.value = err.message || 'Could not copy that project.';
    copiedId.value = project.id;
  }
}

function exportUrl(id, kind) {
  return projectExportUrl(id, kind);
}

onMounted(() => {
  if (isAuthenticated.value) refresh({ initial: true }).catch(() => { booting.value = false; });
  else booting.value = false;
});

watch(isAuthenticated, (signedIn, wasSignedIn) => {
  if (signedIn && !wasSignedIn) refresh({ initial: true }).catch(() => { booting.value = false; });
  if (!signedIn) {
    projects.value = [];
    booting.value = false;
  }
});
</script>

<template>
  <main class="account-page">
    <header class="account-page__hero">
      <p class="account-page__kicker">Account</p>
      <h1>Projects</h1>
      <p>
        Keep findings, ideas, and plans together. Download Markdown or PDF, or copy the write-up into an email.
      </p>
    </header>

    <section v-if="!isAuthenticated" class="account-card">
      <h2>Sign in to see your projects</h2>
      <p>Projects stay on your account so you can export them later.</p>
      <button type="button" class="btn btn--primary" @click="openLoginPopup()">Sign in</button>
    </section>

    <template v-else>
      <PageSkeleton v-if="booting" variant="list" :rows="2" label="Loading projects" />
      <template v-else>
      <form class="account-card account-form" @submit.prevent="create">
        <h2>New project</h2>
        <label class="auth-field">
          <span class="auth-field__label">Project name</span>
          <input
            v-model="name"
            type="text"
            maxlength="80"
            required
            placeholder="For example, CDSCO device notes"
          >
        </label>
        <p class="auth-field__hint">Free includes one project. Pro includes twenty.</p>
        <p v-if="notice" class="account-notice" role="status">{{ notice }}</p>
        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
        <button class="btn btn--primary" type="submit" :disabled="loading || !name.trim()">
          {{ loading ? 'Saving…' : 'Create project' }}
        </button>
      </form>

      <p v-if="!loading && !projects.length" class="account-empty">
        No projects yet. Name one above, then save a finding or idea into it from research.
      </p>

      <ul v-if="projects.length" class="project-list">
        <li v-for="project in projects" :key="project.id" class="account-card project-card">
          <h2>{{ project.name }}</h2>
          <p>Export includes citations and source links from the items you saved.</p>
          <div class="project-card__actions">
            <a class="btn btn--ghost" :href="exportUrl(project.id, 'md')">Download Markdown</a>
            <a class="btn btn--ghost" :href="exportUrl(project.id, 'pdf')">Download PDF</a>
            <button type="button" class="btn btn--ghost" @click="copyEmail(project)">
              {{ copiedId === project.id && !copyError ? 'Copied' : 'Copy as email' }}
            </button>
          </div>
          <p v-if="copyError && copiedId === project.id" class="form-error" role="alert">{{ copyError }}</p>
        </li>
      </ul>
      </template>
    </template>
  </main>
</template>
