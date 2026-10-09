import { ref } from 'vue';
import { deleteProfile, fetchProfile, fetchReferral, redeemReferral, saveProfile } from '../services/profile.service.js';
import { useProfileStore } from '../stores/profile.store.js';

export function useProfile() {
  const store = useProfileStore();
  const error = ref('');
  const saving = ref(false);

  async function load() {
    const data = await fetchProfile();
    store.setProfile(data);
    return data;
  }

  async function save(body) {
    saving.value = true;
    error.value = '';
    try {
      const data = await saveProfile(body);
      store.setProfile(data);
      return data;
    } catch (err) {
      error.value = err.message;
      throw err;
    } finally {
      saving.value = false;
    }
  }

  async function remove() {
    await deleteProfile();
    store.setProfile(null);
  }

  return {
    store,
    error,
    saving,
    load,
    save,
    remove,
    referral: fetchReferral,
    redeem: redeemReferral,
  };
}
