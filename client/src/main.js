import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import { router } from './router/index.js';
import { useAuthSessionStore } from './stores/authSession.store.js';
import { bootstrapAuthSession } from './services/auth.service.js';
import { ensureIndustriesCached } from './services/industries.service.js';
import { ensureOptionsCached } from './services/optionCatalog.service.js';
import './styles.css';

const app = createApp(App);
const pinia = createPinia();
app.use(pinia);
app.use(router);

const authSession = useAuthSessionStore(pinia);
authSession.hydrateFromStorage();

// Warm research industries into IndexedDB on startup (skip network if already cached).
ensureIndustriesCached();
ensureOptionsCached();

app.mount('#app');

bootstrapAuthSession().catch(() => {
  // Keep hydrated profile until cookies prove the session is dead.
});
