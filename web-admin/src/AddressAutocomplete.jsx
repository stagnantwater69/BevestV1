import React, { useState, useEffect, useRef } from 'react';
import { FiMapPin } from 'react-icons/fi';

const PH_CURATED_PLACES = [
  'Ayala Avenue, Makati City, Metro Manila, Philippines',
  'Bonifacio Global City (BGC), Taguig City, Metro Manila, Philippines',
  'Ortigas Center, Pasig City, Metro Manila, Philippines',
  'Cebu IT Park, Lahug, Cebu City, Cebu, Philippines',
  'Cebu Business Park, Ayala Center, Cebu City, Cebu, Philippines',
  'Quezon Avenue, Quezon City, Metro Manila, Philippines',
  'Alabang, Muntinlupa City, Metro Manila, Philippines',
  'Shaw Boulevard, Mandaluyong City, Metro Manila, Philippines',
  'Roxas Boulevard, Ermita, Manila, Metro Manila, Philippines',
  'Mall of Asia Complex, Pasay City, Metro Manila, Philippines',
  'Eastwood City, Bagumbayan, Quezon City, Metro Manila, Philippines',
  'Clark Freeport Zone, Mabalacat, Pampanga, Philippines',
  'Filinvest City, Alabang, Muntinlupa, Metro Manila, Philippines',
  'J.P. Laurel Avenue, Bajada, Davao City, Davao del Sur, Philippines',
  'Iloilo Business Park, Mandurriao, Iloilo City, Iloilo, Philippines',
  'Arca South, Western Bicutan, Taguig City, Metro Manila, Philippines',
  'Bridgetowne, Ugong Norte, Quezon City, Metro Manila, Philippines',
  'Nuvali, Santa Rosa City, Laguna, Philippines',
];

/**
 * Controlled address text input with live dropdown autocomplete suggestions
 * (OpenStreetMap Nominatim + curated Philippine business hubs fallback).
 */
const AddressAutocomplete = ({
  value = '',
  onChange,
  placeholder = 'e.g. 123 Ayala Ave, Makati City',
  className = 'form-input',
  hasError = false,
  required = false,
  id,
  style,
}) => {
  const [suggestions, setSuggestions] = useState([]);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const wrapperRef = useRef(null);
  const justSelectedRef = useRef(false);

  useEffect(() => {
    const handleOutside = (e) => {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handleOutside);
    return () => document.removeEventListener('mousedown', handleOutside);
  }, []);

  useEffect(() => {
    if (justSelectedRef.current) {
      justSelectedRef.current = false;
      return undefined;
    }

    const q = String(value || '').trim();
    if (q.length < 2) {
      setSuggestions([]);
      setLoading(false);
      return undefined;
    }

    const qLower = q.toLowerCase();
    const localMatches = PH_CURATED_PLACES.filter((p) =>
      p.toLowerCase().includes(qLower)
    );
    if (localMatches.length > 0) {
      setSuggestions(localMatches.slice(0, 6));
    }

    const controller = new AbortController();
    const timer = setTimeout(async () => {
      setLoading(true);
      try {
        const url = `https://nominatim.openstreetmap.org/search?format=json&addressdetails=1&countrycodes=ph&limit=6&q=${encodeURIComponent(q)}`;
        const res = await fetch(url, {
          signal: controller.signal,
          headers: { Accept: 'application/json' },
        });
        if (res.ok) {
          const data = await res.json();
          const remote = Array.isArray(data)
            ? data.map((item) => item.display_name).filter(Boolean)
            : [];
          const merged = Array.from(new Set([...remote, ...localMatches])).slice(0, 6);
          setSuggestions(merged);
          if (merged.length > 0) setOpen(true);
        }
      } catch (err) {
        if (err.name !== 'AbortError') {
          setSuggestions(localMatches.slice(0, 6));
        }
      } finally {
        setLoading(false);
      }
    }, 320);

    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [value]);

  const handleSelect = (item) => {
    justSelectedRef.current = true;
    setOpen(false);
    setSuggestions([]);
    if (onChange) onChange(item);
  };

  return (
    <div ref={wrapperRef} style={{ position: 'relative', width: '100%' }}>
      <input
        id={id}
        type="text"
        className={`${className}${hasError ? ' input-error' : ''}`}
        style={style}
        placeholder={placeholder}
        value={value}
        onFocus={() => {
          if (suggestions.length > 0) setOpen(true);
        }}
        onChange={(e) => {
          if (onChange) onChange(e.target.value);
          if (e.target.value.trim().length >= 2) {
            setOpen(true);
          } else {
            setOpen(false);
          }
        }}
        onKeyDown={(e) => {
          if (e.key === 'Escape') setOpen(false);
        }}
        autoComplete="off"
        required={required}
      />

      {open && (suggestions.length > 0 || loading) && (
        <div
          style={{
            position: 'absolute',
            top: 'calc(100% + 4px)',
            left: 0,
            right: 0,
            backgroundColor: '#ffffff',
            border: '1px solid #cbd5e1',
            borderRadius: '8px',
            boxShadow: '0 10px 25px rgba(15, 23, 42, 0.14)',
            zIndex: 10050,
            maxHeight: '220px',
            overflowY: 'auto',
          }}
        >
          {loading && suggestions.length === 0 ? (
            <div style={{ padding: '0.65rem 0.85rem', fontSize: '0.8rem', color: '#64748b' }}>
              Searching addresses...
            </div>
          ) : (
            suggestions.map((item) => (
              <button
                key={item}
                type="button"
                onMouseDown={(e) => {
                  e.preventDefault();
                  handleSelect(item);
                }}
                style={{
                  width: '100%',
                  textAlign: 'left',
                  padding: '0.6rem 0.8rem',
                  background: 'none',
                  border: 'none',
                  borderBottom: '1px solid #f1f5f9',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'flex-start',
                  gap: '0.5rem',
                  fontSize: '0.82rem',
                  color: '#0f172a',
                  lineHeight: 1.35,
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.backgroundColor = '#fff7ed';
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.backgroundColor = 'transparent';
                }}
              >
                <FiMapPin style={{ color: '#ea580c', flexShrink: 0, marginTop: '0.15rem' }} />
                <span>{item}</span>
              </button>
            ))
          )}
        </div>
      )}
    </div>
  );
};

export default AddressAutocomplete;
