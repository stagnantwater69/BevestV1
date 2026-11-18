/**
 * Shared validation helpers across Developer, Admin, Contractor, and Auth forms.
 */

const EMAIL_REGEX = /^[A-Za-z0-9+_.-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}$/;

export function isValidEmail(email) {
  const trimmed = String(email || '').trim();
  if (!trimmed) return false;
  return EMAIL_REGEX.test(trimmed);
}

/**
 * Returns 'Invalid email address' whenever a non-valid email (missing domain, etc.) is supplied.
 */
export function validateEmail(email, required = true) {
  const trimmed = String(email || '').trim();
  if (!trimmed) {
    return required ? 'Invalid email address' : '';
  }
  return isValidEmail(trimmed) ? '' : 'Invalid email address';
}

/**
 * Live validation while typing: returns 'Invalid email address' once non-empty and invalid.
 */
export function liveEmailError(email) {
  const trimmed = String(email || '').trim();
  if (!trimmed) return '';
  return isValidEmail(trimmed) ? '' : 'Invalid email address';
}

/**
 * Formats user names for profile display:
 * <Last Name>, <First Name> <Middle Initial> (if applicable) <Suffix> (if applicable)
 */
export function formatProfileName(person, fallback = '') {
  if (!person) return fallback;
  const first = String(person.firstName || '').trim();
  const last = String(person.lastName || '').trim();
  const middle = String(person.middleName || '').trim();
  const suffix = String(person.suffix || '').trim();
  if (!first && !last) {
    return String(person.fullName || person.name || person.email || fallback).trim();
  }
  const middleInitial = middle ? `${middle[0].toUpperCase()}.` : '';
  const tail = [first, middleInitial, suffix].filter(Boolean).join(' ');
  return [last, tail].filter(Boolean).join(', ');
}

/**
 * Validates Philippine (+63) and international numbers.
 * For +63, requires exactly 10 local digits (after country code, with no leading 0).
 */
export function validatePhoneNumber(rawPhone, required = true) {
  const clean = String(rawPhone || '').trim();
  if (!clean) {
    return required ? 'Phone number is required.' : '';
  }

  if (clean.startsWith('+63')) {
    const localDigits = clean.slice(3).replace(/\D/g, '');
    if (localDigits.length !== 10) {
      return 'Philippine phone number must be 10 digits (e.g. 9171234567).';
    }
    return '';
  }

  if (clean.startsWith('+')) {
    const spaceIdx = clean.indexOf(' ');
    const localDigits = (spaceIdx > 0 ? clean.slice(spaceIdx + 1) : clean.slice(1)).replace(/\D/g, '');
    if (localDigits.length < 7) {
      return 'Please enter a valid phone number.';
    }
    return '';
  }

  const digits = clean.replace(/\D/g, '').replace(/^0+/, '');
  if (digits.length !== 10) {
    return 'Philippine phone number must be 10 digits (e.g. 9171234567).';
  }
  return '';
}
