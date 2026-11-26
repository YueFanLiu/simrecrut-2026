// Suggested codes from model_artifacts/rules/objective-curated-v8-online-v1.json (catalogs).
// They only fill the HR pickers; the server still validates each code against the active matching rule.
export const requirementCodes = Object.freeze({
  skillCodes: ['JAVA', 'JAVASCRIPT', 'TYPESCRIPT', 'SPRING_BOOT', 'MYSQL', 'POSTGRESQL', 'SQL', 'PYTHON', 'GIT', 'DOCKER',
    'HTML', 'CSS', 'VUE', 'REACT', 'LINUX', 'RUST', 'ROCKSDB', 'WEBASSEMBLY'],
  roleFamilyCodes: ['BACKEND_DEVELOPER', 'SOFTWARE_ENGINEERING', 'SOFTWARE_TESTING', 'DATA_ANALYSIS'],
  degreeLevelCodes: ['ASSOCIATE', 'BACHELOR', 'MASTER', 'PHD'],
  subjectCodes: ['COMPUTER_SCIENCE', 'MATHEMATICS', 'ENGINEERING', 'BUSINESS', 'UNKNOWN'],
  languageCodes: ['EN', 'FR', 'DE', 'ES', 'ZH'],
  languageLevelCodes: ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'],
  conditionCodes: ['REST_API_PROJECT', 'SOFTWARE_PROJECT', 'DEPLOYMENT', 'TEST_SUPPORT', 'ENGINEER_MENTORING', 'SOLE_QA',
    'API_DEVELOPMENT', 'WEB_APPLICATION'],
})
