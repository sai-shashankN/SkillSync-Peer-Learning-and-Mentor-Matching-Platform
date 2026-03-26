interface RazorpaySuccessResponse {
  razorpay_order_id: string;
  razorpay_payment_id: string;
  razorpay_signature: string;
}

interface RazorpayCheckoutOptions {
  key: string;
  amount: number;
  currency: string;
  name: string;
  description: string;
  order_id: string;
  handler: (response: RazorpaySuccessResponse) => void;
  modal?: {
    ondismiss?: () => void;
  };
  prefill?: {
    name?: string;
    email?: string;
  };
  theme?: {
    color?: string;
  };
}

interface RazorpayInstance {
  open: () => void;
  on: (event: 'payment.failed', callback: (response: unknown) => void) => void;
}

declare global {
  interface Window {
    Razorpay?: new (options: RazorpayCheckoutOptions) => RazorpayInstance;
  }
}

let razorpayScriptPromise: Promise<void> | null = null;

function loadRazorpayScript() {
  if (window.Razorpay) {
    return Promise.resolve();
  }

  razorpayScriptPromise ??= new Promise<void>((resolve, reject) => {
    const existingScript = document.querySelector<HTMLScriptElement>(
      'script[data-razorpay-checkout="true"]',
    );

    if (existingScript) {
      existingScript.addEventListener('load', () => resolve(), { once: true });
      existingScript.addEventListener(
        'error',
        () => reject(new Error('Unable to load Razorpay checkout.')),
        { once: true },
      );
      return;
    }

    const script = document.createElement('script');
    script.src = 'https://checkout.razorpay.com/v1/checkout.js';
    script.async = true;
    script.dataset.razorpayCheckout = 'true';
    script.onload = () => resolve();
    script.onerror = () => reject(new Error('Unable to load Razorpay checkout.'));
    document.body.appendChild(script);
  });

  return razorpayScriptPromise;
}

export function useRazorpay() {
  const openCheckout = async (options: {
    orderId: string;
    amount: number;
    keyId: string;
    onSuccess: (response: RazorpaySuccessResponse) => void;
    onCancel: () => void;
  }) => {
    await loadRazorpayScript();

    if (!window.Razorpay) {
      throw new Error('Razorpay checkout is unavailable.');
    }

    const checkout = new window.Razorpay({
      key: options.keyId,
      amount: options.amount,
      currency: 'INR',
      name: 'SkillSync',
      description: 'Mentorship session payment',
      order_id: options.orderId,
      handler: options.onSuccess,
      modal: {
        ondismiss: options.onCancel,
      },
      theme: {
        color: '#2563eb',
      },
    });

    checkout.on('payment.failed', () => {
      options.onCancel();
    });

    checkout.open();
  };

  return { openCheckout };
}
