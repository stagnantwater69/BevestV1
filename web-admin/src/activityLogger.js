import { collection, addDoc, serverTimestamp } from 'firebase/firestore';
import { db, auth } from './firebase';

/**
 * Log administrative or system actions to Firestore /activityLog collection.
 * 
 * @param {string} action Description of the action performed.
 * @param {string|null} actor Optional email or identifier of the user performing the action.
 */
export async function logActivity(action, actor = null) {
  if (!action) return;
  try {
    const currentUser = auth.currentUser;
    const actorId = actor || currentUser?.email || currentUser?.uid || 'Admin';

    await addDoc(collection(db, 'activityLog'), {
      message: action,
      action: action, // Supports both message and action attributes
      actorId: actorId,
      createdAt: Date.now(), // Milliseconds timestamp compatible with Android SystemRepositoryImpl
      timestamp: serverTimestamp()
    });
  } catch (err) {
    console.warn('Could not log activity to Firestore:', err);
  }
}

/**
 * Format a timestamp (number in ms, Firestore Timestamp, or Date) into a friendly relative time.
 */
export function formatTimeAgo(timestamp) {
  if (!timestamp) return 'Just now';
  let timeMs = 0;
  if (typeof timestamp === 'number') {
    timeMs = timestamp;
  } else if (timestamp?.toDate) {
    timeMs = timestamp.toDate().getTime();
  } else if (timestamp instanceof Date) {
    timeMs = timestamp.getTime();
  } else {
    timeMs = new Date(timestamp).getTime();
  }

  if (isNaN(timeMs) || timeMs === 0) return 'Recent';

  const now = Date.now();
  const diffSec = Math.floor((now - timeMs) / 1000);

  if (diffSec < 45) return 'Just now';
  const diffMin = Math.floor(diffSec / 60);
  if (diffMin < 60) return `${diffMin}m ago`;
  const diffHours = Math.floor(diffMin / 60);
  if (diffHours < 24) return `${diffHours}h ago`;
  const diffDays = Math.floor(diffHours / 24);
  if (diffDays < 30) return `${diffDays}d ago`;
  const diffMonths = Math.floor(diffDays / 30);
  if (diffMonths < 12) return `${diffMonths}mo ago`;
  return `${Math.floor(diffMonths / 12)}y ago`;
}
