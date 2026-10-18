// This is a frontend demo switch, not a backend registration policy.
export const registrationEnabled = import.meta.env.VITE_DEMO_REGISTRATION_ENABLED !== 'false'
export const jobsScenario = import.meta.env.VITE_DEMO_JOBS_SCENARIO || 'normal'
export const jobDetailScenario = import.meta.env.VITE_DEMO_JOB_DETAIL_SCENARIO || 'normal'
export const caseCreationScenario = import.meta.env.VITE_DEMO_CASE_CREATION_SCENARIO || 'normal'
export const extractionScenario = import.meta.env.VITE_DEMO_EXTRACTION_SCENARIO || 'normal'
export const reviewScenario = import.meta.env.VITE_DEMO_REVIEW_SCENARIO || 'normal'
export const researchAttributesEnabled = import.meta.env.VITE_DEMO_RESEARCH_ATTRIBUTES_ENABLED === 'true'
export const simulationScenario = import.meta.env.VITE_DEMO_SIMULATION_SCENARIO || 'normal'
export const resultsScenario = import.meta.env.VITE_DEMO_RESULTS_SCENARIO || 'normal'
export const historyScenario = import.meta.env.VITE_DEMO_HISTORY_SCENARIO || 'normal'
export const hrJobsScenario = import.meta.env.VITE_DEMO_HR_JOBS_SCENARIO || 'normal'
export const hrWizardScenario = import.meta.env.VITE_DEMO_HR_WIZARD_SCENARIO || 'normal'
export const hrVersionsScenario = import.meta.env.VITE_DEMO_HR_VERSIONS_SCENARIO || 'normal'
export const hrCasesScenario = import.meta.env.VITE_DEMO_HR_CASES_SCENARIO || 'normal'
export const hrDetailScenario = import.meta.env.VITE_DEMO_HR_DETAIL_SCENARIO || 'normal'
export const researchExperimentsScenario = import.meta.env.VITE_DEMO_RESEARCH_EXPERIMENTS_SCENARIO || 'normal'
export const reviewTasksScenario = import.meta.env.VITE_DEMO_REVIEW_TASKS_SCENARIO || 'normal'

export const reviewDetailScenario = import.meta.env.VITE_DEMO_REVIEW_DETAIL_SCENARIO || 'normal'

export const adminUsersScenario = import.meta.env.VITE_DEMO_ADMIN_USERS_SCENARIO || 'normal'

export const adminRolesScenario = import.meta.env.VITE_DEMO_ADMIN_ROLES_SCENARIO || 'normal'

export const adminModelsScenario = import.meta.env.VITE_DEMO_ADMIN_MODELS_SCENARIO || 'normal'

export const adminProtocolsScenario = import.meta.env.VITE_DEMO_ADMIN_PROTOCOLS_SCENARIO || 'normal'

export const adminHealthScenario = import.meta.env.VITE_DEMO_ADMIN_HEALTH_SCENARIO || 'normal'

export const adminFailuresScenario = import.meta.env.VITE_DEMO_ADMIN_FAILURES_SCENARIO || 'normal'

export const adminAuditScenario = import.meta.env.VITE_DEMO_ADMIN_AUDIT_SCENARIO || 'normal'
