import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { useAuth } from './useAuth.js';
import { useUsage } from './useUsage.js';
import {
  cancelBillingSubscription,
  fetchBillingStatus,
  openRazorpayCheckout,
  startCheckout,
} from '../services/billing.service.js';

export function useBilling() {
  const router = useRouter();
  const { isAuthenticated, email, shortName, openLoginPopup } = useAuth();
  const { refreshUsage } = useUsage();

  const isBusy = ref(false);
  const error = ref('');
  const status = ref(null);

  async function loadStatus() {
    if (!isAuthenticated.value) {
      status.value = null;
      return null;
    }
    status.value = await fetchBillingStatus();
    return status.value;
  }

  async function checkout(planId, interval = 'monthly') {
    error.value = '';
    if (!isAuthenticated.value) {
      openLoginPopup();
      error.value = 'Sign in to upgrade.';
      return;
    }
    isBusy.value = true;
    try {
      const data = await startCheckout({ planId, interval });
      await openRazorpayCheckout({
        keyId: data.keyId,
        subscriptionId: data.subscriptionId,
        email: data.email || email.value,
        name: shortName.value,
        onSuccess: async () => {
          await refreshUsage();
          await loadStatus();
          router.push({ name: 'billing-success' });
        },
      });
    } catch (err) {
      error.value = err.message || 'Checkout failed.';
    } finally {
      isBusy.value = false;
    }
  }

  async function cancel() {
    error.value = '';
    isBusy.value = true;
    try {
      status.value = await cancelBillingSubscription();
      await refreshUsage();
    } catch (err) {
      error.value = err.message || 'Cancel failed.';
    } finally {
      isBusy.value = false;
    }
  }

  return {
    isBusy,
    error,
    status,
    loadStatus,
    checkout,
    cancel,
  };
}
