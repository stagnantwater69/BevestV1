import { useState, useEffect, useMemo } from 'react';
import { ref, onValue } from 'firebase/database';
import { rtdb } from './firebase';

const ONLINE_TIMEOUT_MS = 60_000;

const normalizeMac = (mac) =>
  String(mac || '')
    .replace(/[^A-Fa-f0-9]/g, '')
    .toUpperCase();

const isMatchingEsp32Mac = (macA, macB) => {
  const a = normalizeMac(macA);
  const b = normalizeMac(macB);
  if (!a || !b) return false;
  if (a === b) return true;
  if (a.length === 12 && b.length === 12) {
    const numA = parseInt(a, 16);
    const numB = parseInt(b, 16);
    if (!Number.isNaN(numA) && !Number.isNaN(numB)) {
      return Math.abs(numA - numB) <= 2;
    }
  }
  return false;
};

const toNum = (v) => {
  if (typeof v === 'number' && !Number.isNaN(v)) return v;
  if (typeof v === 'string' && v.trim() !== '') {
    const n = Number(v);
    if (!Number.isNaN(n)) return n;
  }
  return null;
};

const toBool = (v) => {
  if (typeof v === 'boolean') return v;
  if (typeof v === 'number') return v > 0;
  if (typeof v === 'string') return v.toLowerCase() === 'true' || v === '1';
  return false;
};

/**
 * Subscribes directly to Firebase Realtime Database (`vestsLive`, `vestsLiveByMac`,
 * and `liveReadingsByVest`) so vest online/offline status, Wi-Fi RSSI, battery,
 * and lastSeen come straight from RTDB in real time.
 */
export function useRtdbVestPresence() {
  const [vestsLive, setVestsLive] = useState(null);
  const [vestsLiveByMac, setVestsLiveByMac] = useState(null);
  const [readingsByVest, setReadingsByVest] = useState(null);
  const [rtLoaded, setRtLoaded] = useState(false);
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const unsub1 = onValue(
      ref(rtdb, 'vestsLive'),
      (snap) => {
        setVestsLive(snap.exists() ? snap.val() : {});
        setRtLoaded(true);
      },
      () => setRtLoaded(true)
    );
    const unsub2 = onValue(
      ref(rtdb, 'vestsLiveByMac'),
      (snap) => {
        setVestsLiveByMac(snap.exists() ? snap.val() : {});
        setRtLoaded(true);
      },
      () => setRtLoaded(true)
    );
    const unsub3 = onValue(
      ref(rtdb, 'liveReadingsByVest'),
      (snap) => {
        setReadingsByVest(snap.exists() ? snap.val() : {});
        setRtLoaded(true);
      },
      () => setRtLoaded(true)
    );

    const timer = setInterval(() => setNow(Date.now()), 10_000);
    return () => {
      unsub1();
      unsub2();
      unsub3();
      clearInterval(timer);
    };
  }, []);

  const liveEntries = useMemo(() => {
    const map = new Map();

    const ingest = (obj) => {
      if (!obj || typeof obj !== 'object') return;
      Object.entries(obj).forEach(([key, raw]) => {
        if (!raw || typeof raw !== 'object') return;
        const vestId = raw.vestId ? String(raw.vestId) : key;
        const mac = normalizeMac(raw.mac || raw.macAddress);
        const serialNumber = raw.serialNumber ? String(raw.serialNumber) : null;
        const wifiRssi = toNum(raw.wifiRssi ?? raw.rssi);
        const wifiSsid = raw.wifiSsid ? String(raw.wifiSsid) : null;
        const battery = toNum(raw.battery);
        const rawOnline = toBool(raw.online);
        const lastSeen =
          toNum(raw.lastSeenServer) ??
          toNum(raw.timestampServer) ??
          toNum(raw.timestamp) ??
          toNum(raw.lastSeen);
        const diff = lastSeen != null ? now - lastSeen : Number.MAX_SAFE_INTEGER;
        const isRecent = lastSeen != null && diff <= ONLINE_TIMEOUT_MS && diff >= -86_400_000;
        const isLive = rawOnline && (isRecent || lastSeen == null || lastSeen <= 100_000_000_000);

        const info = {
          online: isLive,
          battery,
          lastSeen,
          mac: mac || null,
          serialNumber,
          vestId,
          wifiSsid,
          wifiRssi,
        };

        const mergeInto = (targetKey) => {
          if (!targetKey) return;
          const prev = map.get(targetKey);
          if (!prev) {
            map.set(targetKey, info);
          } else {
            map.set(targetKey, {
              online: prev.online || info.online,
              battery: info.battery ?? prev.battery,
              lastSeen: Math.max(prev.lastSeen ?? 0, info.lastSeen ?? 0) || null,
              mac: info.mac || prev.mac,
              serialNumber: info.serialNumber || prev.serialNumber,
              vestId: info.vestId || prev.vestId,
              wifiSsid: info.wifiSsid || prev.wifiSsid,
              wifiRssi: info.wifiRssi ?? prev.wifiRssi,
            });
          }
        };

        mergeInto(key);
        if (vestId) mergeInto(vestId);
        if (mac) mergeInto(mac);
      });
    };

    ingest(vestsLive);
    ingest(vestsLiveByMac);
    ingest(readingsByVest);
    return map;
  }, [vestsLive, vestsLiveByMac, readingsByVest, now]);

  const enrichVestWithRtdb = (vest) => {
    const vestId = vest.vestId || vest.id || '';
    const cleanMac = normalizeMac(vest.cleanMac || vest.macAddress);
    const cleanSerial = String(vest.serialNumber || vest.esp32Serial || '')
      .replace(/[^A-Za-z0-9]/g, '')
      .toUpperCase();

    let live = null;
    if (vestId) {
      for (const [k, val] of liveEntries.entries()) {
        if (k.toLowerCase() === vestId.toLowerCase()) {
          live = val;
          break;
        }
      }
    }
    if (!live && cleanMac) {
      live = liveEntries.get(cleanMac) || null;
      if (!live) {
        for (const val of liveEntries.values()) {
          if (val.mac && isMatchingEsp32Mac(val.mac, cleanMac)) {
            live = val;
            break;
          }
        }
      }
    }
    if (!live && cleanSerial) {
      for (const val of liveEntries.values()) {
        const s = String(val.serialNumber || '')
          .replace(/[^A-Za-z0-9]/g, '')
          .toUpperCase();
        if (s && s === cleanSerial) {
          live = val;
          break;
        }
      }
    }

    const isOnline = rtLoaded ? Boolean(live?.online) : Boolean(vest.online);
    const hasWorker = Boolean(vest.assignedWorkerId);
    const isMaintenance = vest.status === 'MAINTENANCE';
    const effectiveStatus = isMaintenance
      ? 'MAINTENANCE'
      : isOnline && hasWorker
      ? 'ACTIVE'
      : isOnline
      ? 'AVAILABLE'
      : hasWorker
      ? 'ASSIGNED'
      : 'OFFLINE';

    return {
      ...vest,
      online: isOnline,
      status: effectiveStatus,
      battery: live?.battery ?? vest.battery,
      lastSeen: live?.lastSeen ?? vest.lastSeen,
      wifiSsid: live?.wifiSsid ?? vest.wifiSsid,
      wifiRssi: isOnline ? (live?.wifiRssi ?? vest.wifiRssi) : null,
      rssi: isOnline ? (live?.wifiRssi ?? vest.rssi) : null,
    };
  };

  return { rtLoaded, enrichVestWithRtdb };
}

