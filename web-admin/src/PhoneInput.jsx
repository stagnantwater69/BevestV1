import React, { useState, useEffect, useRef } from 'react';
import { FiChevronDown, FiSearch, FiCheck } from 'react-icons/fi';

export const COUNTRY_CODES = [
  { code: '+63', iso: 'ph', name: 'Philippines' },
  { code: '+1', iso: 'us', name: 'United States' },
  { code: '+1-ca', codeValue: '+1', iso: 'ca', name: 'Canada' },
  { code: '+65', iso: 'sg', name: 'Singapore' },
  { code: '+60', iso: 'my', name: 'Malaysia' },
  { code: '+62', iso: 'id', name: 'Indonesia' },
  { code: '+84', iso: 'vn', name: 'Vietnam' },
  { code: '+66', iso: 'th', name: 'Thailand' },
  { code: '+81', iso: 'jp', name: 'Japan' },
  { code: '+82', iso: 'kr', name: 'South Korea' },
  { code: '+86', iso: 'cn', name: 'China' },
  { code: '+852', iso: 'hk', name: 'Hong Kong' },
  { code: '+886', iso: 'tw', name: 'Taiwan' },
  { code: '+44', iso: 'gb', name: 'United Kingdom' },
  { code: '+61', iso: 'au', name: 'Australia' },
  { code: '+91', iso: 'in', name: 'India' },
  { code: '+971', iso: 'ae', name: 'United Arab Emirates' },
  { code: '+966', iso: 'sa', name: 'Saudi Arabia' },
  { code: '+49', iso: 'de', name: 'Germany' },
  { code: '+33', iso: 'fr', name: 'France' },
  { code: '+39', iso: 'it', name: 'Italy' },
  { code: '+34', iso: 'es', name: 'Spain' },
  { code: '+64', iso: 'nz', name: 'New Zealand' },
];

/**
 * FlagImage displays the official national flag using FlagCDN
 */
export const FlagImage = ({ iso, alt = '' }) => (
  <img
    src={`https://flagcdn.com/w40/${iso.toLowerCase()}.png`}
    srcSet={`https://flagcdn.com/w80/${iso.toLowerCase()}.png 2x`}
    width="20"
    height="14"
    alt={alt || iso}
    className="phone-flag-img"
    loading="lazy"
  />
);

/**
 * Helper to parse a stored raw phone string into countryCode and localNumber
 */
export function parsePhoneNumber(rawPhone, fallbackCode = '+63') {
  if (!rawPhone || !rawPhone.trim()) {
    return { countryCode: fallbackCode, localNumber: '' };
  }
  const clean = rawPhone.trim();

  // Try matching known codes, sorted by length descending
  const sorted = [...COUNTRY_CODES].sort(
    (a, b) => (b.codeValue || b.code).length - (a.codeValue || a.code).length
  );
  const matched = sorted.find((c) => clean.startsWith(c.codeValue || c.code));
  if (matched) {
    const code = matched.codeValue || matched.code;
    let rest = clean.slice(code.length).trim();
    if (code === '+63' && rest.startsWith('0') && rest.length > 1) {
      rest = rest.replace(/^0+/, '').trim();
    }
    return { countryCode: code, localNumber: rest };
  }

  // If starts with 0 and fallback is +63 (standard Philippine mobile syntax like 0917...)
  if (clean.startsWith('0') && fallbackCode === '+63') {
    return { countryCode: '+63', localNumber: clean.replace(/^0+/, '').trim() };
  }

  // Unknown prefix starting with +
  if (clean.startsWith('+')) {
    const spaceIndex = clean.indexOf(' ');
    if (spaceIndex > 0) {
      return {
        countryCode: clean.slice(0, spaceIndex),
        localNumber: clean.slice(spaceIndex + 1).trim(),
      };
    }
  }

  return { countryCode: fallbackCode, localNumber: clean };
}

/**
 * Format any phone number input so that it ALWAYS includes the country code.
 * Ensures that before saving to the database, the country code is prepended.
 */
export function formatPhoneWithCountryCode(rawPhone, fallbackCode = '+63') {
  if (!rawPhone || !rawPhone.trim()) return '';
  const parsed = parsePhoneNumber(rawPhone, fallbackCode);
  if (!parsed.localNumber) return '';
  let local = parsed.localNumber.trim();
  if (parsed.countryCode === '+63' && local.startsWith('0')) {
    local = local.replace(/^0+/, '').trim();
  }
  return local ? `${parsed.countryCode} ${local}` : '';
}

const PhoneInput = ({
  value = '',
  onChange,
  placeholder = '900 000 0000',
  id,
  disabled = false,
  required = false,
  className = '',
  variant = 'standard', // 'standard' | 'floating'
  label = 'Phone number',
}) => {
  const [selectedCode, setSelectedCode] = useState('+63');
  const [selectedIso, setSelectedIso] = useState('ph');
  const [localNumber, setLocalNumber] = useState('');
  const [isOpen, setIsOpen] = useState(false);
  const [openUpward, setOpenUpward] = useState(false);
  const [search, setSearch] = useState('');
  const dropdownRef = useRef(null);

  // Sync internal state when external value changes
  useEffect(() => {
    const parsed = parsePhoneNumber(value, selectedCode);
    setSelectedCode(parsed.countryCode);
    setLocalNumber(parsed.localNumber);
    const matchedCountry = COUNTRY_CODES.find((c) => (c.codeValue || c.code) === parsed.countryCode);
    if (matchedCountry) {
      setSelectedIso(matchedCountry.iso);
    }

    // If external value is a plain local number without the country code prefix,
    // automatically sync the country code to the parent form so it includes the prefix
    if (onChange && parsed.localNumber && !value.trim().startsWith('+')) {
      onChange(`${parsed.countryCode} ${parsed.localNumber}`);
    }
  }, [value]);

  // Click outside listener to close dropdown
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target)) {
        setIsOpen(false);
      }
    };
    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [isOpen]);

  const toggleDropdown = () => {
    if (disabled) return;
    if (!isOpen && dropdownRef.current) {
      const rect = dropdownRef.current.getBoundingClientRect();
      const modal = dropdownRef.current.closest('.profile-modal-card, .modal-content, .sub-modal');
      if (modal) {
        const modalRect = modal.getBoundingClientRect();
        const spaceBelowInModal = modalRect.bottom - rect.bottom;
        // If less than 260px available below inside modal card, open upward!
        setOpenUpward(spaceBelowInModal < 260);
      } else {
        const spaceBelow = window.innerHeight - rect.bottom;
        setOpenUpward(spaceBelow < 260 && rect.top > 260);
      }
    }
    setIsOpen(!isOpen);
  };

  const currentCountry =
    COUNTRY_CODES.find((c) => (c.codeValue || c.code) === selectedCode && c.iso === selectedIso) ||
    COUNTRY_CODES.find((c) => (c.codeValue || c.code) === selectedCode) ||
    COUNTRY_CODES[0];

  const filteredCountries = COUNTRY_CODES.filter((c) => {
    const q = search.toLowerCase().trim();
    if (!q) return true;
    return (
      c.name.toLowerCase().includes(q) ||
      (c.codeValue || c.code).includes(q) ||
      c.iso.toLowerCase().includes(q)
    );
  });

  const handleSelectCountry = (country) => {
    const newCode = country.codeValue || country.code;
    setSelectedCode(newCode);
    setSelectedIso(country.iso);
    setIsOpen(false);
    setSearch('');
    if (onChange) {
      if (localNumber.trim()) {
        onChange(`${newCode} ${localNumber.trim()}`);
      } else {
        onChange('');
      }
    }
  };

  const handleNumberChange = (e) => {
    const inputVal = e.target.value;

    // Check if user pasted an international number starting with +
    if (inputVal.trim().startsWith('+')) {
      const parsed = parsePhoneNumber(inputVal, selectedCode);
      setSelectedCode(parsed.countryCode);
      setLocalNumber(parsed.localNumber);
      const matchedCountry = COUNTRY_CODES.find((c) => (c.codeValue || c.code) === parsed.countryCode);
      if (matchedCountry) setSelectedIso(matchedCountry.iso);

      if (onChange) {
        onChange(parsed.localNumber ? `${parsed.countryCode} ${parsed.localNumber}` : '');
      }
      return;
    }

    // If typing local number starting with 0 in +63, clean leading 0 for mobile standard
    let cleanVal = inputVal;
    if (selectedCode === '+63' && cleanVal.startsWith('0') && cleanVal.length > 1) {
      cleanVal = cleanVal.replace(/^0+/, '');
    }

    setLocalNumber(cleanVal);
    if (onChange) {
      const trimmed = cleanVal.trim();
      // Country code is always added to the phone number
      onChange(trimmed ? `${selectedCode} ${trimmed}` : '');
    }
  };

  const renderDropdownMenu = () => (
    <div
      className={`phone-dropdown-menu ${openUpward ? 'open-upward' : ''}`}
      onClick={(e) => e.stopPropagation()}
    >
      <div className="phone-dropdown-search">
        <FiSearch />
        <input
          type="text"
          placeholder="Search country or code..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          autoFocus
        />
      </div>
      <div className="phone-dropdown-list">
        {filteredCountries.length === 0 ? (
          <div style={{ padding: '0.75rem', textAlign: 'center', color: '#94a3b8', fontSize: '0.8rem' }}>
            No countries found
          </div>
        ) : (
          filteredCountries.map((c) => {
            const code = c.codeValue || c.code;
            const isSelected = (currentCountry.codeValue || currentCountry.code) === code && currentCountry.iso === c.iso;
            return (
              <div
                key={c.iso + c.code}
                className={`phone-dropdown-item ${isSelected ? 'selected' : ''}`}
                onClick={() => handleSelectCountry(c)}
              >
                <FlagImage iso={c.iso} alt={c.name} />
                <span className="country-name">{c.name}</span>
                <span className="country-dial-code">{code}</span>
                {isSelected && <FiCheck style={{ marginLeft: 'auto', color: '#ea580c' }} />}
              </div>
            );
          })
        )}
      </div>
    </div>
  );

  if (variant === 'floating') {
    return (
      <div
        className={`floating-input-group floating-phone-group ${className}`}
        ref={dropdownRef}
        style={{ zIndex: isOpen ? 9999 : undefined }}
      >
        <label className="floating-phone-label">{label}</label>
        <div className="floating-phone-controls">
          <button
            type="button"
            className="floating-phone-flag-btn"
            onClick={toggleDropdown}
            disabled={disabled}
            aria-label="Select Country"
          >
            <FlagImage iso={currentCountry.iso} alt={currentCountry.name} />
            <span className="phone-code-text">{currentCountry.codeValue || currentCountry.code}</span>
            <FiChevronDown className={`phone-chevron ${isOpen ? 'open' : ''}`} />
          </button>

          <span className="floating-phone-divider" />

          <input
            type="tel"
            id={id}
            className="floating-phone-input"
            placeholder={placeholder}
            value={localNumber}
            onChange={handleNumberChange}
            disabled={disabled}
            required={required}
          />
        </div>

        {isOpen && renderDropdownMenu()}
      </div>
    );
  }

  return (
    <div
      className={`phone-input-group ${className}`}
      ref={dropdownRef}
      style={{ zIndex: isOpen ? 9999 : undefined }}
    >
      <button
        type="button"
        className="phone-flag-btn"
        onClick={toggleDropdown}
        disabled={disabled}
        aria-label="Select Country"
      >
        <FlagImage iso={currentCountry.iso} alt={currentCountry.name} />
        <span className="phone-code-text">{currentCountry.codeValue || currentCountry.code}</span>
        <FiChevronDown className={`phone-chevron ${isOpen ? 'open' : ''}`} />
      </button>

      <input
        type="tel"
        id={id}
        className="form-input phone-input-field"
        placeholder={placeholder}
        value={localNumber}
        onChange={handleNumberChange}
        disabled={disabled}
        required={required}
      />

      {isOpen && renderDropdownMenu()}
    </div>
  );
};

export default PhoneInput;
