// Node-based demo tests have no Vite environment; deployed builds set this explicitly.
export const demoMode = import.meta.env?.VITE_APP_DEMO_MODE === 'true' || !import.meta.env
// Enable only after the connected backend implements the documented revision endpoint.
export const cvRevisionsEnabled = import.meta.env?.VITE_APP_CV_REVISIONS_ENABLED === 'true'
