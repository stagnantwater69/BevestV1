import { useState, useEffect } from 'react';
import { doc, onSnapshot, setDoc } from 'firebase/firestore';
import { db } from './firebase';

export const DEFAULT_WEBPAGE_SETTINGS = {
  callUs: '+63 917 555 0148',
  emailUs: 'contact@bevest.com',
  smallTitle: 'Safe Workplace',
  bigTitle: 'Your Safety is in our Vest'
};

const STORAGE_KEY = 'bevest_webpage_settings';

function readCachedSettings() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      return { ...DEFAULT_WEBPAGE_SETTINGS, ...JSON.parse(raw) };
    }
  } catch {
    // ignore storage read error
  }
  return DEFAULT_WEBPAGE_SETTINGS;
}

function cacheSettings(data) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
  } catch {
    // ignore storage write error
  }
}

/**
 * Real-time subscription hook for BeVest public Home webpage settings
 * stored in Firestore `settings/webpage` (managed by Developer Account).
 */
export function useWebpageSettings() {
  const [settings, setSettings] = useState(readCachedSettings);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const docRef = doc(db, 'settings', 'webpage');
    const unsubscribe = onSnapshot(
      docRef,
      (snap) => {
        if (snap.exists()) {
          const data = snap.data() || {};
          const merged = {
            callUs: data.callUs || DEFAULT_WEBPAGE_SETTINGS.callUs,
            emailUs: data.emailUs || DEFAULT_WEBPAGE_SETTINGS.emailUs,
            smallTitle: data.smallTitle || DEFAULT_WEBPAGE_SETTINGS.smallTitle,
            bigTitle: data.bigTitle || DEFAULT_WEBPAGE_SETTINGS.bigTitle,
            updatedAt: data.updatedAt || null,
            updatedBy: data.updatedBy || null
          };
          setSettings(merged);
          cacheSettings(merged);
        } else {
          setSettings(DEFAULT_WEBPAGE_SETTINGS);
        }
        setLoading(false);
      },
      (err) => {
        console.warn('Webpage settings listener fallback:', err.message);
        setLoading(false);
      }
    );

    return () => unsubscribe();
  }, []);

  return { settings, loading };
}

export async function saveWebpageSettings(nextValues, actorEmail = 'developer@bevest.com') {
  const payload = {
    callUs: (nextValues.callUs || DEFAULT_WEBPAGE_SETTINGS.callUs).trim(),
    emailUs: (nextValues.emailUs || DEFAULT_WEBPAGE_SETTINGS.emailUs).trim(),
    smallTitle: (nextValues.smallTitle || DEFAULT_WEBPAGE_SETTINGS.smallTitle).trim(),
    bigTitle: (nextValues.bigTitle || DEFAULT_WEBPAGE_SETTINGS.bigTitle).trim(),
    updatedAt: new Date(),
    updatedBy: actorEmail
  };

  await setDoc(doc(db, 'settings', 'webpage'), payload, { merge: true });
  cacheSettings(payload);
  return payload;
}

export async function resetWebpageSettings(actorEmail = 'developer@bevest.com') {
  return saveWebpageSettings(DEFAULT_WEBPAGE_SETTINGS, actorEmail);
}
