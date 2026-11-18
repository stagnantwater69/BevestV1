import React from 'react';
import { FiAlertTriangle, FiCheckCircle, FiTrash2, FiPlusCircle, FiX } from 'react-icons/fi';

/**
 * Reusable Confirmation & Notice Modal for Create, Add, Remove, and Delete actions.
 */
const ConfirmModal = ({
  open,
  title = 'Confirm Action',
  message = '',
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  variant = 'primary', // 'primary' | 'danger' | 'success'
  loading = false,
  noticeOnly = false,
  onConfirm,
  onCancel,
}) => {
  if (!open) return null;

  const isDanger = variant === 'danger';
  const isSuccess = variant === 'success';

  const iconBg = isDanger ? '#fef2f2' : isSuccess ? '#f0fdf4' : '#fff7ed';
  const iconColor = isDanger ? '#dc2626' : isSuccess ? '#16a34a' : '#ea580c';
  const btnBg = isDanger ? '#dc2626' : isSuccess ? '#16a34a' : '#ea580c';

  const IconComp = isDanger
    ? FiTrash2
    : isSuccess
    ? FiCheckCircle
    : confirmLabel.toLowerCase().includes('add') || confirmLabel.toLowerCase().includes('create')
    ? FiPlusCircle
    : FiAlertTriangle;

  return (
    <div
      className="modal-backdrop"
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(15, 23, 42, 0.55)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 10000,
        padding: '1rem',
      }}
      onClick={() => !loading && onCancel && onCancel()}
    >
      <div
        className="modal-content sub-modal"
        style={{
          backgroundColor: '#ffffff',
          borderRadius: '14px',
          maxWidth: '440px',
          width: '100%',
          padding: '1.5rem',
          boxShadow: '0 20px 25px -5px rgba(15, 23, 42, 0.18)',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.85rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.65rem' }}>
            <div
              style={{
                padding: '0.5rem',
                backgroundColor: iconBg,
                color: iconColor,
                borderRadius: '10px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <IconComp style={{ fontSize: '1.25rem' }} />
            </div>
            <h3 style={{ fontSize: '1.15rem', fontWeight: 700, margin: 0, color: '#0f172a' }}>{title}</h3>
          </div>
          {onCancel && (
            <button
              type="button"
              onClick={() => !loading && onCancel()}
              style={{
                background: 'none',
                border: 'none',
                cursor: 'pointer',
                color: '#64748b',
                fontSize: '1.2rem',
                padding: '0.25rem',
              }}
              aria-label="Close"
            >
              <FiX />
            </button>
          )}
        </div>

        <p style={{ color: '#475569', fontSize: '0.9rem', lineHeight: 1.55, marginBottom: '1.5rem', whiteSpace: 'pre-line' }}>
          {message}
        </p>

        <div className="modal-actions" style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.65rem' }}>
          {!noticeOnly && onCancel && (
            <button type="button" className="btn-secondary" onClick={onCancel} disabled={loading}>
              {cancelLabel}
            </button>
          )}
          <button
            type="button"
            className="btn-primary"
            style={{ backgroundColor: btnBg, borderColor: btnBg }}
            onClick={noticeOnly ? onCancel : onConfirm}
            disabled={loading}
          >
            {loading ? 'Processing...' : noticeOnly ? 'OK' : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
};

export default ConfirmModal;
