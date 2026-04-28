const STORAGE_KEY = "recent_file_uploads";
const MAX_ITEMS = 20;

export function getRecentFiles() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export function addRecentFile(entry) {
  const current = getRecentFiles();
  const next = [entry, ...current.filter((item) => item.fileId !== entry.fileId)].slice(0, MAX_ITEMS);
  localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  return next;
}

