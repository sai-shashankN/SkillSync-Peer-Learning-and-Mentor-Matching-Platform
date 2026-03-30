interface PayPalApproveData {
  orderID: string;
}

interface PayPalButtonsInstance {
  render: (container: HTMLElement | string) => Promise<void>;
  close?: () => Promise<void>;
}

interface PayPalNamespace {
  Buttons: (options: {
    createOrder: () => Promise<string> | string;
    onApprove: (data: PayPalApproveData) => Promise<void> | void;
    onCancel?: () => void;
    onError?: (error: unknown) => void;
    style?: {
      layout?: 'vertical' | 'horizontal';
      shape?: 'rect' | 'pill';
      label?: 'paypal' | 'checkout' | 'pay';
    };
  }) => PayPalButtonsInstance;
}

declare global {
  interface Window {
    paypal?: PayPalNamespace;
  }
}

let payPalScriptPromise: Promise<void> | null = null;
let loadedSdkKey: string | null = null;

function getSdkKey(clientId: string, currency: string) {
  return `${clientId}:${currency}`;
}

function loadPayPalScript(clientId: string, currency: string) {
  const sdkKey = getSdkKey(clientId, currency);

  if (window.paypal && loadedSdkKey === sdkKey) {
    return Promise.resolve();
  }

  payPalScriptPromise = new Promise<void>((resolve, reject) => {
    const existingScript = document.querySelector<HTMLScriptElement>('script[data-paypal-sdk="true"]');
    if (existingScript) {
      existingScript.remove();
    }

    const script = document.createElement('script');
    script.src = `https://www.paypal.com/sdk/js?client-id=${encodeURIComponent(clientId)}&currency=${encodeURIComponent(currency)}&components=buttons`;
    script.async = true;
    script.dataset.paypalSdk = 'true';
    script.onload = () => {
      loadedSdkKey = sdkKey;
      resolve();
    };
    script.onerror = () => reject(new Error('Unable to load PayPal checkout.'));
    document.body.appendChild(script);
  });

  return payPalScriptPromise;
}

async function renderPayPalButtons(
  container: HTMLElement,
  options: {
    clientId: string;
    currency: string;
    orderId: string;
    onApprove: (orderId: string) => Promise<void>;
    onCancel: () => void;
    onError: (error: unknown) => void;
  },
) {
  await loadPayPalScript(options.clientId, options.currency);

  if (!window.paypal) {
    throw new Error('PayPal checkout is unavailable.');
  }

  container.innerHTML = '';
  const buttons = window.paypal.Buttons({
    createOrder: () => options.orderId,
    onApprove: (data) => options.onApprove(data.orderID),
    onCancel: options.onCancel,
    onError: options.onError,
    style: {
      layout: 'vertical',
      shape: 'pill',
      label: 'paypal',
    },
  });

  await buttons.render(container);

  return async () => {
    container.innerHTML = '';
    if (typeof buttons.close === 'function') {
      await buttons.close();
    }
  };
}

export function usePayPal() {
  return { renderButtons: renderPayPalButtons };
}
