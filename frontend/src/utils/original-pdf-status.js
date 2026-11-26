const states = {
  absent: ['No original PDF is stored.', 'The server reports that no original PDF is stored for this record.', 'success'],
  pending: ['PDF deletion pending', 'Deletion is not yet confirmed. Cleanup is retried automatically by the server.', 'warning'],
  failed: ['PDF deletion failed', 'The server has not confirmed deletion. Refresh to check the latest status.', 'danger'],
  temporary: ['Original PDF temporarily stored', 'Confirm the reviewed facts to request cleanup. Check this status again after confirmation.', 'info'],
  stored: ['Original PDF still stored', 'The server still reports an original PDF. Automatic deletion is not confirmed for this record.', 'warning'],
  planned: ['PDF cleanup starts after confirmation', 'After confirmation, the server requests deletion of any uploaded original PDF.', 'info'],
  unknown: ['PDF status unavailable', 'The server has not provided enough information to confirm whether an original PDF is still stored.', 'info'],
  demo: ['PDF not uploaded in this demo', 'The demo uses local file metadata and synthetic facts. It does not delete files on a server.', 'info'],
}

const confirmed = row => row?.confirmed === true || row?.confirmed === 1
const temporary = row => row?.temporary_original === true || row?.temporary_original === 1

// Absence is not proof that a file once existed (e.g. a structured revised copy).
// Confirmation, publication and assessment completion are never deletion receipts.
export function originalPdfStatus(row, source = 'library') {
  let code = 'unknown'
  if (source === 'demo') code = 'demo'
  else if (source === 'case') {
    if (row?.cleanupStatus === 'COMPLETE') code = 'absent'
    else if (row?.cleanupStatus === 'PENDING') code = 'pending'
    else if (row?.cleanupStatus === 'FAILED') code = 'failed'
    else if (row?.cleanupStatus === 'NONE' && !row.confirmedAt) code = 'planned'
  } else if (source === 'library') {
    if (row?.originalAvailable === false) code = 'absent'
    else if (row?.originalAvailable === true) {
      code = temporary(row) ? confirmed(row) ? 'pending' : 'temporary' : 'stored'
    }
  }
  const [label, description, type] = states[code]
  return { code, label, description, type, pending: code === 'pending' }
}

export function mayRetryLibraryPdfCleanup(row) {
  // This endpoint requires successful publication of the current revision.
  return confirmed(row) && temporary(row) && row.originalAvailable === true
    && Number.isInteger(row.revision) && row.revision > 0 && row.cloud_revision === row.revision
}

// Refresh metadata without replacing unsaved facts or silently locking an editor.
export function withOriginalPdfMetadata(current, latest) {
  const result = { ...current }
  for (const key of ['originalAvailable', 'temporary_original']) {
    delete result[key]
    if (Object.hasOwn(latest, key)) result[key] = latest[key]
  }
  return result
}
