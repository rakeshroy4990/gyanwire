import { defineStore } from 'pinia';
import { ref } from 'vue';

export const useProfileStore = defineStore('profile', () => {
  const profile = ref(null);
  const consentAt = ref(null);

  function setProfile(next) {
    profile.value = next;
    consentAt.value = next?.consentAt || null;
  }

  return { profile, consentAt, setProfile };
});
