import { apiUrl } from './apiBase.js';

async function parseJson(res) {
  try {
    return await res.json();
  } catch {
    return null;
  }
}

async function api(path, { method = 'GET', body } = {}) {
  const res = await fetch(apiUrl(path), {
    method,
    credentials: 'include',
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  });
  const payload = await parseJson(res);
  return { res, payload };
}

export async function startCheckout({ planId, interval }) {
  const { res, payload } = await api('/api/billing/checkout', {
    method: 'POST',
    body: { planId, interval },
  });
  if (!res.ok || !payload?.success) {
    const err = new Error(payload?.message || 'Could not start checkout.');
    err.errorCode = payload?.errorCode;
    throw err;
  }
  return payload.data;
}

export async function fetchBillingOffers() {
  const { res, payload } = await api('/api/billing/offers');
  if (!res.ok || !payload?.success) {
    return { annual: false, student: false };
  }
  return payload.data || { annual: false, student: false };
}

export async function fetchBillingStatus() {
  const { res, payload } = await api('/api/billing/status');
  if (!res.ok || !payload?.success) {
    throw new Error(payload?.message || 'Could not load billing status.');
  }
  return payload.data;
}

export async function cancelBillingSubscription() {
  const { res, payload } = await api('/api/billing/cancel', { method: 'POST' });
  if (!res.ok || !payload?.success) {
    throw new Error(payload?.message || 'Could not cancel subscription.');
  }
  return payload.data;
}

function loadRazorpayScript() {
  if (window.Razorpay) return Promise.resolve();
  return new Promise((resolve, reject) => {
    const existing = document.querySelector('script[data-gyanwire-razorpay]');
    if (existing) {
      existing.addEventListener('load', () => resolve());
      existing.addEventListener('error', () => reject(new Error('Razorpay failed to load.')));
      return;
    }
    const script = document.createElement('script');
    script.src = 'https://checkout.razorpay.com/v1/checkout.js';
    script.async = true;
    script.dataset.gyanwireRazorpay = '1';
    script.onload = () => resolve();
    script.onerror = () => reject(new Error('Razorpay failed to load.'));
    document.head.appendChild(script);
  });
}

export async function openRazorpayCheckout({
  keyId,
  subscriptionId,
  email,
  name,
  onSuccess,
  onDismiss,
}) {
  await loadRazorpayScript();
  const options = {
    key: keyId,
    subscription_id: subscriptionId,
    name: 'Gyanwire',
    description: 'Research subscription',
    prefill: {
      email: email || '',
      name: name || '',
    },
    theme: { color: '#0F6B5C' },
    handler(response) {
      onSuccess?.(response);
    },
    modal: {
      ondismiss() {
        onDismiss?.();
      },
    },
  };
  const rzp = new window.Razorpay(options);
  rzp.open();
}
