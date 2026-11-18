// Node-based demo tests have no Vite environment; deployed builds set this explicitly.
export const demoMode = import.meta.env?.VITE_APP_DEMO_MODE === 'true' || !import.meta.env
