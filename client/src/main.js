import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import { useAuthSessionStore } from './stores/authSession.store.js';
import { bootstrapAuthSession } from './services/auth.service.js';
import './styles.css';

const app = createApp(App);
const pinia = createPinia();
app.use(pinia);

const authSession = useAuthSessionStore(pinia);
authSession.hydrateFromStorage();

app.mount('#app');

bootstrapAuthSession().catch(() => {
  // Keep hydrated profile until cookies prove the session is dead.
});
